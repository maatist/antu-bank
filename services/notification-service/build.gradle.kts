plugins {
    id("antubank.spring-boot-conventions")
}

description = "notification-service: consume eventos notificables (TransferConfirmed, TransferFlagged) " +
        "y envía notificaciones asíncronas mock (log/email) con contenido localizable es/en. " +
        "Servicio stateless: no usa PostgreSQL."

dependencies {
    implementation(project(":common-domain"))

    // web: aporta el MessageSource autoconfigurado (spring.messages.*) para el contenido i18n
    // y el actuator de health; el servicio no expone endpoints REST de negocio.
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    // Observabilidad: registro Prometheus de Micrometer (tarea 11.1, Requisito 11, criterios 1 y 2).
    // Su presencia en runtime habilita el endpoint /actuator/prometheus scrapeable por Prometheus.
    runtimeOnly(libs.micrometer.registry.prometheus)
    // Observabilidad — trazas distribuidas (tarea 11.2, Requisito 11, criterio 3). El puente de
    // Micrometer Tracing hacia OpenTelemetry instrumenta el consumer Kafka, continuando la traza
    // propagada en los headers del evento para correlacionar la notificación con la traza distribuida.
    // El exporter OTLP envía los spans a Jaeger (ver application.yml).
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    // Observabilidad — logs estructurados a Loki (tarea 11.3, Requisito 11, criterio 4). El appender
    // loki4j (activado en logback-spring.xml, perfil !test) envía los logs en JSON a Loki incluyendo
    // los campos MDC traceId/spanId que popula Micrometer Tracing, para correlacionar logs ↔ trazas
    // por trace-id en Grafana. runtimeOnly: se referencia solo desde la config de Logback.
    runtimeOnly(libs.loki.logback.appender)

    // Mensajería: consumer de los eventos notificables TransferConfirmed (topic transfer.events)
    // y TransferFlagged (topic fraud.events) — tarea 7.2, Requisito 7, criterios 3 y 4.
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)
}
