plugins {
    id("antubank.spring-boot-conventions")
}

description = "fraud-service: reglas de fraude (monto y velocidad, umbrales CLP) sobre eventos de " +
        "transferencia; emite TransferFlagged. Servicio stateless (ventana en memoria)."

dependencies {
    implementation(project(":common-domain"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    // Observabilidad: registro Prometheus de Micrometer (tarea 11.1, Requisito 11, criterios 1 y 2).
    // Su presencia en runtime habilita el endpoint /actuator/prometheus scrapeable por Prometheus.
    runtimeOnly(libs.micrometer.registry.prometheus)
    // Observabilidad — trazas distribuidas (tarea 11.2, Requisito 11, criterio 3). El puente de
    // Micrometer Tracing hacia OpenTelemetry instrumenta el consumer y el producer Kafka, continuando
    // la traza propagada en los headers del evento de transferencia para correlacionar el análisis de
    // fraude con la traza distribuida. El exporter OTLP envía los spans a Jaeger (ver application.yml).
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    // Observabilidad — logs estructurados a Loki (tarea 11.3, Requisito 11, criterio 4). El appender
    // loki4j (activado en logback-spring.xml, perfil !test) envía los logs en JSON a Loki incluyendo
    // los campos MDC traceId/spanId que popula Micrometer Tracing, para correlacionar logs ↔ trazas
    // por trace-id en Grafana. runtimeOnly: se referencia solo desde la config de Logback.
    runtimeOnly(libs.loki.logback.appender)

    // Mensajería: consumer del evento TransferConfirmed y producer del evento TransferFlagged
    // (tarea 7.1, Requisito 7, criterios 1 y 2). El servicio es stateless: no usa PostgreSQL,
    // la ventana deslizante de la regla de velocidad se mantiene en memoria.
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)
}
