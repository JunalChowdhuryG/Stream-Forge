package dev.streamforge.engine.checkpoint;

import dev.streamforge.core.checkpoint.CheckpointManager;
import dev.streamforge.core.checkpoint.StepCheckpoint;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DisplayName("PostgresCheckpointManager - integration test con PostgreSQL real")
class PostgresCheckpointManagerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("streamforge_test")
            .withUsername("test")
            .withPassword("test");

    private CheckpointManager manager;
    private static final String EXEC_ID = "550e8400-e29b-41d4-a716-446655440000";

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        jdbc    = new JdbcTemplate(ds);
        manager = new PostgresCheckpointManager(jdbc);

        // Limpiar y crear la fila padre requerida por la foreign key
        jdbc.update("DELETE FROM step_checkpoints WHERE execution_id::text = ?", EXEC_ID);
        jdbc.update("DELETE FROM pipeline_executions WHERE id::text = ?", EXEC_ID);

        jdbc.update("""
            INSERT INTO pipeline_executions (id, pipeline_id, state)
            VALUES (?::uuid, 'orders-daily-etl', 'RUNNING')
            """, EXEC_ID);
    }

    @Test
    @DisplayName("guardar y recuperar checkpoint desde PostgreSQL real")
    void guardarYRecuperar_PostgreSQL() {
        StepCheckpoint cp = new StepCheckpoint(
            EXEC_ID, "extract-orders",
            Instant.now(), "pg://orders_temp/output",
            45231L, "sha256-abc123"
        );

        manager.save(cp);
        Optional<StepCheckpoint> found = manager.find(EXEC_ID, "extract-orders");

        assertTrue(found.isPresent());
        assertEquals(45231L, found.get().getRowsProcessed());
        assertEquals("sha256-abc123", found.get().getChecksum());
    }

    @Test
    @DisplayName("upsert sobreescribe checkpoint existente")
    void upsert_sobreescribeExistente() {
        manager.save(new StepCheckpoint(EXEC_ID, "step-1", Instant.now(), null, 100L, null));
        manager.save(new StepCheckpoint(EXEC_ID, "step-1", Instant.now(), null, 999L, null));

        Optional<StepCheckpoint> found = manager.find(EXEC_ID, "step-1");
        assertTrue(found.isPresent());
        assertEquals(999L, found.get().getRowsProcessed());
    }

    @Test
    @DisplayName("findAll retorna checkpoints ordenados por completed_at")
    void findAll_ordenadosPorFecha() throws InterruptedException {
        manager.save(new StepCheckpoint(EXEC_ID, "step-a", Instant.now(), null, 10L, null));
        Thread.sleep(10);
        manager.save(new StepCheckpoint(EXEC_ID, "step-b", Instant.now(), null, 20L, null));
        Thread.sleep(10);
        manager.save(new StepCheckpoint(EXEC_ID, "step-c", Instant.now(), null, 30L, null));

        var all = manager.findAll(EXEC_ID);
        assertEquals(3, all.size());
        assertEquals("step-a", all.get(0).getStepId());
        assertEquals("step-b", all.get(1).getStepId());
        assertEquals("step-c", all.get(2).getStepId());
    }

    @Test
    @DisplayName("deleteAll elimina todos los checkpoints de la ejecucion")
    void deleteAll_eliminaTodos() {
        manager.save(new StepCheckpoint(EXEC_ID, "step-1", Instant.now(), null, 100L, null));
        manager.save(new StepCheckpoint(EXEC_ID, "step-2", Instant.now(), null, 200L, null));

        manager.deleteAll(EXEC_ID);

        assertTrue(manager.findAll(EXEC_ID).isEmpty());
    }
}