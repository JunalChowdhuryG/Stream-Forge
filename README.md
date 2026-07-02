# StreamForge

> Motor de Pipelines de Datos con Linaje, Calidad y Observabilidad

[![CI](https://github.com/JunalChowdhuryG/streamforge/actions/workflows/ci.yml/badge.svg)](https://github.com/JunalChowdhuryG/streamforge/actions)
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


```bash
docker compose -f docker/docker-compose.yml up -d
mvn spring-boot:run -pl streamforge-engine
```

## Modulos

| Modulo                   | Descripcion                                                   |
| ------------------------ | ------------------------------------------------------------- |
| `streamforge-core`       | Libreria Java pura - DAG, FSM, linaje, calidad, checkpointing |
| `streamforge-engine`     | Spring Boot - orquestacion y ciclo de vida de pipelines       |
| `streamforge-api`        | REST API - administracion, linaje y calidad                   |
| `streamforge-quality`    | Motor de reglas de calidad y reportes                         |
| `streamforge-connectors` | Conectores CSV, JSON, PostgreSQL y Kafka                      |
