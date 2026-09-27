plugins {
    id("antubank.spring-boot-conventions")
}

description = "transfer-service: transferencias idempotentes en CLP (persiste Idempotency-Key con su resultado)."

dependencies {
    implementation(project(":common-domain"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    // Observabilidad: registro Prometheus de Micrometer (tarea 11.1, Requisito 11, criterios 1 y 2).
    // Su presencia en runtime habilita el endpoint /actuator/prometheus scrapeable por Prometheus.
    runtimeOnly(libs.micrometer.registry.prometheus)
    // Observabilidad — trazas distribuidas (tarea 11.2, Requisito 11, criterio 3). El puente de
    // Micrometer Tracing hacia OpenTelemetry instrumenta los bordes HTTP (RestTemplate/WebClient del
    // llamado síncrono al ledger) y el producer Kafka del relay de la outbox, propagando el contexto
    // de traza (traceparent W3C en HTTP; headers en Kafka) para que la traza cruce
    // gateway → transfer → ledger. El exporter OTLP envía los spans a Jaeger (ver application.yml).
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    // Observabilidad — logs estructurados a Loki (tarea 11.3, Requisito 11, criterio 4). El appender
    // loki4j (activado en logback-spring.xml, perfil !test) envía los logs en JSON a Loki incluyendo
    // los campos MDC traceId/spanId que popula Micrometer Tracing, para correlacionar logs ↔ trazas
    // por trace-id en Grafana. runtimeOnly: se referencia solo desde la config de Logback.
    runtimeOnly(libs.loki.logback.appender)
    implementation(libs.springdoc.openapi.webmvc)

    // Seguridad: resource server que valida el JWT de Keycloak contra el JWKS del realm
    // (tarea 8.2, Requisito 8, criterio 4).
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    // Mensajería: producer Kafka para el relay de la outbox (tarea 5.2, Requisito 5).
    implementation(libs.spring.kafka)

    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
}
