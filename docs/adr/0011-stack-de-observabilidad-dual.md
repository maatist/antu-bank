# ADR-0011: Stack de observabilidad dual (métricas + trazas + logs)

- **Estado:** Aceptado
- **Fecha:** decisión reflejada en el código e infraestructura (tarea 11)

## Contexto

Un sistema de microservicios distribuido es difícil de diagnosticar: una sola transferencia cruza
el gateway, `transfer-service` y `ledger-service`, más los consumidores de Kafka. Cuando algo
falla o se degrada, no basta con un tipo de señal. Los **tres pilares de la observabilidad**
—métricas, trazas y logs— responden preguntas distintas:

- **Métricas:** ¿cuál es la latencia, el error rate y el throughput agregados? (tendencias)
- **Trazas:** ¿por dónde pasó *esta* request y dónde se fue el tiempo? (causa de un caso concreto)
- **Logs:** ¿qué ocurrió exactamente en *este* punto? (detalle)

El requisito de observabilidad (Requisito 11) pide explícitamente las tres señales, además de que
las trazas crucen `gateway → transfer → ledger` y que los logs se correlacionen con las trazas.

## Decisión

Adoptar un **stack de observabilidad completo con tecnologías estándar**, todas alineadas por el
BOM de Spring Boot 3.3.6:

- **Métricas:** Micrometer → **Prometheus**, con dashboards en **Grafana** (latencia, error rate,
  throughput). Cada servicio expone `/actuator/prometheus`.
- **Trazas:** Micrometer Tracing (bridge a OpenTelemetry) con exporter **OTLP → Jaeger**;
  propagación de contexto W3C `traceparent` en HTTP y por headers en Kafka, de modo que la traza
  cruce gateway → transfer → ledger.
- **Logs:** JSON estructurado enviado a **Loki** vía el appender `loki4j`, incluyendo `traceId` y
  `spanId` desde el MDC para **correlacionar logs con trazas por trace-id** en Grafana.

En local, todo el stack (Prometheus, Grafana, Jaeger, Loki) se levanta junto con
`infra/docker-compose.yml`.

## Consecuencias

**Beneficios**

- Diagnóstico completo: se puede pasar de una métrica anómala a la traza del caso concreto y de ahí
  a los logs de ese trace-id.
- Tecnologías estándar y ampliamente conocidas (Prometheus/Grafana/Jaeger/Loki/OpenTelemetry), lo
  que suma como evidencia de portafolio.
- La instrumentación es en gran parte automática vía Micrometer/OpenTelemetry, con poco código a
  medida.

**Trade-offs**

- Un stack de observabilidad **pesado de operar** (cuatro componentes de infraestructura además de
  la app), notable sobre todo en el entorno local y en la demo con recursos limitados.
- El envío de logs vía HTTP push (loki4j) acopla cada servicio a la disponibilidad de Loki para el
  transporte de logs, mitigado porque es asíncrono y no bloquea la request.

Para un proyecto que busca demostrar operabilidad de nivel producción, el costo del stack completo
es justamente parte del valor.
