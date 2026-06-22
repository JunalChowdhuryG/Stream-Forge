package dev.streamforge.engine.checkpoint;

import dev.streamforge.core.checkpoint.CheckpointManager;
import dev.streamforge.core.checkpoint.StepCheckpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion de CheckpointManager que persiste en PostgreSQL
 * Usa JdbcTemplate de Spring para operaciones simples y eficientes
 */
@Repository
public class PostgresCheckpointManager implements CheckpointManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresCheckpointManager.class);

    private final JdbcTemplate jdbc;

    public PostgresCheckpointManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    
    @Override
    public void save(StepCheckpoint checkpoint) {
        String sql = """
                INSERT INTO step_checkpoints 
                    (execution_id, step_id, completed_at, output_location, rows_processed, checksum)
                VALUES (?::uuid, ?, ?, ?, ?, ?)
                ON CONFLICT (execution_id, step_id) 
                DO UPDATE SET
                    completed_at     = EXCLUDED.completed_at,
                    output_location  = EXCLUDED.output_location,
                    rows_processed   = EXCLUDED.rows_processed,
                    checksum         = EXCLUDED.checksum
                """;

        jdbc.update(sql,
                checkpoint.getExecutionId(),                    
                checkpoint.getStepId(),
                Timestamp.from(checkpoint.getCompletedAt()),
                checkpoint.getOutputLocation(),
                checkpoint.getRowsProcessed(),
                checkpoint.getChecksum()
        );

        log.debug("Checkpoint guardado - executionId={}, stepId={}, rows={}",
                checkpoint.getExecutionId(), 
                checkpoint.getStepId(), 
                checkpoint.getRowsProcessed());
    }



    @Override
    public Optional<StepCheckpoint> find(String executionId, String stepId) {
        List<StepCheckpoint> results = jdbc.query(
            "SELECT * FROM step_checkpoints WHERE execution_id = ?::uuid AND step_id = ?",
            new StepCheckpointRowMapper(),
            executionId, stepId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean exists(String executionId, String stepId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM step_checkpoints WHERE execution_id = ?::uuid AND step_id = ?",
            Integer.class,
            executionId, stepId
        );
        return count != null && count > 0;
    }

    @Override
    public List<StepCheckpoint> findAll(String executionId) {
        return jdbc.query(
            "SELECT * FROM step_checkpoints WHERE execution_id = ?::uuid ORDER BY completed_at",
            new StepCheckpointRowMapper(),
            executionId
        );
    }

    @Override
    public void deleteAll(String executionId) {
        jdbc.update(
            "DELETE FROM step_checkpoints WHERE execution_id = ?::uuid",
            executionId
        );
    }
    private static class StepCheckpointRowMapper implements RowMapper<StepCheckpoint> {
        @Override
        public StepCheckpoint mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StepCheckpoint(
                rs.getString("execution_id"),
                rs.getString("step_id"),
                rs.getTimestamp("completed_at").toInstant(),
                rs.getString("output_location"),
                rs.getLong("rows_processed"),
                rs.getString("checksum")
            );
        }
    }
}