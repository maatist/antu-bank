rootProject.name = "antu-bank"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Módulo de dominio compartido (dominio puro, sin Spring)
include(":common-domain")

// Microservicios (se materializan en sus tareas respectivas del plan).
// A medida que cada servicio se implemente, se descomenta su include:
include(":services:account-service")
include(":services:ledger-service")
include(":services:transfer-service")
include(":services:fraud-service")
include(":services:notification-service")
include(":services:api-gateway")
