# StreamForge

> Motor de Pipelines de Datos con Linaje, Calidad y Observabilidad

[![CI](https://github.com/JunalChowdhuryG/Stream-Forge/actions/workflows/ci.yml/badge.svg)](https://github.com/JunalChowdhuryG/Stream-Forge/actions)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=spring-boot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-3.6-231F20?logo=apache-kafka)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## El problema

Los pipelines de datos en produccion degradan su calidad silenciosamente.
Un campo que empezo con 2% de nulos puede llegar al 40% sin que nadie lo note.
Cuando el problema se detecta, hay semanas de datos corruptos y nadie sabe
que transformacion lo introdujo.

## La solucion

StreamForge es el motor que da visibilidad, control de calidad y resiliencia
a cualquier pipeline de datos:

```
YAML del pipeline
    │
    ▼
DAGBuilder -> TopologicalSorter (Kahn)
    │
    ▼  [pasos independientes en paralelo via Virtual Threads]
PipelineExecutor
    │
    ├── QualityRuleEngine -> reporte HTML por paso
    ├── LineageRecorder   -> grafo de linaje campo a campo
    └── CheckpointManager -> reanudacion desde punto de falla
```

---

## Inicio rapido

```bash
docker compose -f docker/docker-compose.yml up -d
mvn spring-boot:run -pl streamforge-engine
curl -X POST http://localhost:8080/pipelines/orders-daily-etl/execute \
  -H 'Content-Type: application/json' \
  -d '{"date": "2026-01-15"}'
```

---

## Estado del proyecto

| Hito | Descripcion | Estado |
|------|-------------|--------|
| H1 | Infraestructura base y proyecto Maven multi-modulo | Completado |
| H2 | DAGBuilder y ordenamiento topologico con Kahn | Completado |
| H3 | PipelineFSM, StepFSM y CheckpointManager | Completado |
| H4 | Sistema de linaje de datos - grafo e impacto inverso | Completado |
| H5 | Motor de reglas de calidad - 5 evaluadores + reporte HTML | Completado |
| H6 | Conectores CSV, JSON, PostgreSQL y Kafka | Completado |
| H7 | Observabilidad - metricas Prometheus y dashboard Grafana | Completado |
| H8 | API REST, chaos tests y portafolio listo | Completado |

---

## Modulos

| Modulo | Descripcion |
|--------|-------------|
| `streamforge-core` | DAG, FSM, linaje, checkpointing - sin dependencias de framework |
| `streamforge-engine` | Spring Boot - orquestacion y ciclo de vida de pipelines |
| `streamforge-api` | REST API - administracion, linaje y calidad |
| `streamforge-quality` | Motor de reglas de calidad y reportes HTML |
| `streamforge-connectors` | Conectores CSV, JSON, PostgreSQL y Kafka |

---

## Tests

```bash
# Todos los tests
mvn test

# Unit tests (sin Docker)
mvn test -pl streamforge-core
mvn test -pl streamforge-quality

# Integration tests (requiere Docker)
mvn test -pl streamforge-engine
```

---

## Servicios Docker

| Servicio   | Puerto | Descripcion |
|------------|--------|-------------|
| PostgreSQL | 5432   | Metadata, linaje y checkpoints |
| Kafka      | 9092   | Broker de streaming |
| Prometheus | 9090   | Metricas |
| Grafana    | 3000   | Dashboards (admin/admin) |

