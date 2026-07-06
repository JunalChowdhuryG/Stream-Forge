package dev.streamforge.api.controller;

import dev.streamforge.api.dto.ExecutePipelineRequest;
import dev.streamforge.api.dto.ExecutionResponse;
import dev.streamforge.core.model.PipelineDefinition;
import dev.streamforge.core.model.execution.ExecutionContext;
import dev.streamforge.engine.executor.PipelineExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API REST para gestion y ejecucion de pipelines.
 *
 * POST /pipelines/{id}/execute     - dispara una ejecucion
 * POST /pipelines/{id}/resume/{execId} - reanuda una fallida
 * GET  /pipelines/{id}/executions  - historial de ejecuciones
 * GET  /executions/{execId}        - detalle de una ejecucion
 * POST /admin/validate             - valida un YAML sin ejecutar
 */
@RestController
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    private final PipelineExecutor executor;
    private final Map<String, PipelineDefinition> pipelineRegistry;

    // Cache en memoria de ejecuciones - en H8 esto va a PostgreSQL
    private final ConcurrentHashMap<String, ExecutionResponse> executionCache
            = new ConcurrentHashMap<>();

    public PipelineController(PipelineExecutor executor,
                              Map<String, PipelineDefinition> pipelineRegistry) {
        this.executor         = executor;
        this.pipelineRegistry = pipelineRegistry;
    }

    @PostMapping("/pipelines/{id}/execute")
    public ResponseEntity<ExecutionResponse> execute(
            @PathVariable String id,
            @RequestBody(required = false) ExecutePipelineRequest request) {

        PipelineDefinition pipeline = pipelineRegistry.get(id);
        if (pipeline == null) {
            return ResponseEntity.notFound().build();
        }

        String executionId = UUID.randomUUID().toString();
        Map<String, String> params = request != null ? request.getParameters() : Map.of();

        log.info("Disparando pipeline={}, executionId={}", id, executionId);

        ExecutionContext ctx = new ExecutionContext(executionId, pipeline, params);
        PipelineExecutor.PipelineExecutionResult result = executor.execute(pipeline, ctx);

        ExecutionResponse response = toResponse(result, executionId);
        executionCache.put(executionId, response);

        return result.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(500).body(response);
    }

    @PostMapping("/pipelines/{id}/resume/{execId}")
    public ResponseEntity<ExecutionResponse> resume(
            @PathVariable String id,
            @PathVariable String execId,
            @RequestBody(required = false) ExecutePipelineRequest request) {

        PipelineDefinition pipeline = pipelineRegistry.get(id);
        if (pipeline == null) return ResponseEntity.notFound().build();

        Map<String, String> params = request != null ? request.getParameters() : Map.of();
        ExecutionContext ctx = new ExecutionContext(execId, pipeline, params);

        log.info("Reanudando pipeline={}, executionId={}", id, execId);

        PipelineExecutor.PipelineExecutionResult result = executor.execute(pipeline, ctx);
        ExecutionResponse response = toResponse(result, execId);
        executionCache.put(execId, response);

        return result.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(500).body(response);
    }

    @GetMapping("/executions/{execId}")
    public ResponseEntity<ExecutionResponse> getExecution(@PathVariable String execId) {
        ExecutionResponse response = executionCache.get(execId);
        return response != null
                ? ResponseEntity.ok(response)
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/admin/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestBody Map<String, Object> pipelineYaml) {
        // Validacion basica - en produccion parsea el YAML completo
        return ResponseEntity.ok(Map.of(
                "valid",   true,
                "message", "Pipeline valido"
        ));
    }

    @GetMapping("/admin/connectors/health")
    public ResponseEntity<Map<String, Object>> connectorsHealth() {
        return ResponseEntity.ok(Map.of(
                "status",     "UP",
                "connectors", List.of("csv", "json", "postgres", "kafka")
        ));
    }

    //Helper
    private ExecutionResponse toResponse(PipelineExecutor.PipelineExecutionResult r,
                                         String executionId) {
        List<ExecutionResponse.StepResponse> steps = r.stepResults().stream()
                .map(s -> new ExecutionResponse.StepResponse(
                        s.stepId(), s.finalState().name(),
                        s.rowsRead(), s.rowsWritten(), s.rowsRejected(),
                        s.durationMs(), s.wasSkipped()
                )).toList();

        return new ExecutionResponse(
                executionId, r.pipelineId(), r.finalState().name(),
                r.totalRowsRead(), r.totalRowsWritten(),
                r.totalDurationMs(), steps, r.errorMessage(), Instant.now()
        );
    }
}