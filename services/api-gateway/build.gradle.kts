plugins {
    id("antubank.spring-boot-conventions")
}

description = "api-gateway: punto de entrada único (Spring Cloud Gateway, reactivo/WebFlux). " +
        "Enruta el tráfico externo a los servicios internos, valida el JWT en el borde y propaga " +
        "el token a downstream (tarea 9.1, Requisito 9)."

// Importa la BOM de Spring Cloud vía io.spring.dependency-management (aplicado por la convención
// spring-boot-conventions). Alinea Spring Cloud Gateway y demás artefactos del release train con
// Spring Boot 3.3.5. La release train 2023.0.x (Leyton) es compatible con Spring Boot 3.3.x.
dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.bom.get().toString())
    }
}

dependencies {
    // Spring Cloud Gateway es REACTIVO: corre sobre Spring WebFlux (Netty), NO sobre el stack
    // servlet (spring-boot-starter-web). Por eso este módulo NO declara starter-web ni springdoc
    // webmvc: mezclar servlet y reactivo rompe el auto-arranque del gateway. El starter de gateway
    // ya trae WebFlux transitivamente; se añade webflux explícito para dejar la intención clara.
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.webflux)

    // Resiliencia (tarea 9.2, Requisito 9, criterio 4): Spring Cloud Circuit Breaker + Resilience4j
    // reactivo. Habilita el filtro CircuitBreaker en las rutas del gateway y la configuración de
    // instancias Resilience4j (umbral de fallo, ventana, timeouts). El rate limiting (criterio 3)
    // se implementa como filtro global en memoria (sin Redis), por lo que no requiere dependencias
    // extra. Ver GatewayResilienceProperties, RateLimitingGlobalFilter y FallbackController.
    implementation(libs.spring.cloud.starter.circuitbreaker.reactor.resilience4j)

    // Seguridad en el borde: el gateway actúa como OAuth2 Resource Server reactivo. Valida el JWT
    // de Keycloak contra el JWKS del realm antu-bank y luego propaga el header Authorization a los
    // servicios downstream (tarea 9.1, Requisito 9, criterio 2; design.md sección 7).
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    // GraphQL BFF (tarea 9.3, Requisito 9, criterios 5 y 6): Spring for GraphQL sobre WebFlux.
    // Expone /graphql (endpoint HTTP) y la UI GraphiQL como playground. El schema `me { accounts,
    // balances, transfers }` agrega los REST internos (account/ledger/transfer) usando el WebClient
    // reactivo de WebFlux, propagando el Bearer token entrante a cada llamada downstream.
    implementation(libs.spring.boot.starter.graphql)

    implementation(libs.spring.boot.starter.actuator)
    // Observabilidad: registro Prometheus de Micrometer (tarea 11.1, Requisito 11, criterios 1 y 2).
    // Su presencia en runtime habilita el endpoint /actuator/prometheus scrapeable por Prometheus.
    runtimeOnly(libs.micrometer.registry.prometheus)
    // Observabilidad — trazas distribuidas (tarea 11.2, Requisito 11, criterio 3). El gateway es el
    // ORIGEN de la traza en el borde: el puente de Micrometer Tracing hacia OpenTelemetry instrumenta
    // el server WebFlux y el WebClient reactivo con que enruta a downstream, iniciando el span raíz y
    // propagando el contexto (traceparent W3C) para que la traza cruce gateway → transfer → ledger.
    // El exporter OTLP envía los spans a Jaeger (ver application.yml).
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    // Observabilidad — logs estructurados a Loki (tarea 11.3, Requisito 11, criterio 4). El appender
    // loki4j (activado en logback-spring.xml, perfil !test) envía los logs en JSON a Loki incluyendo
    // los campos MDC traceId/spanId que popula Micrometer Tracing, para correlacionar logs ↔ trazas
    // por trace-id en Grafana. runtimeOnly: se referencia solo desde la config de Logback.
    runtimeOnly(libs.loki.logback.appender)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    // spring-graphql-test aporta HttpGraphQlTester/GraphQlTester para probar la query `me` sin
    // levantar los servicios downstream (se mockea el cliente de agregación).
    testImplementation(libs.spring.graphql.test)
    // reactor-test aporta StepVerifier/WebTestClient reactivo (viene con spring-boot-starter-test).
}
