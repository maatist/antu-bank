plugins {
    `kotlin-dsl`
}

// Los convention plugins viven en src/main/kotlin como scripts *.gradle.kts.
// Para poder aplicar los plugins de Spring Boot desde un precompiled script plugin,
// se declaran como dependencias de este build. Las versiones se leen del version catalog
// compartido (libs), registrado en settings.gradle.kts de build-logic.
val springBootVersion = libs.versions.springBoot.get()
val springDepMgmtVersion = libs.versions.springDependencyManagement.get()

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:$springBootVersion")
    implementation("io.spring.gradle:dependency-management-plugin:$springDepMgmtVersion")
}
