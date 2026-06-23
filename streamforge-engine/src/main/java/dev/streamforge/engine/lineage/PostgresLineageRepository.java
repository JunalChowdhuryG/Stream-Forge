package dev.streamforge.engine.lineage;

import dev.streamforge.core.lineage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Persistencia del grafo de linaje en PostgreSQL.
 */
@Repository
public class PostgresLineageRepository implements LineageRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresLineageRepository.class);

    private final JdbcTemplate jdbc;

    public PostgresLineageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LineageNode saveNode(LineageNode node) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO lineage_nodes
                    (execution_id, dataset_name, field_name, data_type, step_id, created_at)
                VALUES (?::uuid, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, node.getExecutionId());
            ps.setString(2, node.getDatasetName());
            ps.setString(3, node.getFieldName());
            ps.setString(4, node.getDataType());
            ps.setString(5, node.getStepId());
            ps.setTimestamp(6, Timestamp.from(node.getCreatedAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            node.setId(key.longValue());
        }
        return node;
    }

    @Override
    public void saveEdge(LineageEdge edge) {
        jdbc.update("""
            INSERT INTO lineage_edges
                (execution_id, source_node_id, target_node_id,
                 transformation_type, step_id, created_at)
            VALUES (?::uuid, ?, ?, ?, ?, ?)
            """,
            edge.getExecutionId(),
            edge.getSource().getId(),
            edge.getTarget().getId(),
            edge.getTransformationType().name(),
            edge.getStepId(),
            Timestamp.from(edge.getCreatedAt())
        );
    }

    @Override
    public void saveGraph(LineageGraph graph) {
        log.info("Persistiendo grafo de linaje — executionId={}, nodes={}, edges={}",
                graph.getExecutionId(), graph.getNodeCount(), graph.getEdgeCount());

        //Persistir nodos primero para obtener sus IDs
        for (LineageNode node : graph.getNodes()) {
            if (node.getId() == null) {
                saveNode(node);
            }
        }

        //Persistir aristas con referencias a los IDs de nodos
        for (LineageEdge edge : graph.getEdges()) {
            saveEdge(edge);
        }

        log.info("Grafo persistido — executionId={}", graph.getExecutionId());
    }

    @Override
    public List<LineageNode> findNodesByExecution(String executionId) {
        return jdbc.query("""
            SELECT id, execution_id, dataset_name, field_name, data_type, step_id, created_at
            FROM lineage_nodes
            WHERE execution_id = ?::uuid
            ORDER BY id
            """,
            (rs, row) -> {
                LineageNode node = new LineageNode(
                    rs.getString("execution_id"),
                    rs.getString("dataset_name"),
                    rs.getString("field_name"),
                    rs.getString("data_type"),
                    rs.getString("step_id")
                );
                node.setId(rs.getLong("id"));
                return node;
            },
            executionId
        );
    }

    @Override
    public List<LineageEdge> findEdgesByExecution(String executionId) {
        // Cargar nodos primero para construir las aristas
        List<LineageNode> nodes = findNodesByExecution(executionId);
        Map<Long, LineageNode> nodeById = new java.util.HashMap<>();
        for (LineageNode n : nodes) nodeById.put(n.getId(), n);

        return jdbc.query("""
            SELECT id, execution_id, source_node_id, target_node_id,
                   transformation_type, step_id, created_at
            FROM lineage_edges
            WHERE execution_id = ?::uuid
            ORDER BY id
            """,
            (rs, row) -> {
                LineageNode source = nodeById.get(rs.getLong("source_node_id"));
                LineageNode target = nodeById.get(rs.getLong("target_node_id"));
                LineageEdge edge = new LineageEdge(
                    rs.getString("execution_id"),
                    source, target,
                    TransformationType.valueOf(rs.getString("transformation_type")),
                    rs.getString("step_id")
                );
                edge.setId(rs.getLong("id"));
                return edge;
            },
            executionId
        );
    }
}