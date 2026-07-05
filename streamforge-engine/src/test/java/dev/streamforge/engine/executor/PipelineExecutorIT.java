package dev.streamforge.engine.executor;

import dev.streamforge.connectors.csv.CsvConnector;
import dev.streamforge.connectors.registry.ConnectorRegistry;
import dev.streamforge.core.checkpoint.CheckpointManager;
import dev.streamforge.core.dag.DAGBuilder;
import dev.streamforge.core.dag.TopologicalSorter;
import dev.streamforge.core.lineage.LineageRepository;
import dev.streamforge.core.metrics.StreamForgeMetrics;
import dev.streamforge.core.model.*;
import dev.streamforge.core.model.execution.ExecutionContext;
import dev.streamforge.quality.engine.QualityRuleEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PipelineExecutor — integration test con pipeline CSV end-to-end")
class PipelineExecutorIT {

    @TempDir
    Path tempDir;

    private PipelineExecutor executor;
    private CheckpointManager checkpointManager;

    @BeforeEach
    void setUp() {
        ConnectorRegistry registry = new ConnectorRegistry();
        registry.register(new CsvConnector());

        checkpointManager = CheckpointManager.inMemory();

        StepExecutor stepExecutor = new StepExecutor(
            registry, checkpointManager,
            new QualityRuleEngine(), StreamForgeMetrics.NOOP,
            3, 100L, 1000L
        );

        executor = new PipelineExecutor(
            new DAGBuilder(), new TopologicalSorter(),
            stepExecutor, LineageRepository.NOOP,
            checkpointManager, StreamForgeMetrics.NOOP, 4
        );
    }

    @Test
    @DisplayName("pipeline CSV end-to-end: SOURCE -> SINK con datos reales")
    void pipeline_csvEndToEnd() throws IOException {
        // Crear archivo CSV de entrada
        Path input  = tempDir.resolve("orders.csv");
        Path output = tempDir.resolve("orders_out.csv");

        Files.writeString(input, """
            order_id,customer_id,total
            1,101,150.50
            2,102,200.00
            3,103,75.25
            """);

        // Definir pipeline
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("csv-test-pipeline");

        StepDefinition source = new StepDefinition();
        source.setId("read-orders");
        source.setType(StepType.SOURCE);
        source.setConnector("csv");
        source.setConfig(Map.of(
            "path",    input.toString(),
            "dataset", "orders_raw"
        ));
        source.setOutput("orders_raw");

        StepDefinition sink = new StepDefinition();
        sink.setId("write-orders");
        sink.setType(StepType.SINK);
        sink.setConnector("csv");
        sink.setDependsOn(List.of("read-orders"));
        sink.setConfig(Map.of("path", output.toString()));
        sink.setInput("orders_raw");

        pipeline.setSteps(List.of(source, sink));

        ExecutionContext ctx = new ExecutionContext(
            "exec-csv-e2e", pipeline, Map.of()
        );

        // Ejecutar
        PipelineExecutor.PipelineExecutionResult result = executor.execute(pipeline, ctx);

        // Verificar resultado
        assertTrue(result.isSuccess(),
            "Pipeline debe completar exitosamente: " + result.errorMessage());
        assertEquals(2, result.stepResults().size());
        assertTrue(result.totalRowsRead() > 0);

        // Verificar que el archivo de salida existe y tiene datos
        assertTrue(Files.exists(output), "Archivo de salida debe existir");
        List<String> lines = Files.readAllLines(output);
        assertTrue(lines.size() >= 4, // cabecera + 3 filas
            "Debe tener al menos 4 lineas (cabecera + 3 filas)");
    }

    @Test
    @DisplayName("pipeline con paso fallido retorna estado FAILED")
    void pipeline_pasoFallido_estadoFailed() {
        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("failing-pipeline");

        StepDefinition badStep = new StepDefinition();
        badStep.setId("bad-step");
        badStep.setType(StepType.SOURCE);
        badStep.setConnector("csv");
        badStep.setConfig(Map.of("path", "/ruta/que/no/existe.csv"));
        badStep.setOutput("nada");

        pipeline.setSteps(List.of(badStep));

        ExecutionContext ctx = new ExecutionContext(
            "exec-fail-test", pipeline, Map.of()
        );

        PipelineExecutor.PipelineExecutionResult result = executor.execute(pipeline, ctx);

        assertFalse(result.isSuccess());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("pipeline reanudado desde checkpoint salta pasos ya completados")
    void pipeline_reanudacion_saltaPasosConCheckpoint() throws IOException {
        Path input  = tempDir.resolve("data.csv");
        Path output = tempDir.resolve("data_out.csv");

        Files.writeString(input, "id,nombre\n1,Ana\n2,Luis\n");

        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId("resume-pipeline");

        StepDefinition source = new StepDefinition();
        source.setId("read-data");
        source.setType(StepType.SOURCE);
        source.setConnector("csv");
        source.setConfig(Map.of("path", input.toString(), "dataset", "data_raw"));
        source.setOutput("data_raw");

        StepDefinition sink = new StepDefinition();
        sink.setId("write-data");
        sink.setType(StepType.SINK);
        sink.setConnector("csv");
        sink.setDependsOn(List.of("read-data"));
        sink.setConfig(Map.of("path", output.toString()));
        sink.setInput("data_raw");

        pipeline.setSteps(List.of(source, sink));

        String executionId = "exec-resume-test";

        // Pre-cargar checkpoint del primer paso (simula que ya se ejecuto)
        checkpointManager.save(new dev.streamforge.core.checkpoint.StepCheckpoint(
            executionId, "read-data",
            java.time.Instant.now(), "csv://data_raw", 2L, null
        ));

        ExecutionContext ctx = new ExecutionContext(
            executionId, pipeline, Map.of()
        );

        PipelineExecutor.PipelineExecutionResult result = executor.execute(pipeline, ctx);

        // El paso read-data debe estar SKIPPED
        boolean sourceSkipped = result.stepResults().stream()
            .anyMatch(r -> r.stepId().equals("read-data") && r.wasSkipped());

        assertTrue(sourceSkipped,
            "El paso 'read-data' debe estar SKIPPED porque tiene checkpoint");
    }
}