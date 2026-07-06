-- Ejecuciones de pipeline
CREATE TABLE pipeline_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     VARCHAR(255) NOT NULL,
    pipeline_version VARCHAR(50),
    state           VARCHAR(50)  NOT NULL DEFAULT 'CREATED',
    triggered_by    VARCHAR(255),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    checkpoint_step VARCHAR(255),
    error_message   TEXT,
    parameters      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ejecuciones de paso
CREATE TABLE step_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID NOT NULL REFERENCES pipeline_executions(id),
    step_id         VARCHAR(255) NOT NULL,
    state           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    rows_read       BIGINT       DEFAULT 0,
    rows_written    BIGINT       DEFAULT 0,
    rows_rejected   BIGINT       DEFAULT 0,
    latency_ms      BIGINT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Checkpoints por etapa
CREATE TABLE step_checkpoints (
    execution_id    UUID         NOT NULL REFERENCES pipeline_executions(id),
    step_id         VARCHAR(255) NOT NULL,
    completed_at    TIMESTAMPTZ  NOT NULL,
    output_location TEXT,
    rows_processed  BIGINT,
    checksum        VARCHAR(64),
    PRIMARY KEY (execution_id, step_id)
);

-- Nodos del grafo de linaje
CREATE TABLE lineage_nodes (
    id              BIGSERIAL    PRIMARY KEY,
    execution_id    UUID         NOT NULL REFERENCES pipeline_executions(id),
    dataset_name    VARCHAR(255) NOT NULL,
    field_name      VARCHAR(255) NOT NULL,
    data_type       VARCHAR(64),
    step_id         VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Aristas del grafo de linaje
CREATE TABLE lineage_edges (
    id                  BIGSERIAL PRIMARY KEY,
    execution_id        UUID      NOT NULL REFERENCES pipeline_executions(id),
    source_node_id      BIGINT    NOT NULL REFERENCES lineage_nodes(id),
    target_node_id      BIGINT    NOT NULL REFERENCES lineage_nodes(id),
    transformation_type VARCHAR(64),
    step_id             VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Resultados de calidad
CREATE TABLE quality_results (
    id              BIGSERIAL    PRIMARY KEY,
    execution_id    UUID         NOT NULL REFERENCES pipeline_executions(id),
    step_id         VARCHAR(255) NOT NULL,
    field_name      VARCHAR(255) NOT NULL,
    rule_type       VARCHAR(64)  NOT NULL,
    passed          BIGINT       DEFAULT 0,
    failed          BIGINT       DEFAULT 0,
    failure_rate    DECIMAL(5,4) DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indices
CREATE INDEX idx_pipeline_executions_pipeline_id ON pipeline_executions(pipeline_id);
CREATE INDEX idx_pipeline_executions_state       ON pipeline_executions(state);
CREATE INDEX idx_step_executions_execution_id    ON step_executions(execution_id);
CREATE INDEX idx_lineage_nodes_execution_id      ON lineage_nodes(execution_id);
CREATE INDEX idx_lineage_nodes_dataset_field     ON lineage_nodes(dataset_name, field_name);
CREATE INDEX idx_lineage_edges_execution_id      ON lineage_edges(execution_id);
CREATE INDEX idx_quality_results_execution_id    ON quality_results(execution_id);