// Convención para microservicios Spring Boot de Antu Bank.
// Hereda la configuración Java compartida y aplica los plugins de Spring Boot.

plugins {
    id("antubank.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// Sobrescribe la versión de Testcontainers gestionada por el BOM de Spring Boot.
// Spring Boot 3.3.x fija Testcontainers en 1.19.x (docker-java 3.3.6, API 1.32), incompatible con
// Docker Engine 29+ (API mínima 1.44). Forzamos 1.21.4, que negocia API 1.44.
extra["testcontainers.version"] = "1.21.4"

// Los tests de integración con Testcontainers requieren Docker en el entorno.
// Nota: se usa Testcontainers >= 1.21.4, que fija la API de Docker en 1.44 y es compatible con
// Docker Engine 29+. Versiones anteriores negociaban API 1.32 y eran rechazadas por el daemon.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    systemProperty("testcontainers.reuse.enable", "false")
}
