# syntax=docker/dockerfile:1.7
# =============================================================================
# api-gateway — Dockerfile multi-stage (Java 21)
# =============================================================================
# El contexto de build DEBE ser la raíz del monorepo, porque el bootJar necesita
# la configuración compartida de Gradle (settings.gradle.kts, build-logic con los
# convention plugins, el version catalog y el módulo common-domain).
#
#   docker build -f services/api-gateway/Dockerfile -t antubank/api-gateway .
#
# Etapa 1 (build): compila el bootJar con el JDK 21 + wrapper de Gradle.
# Etapa 2 (runtime): ejecuta el jar sobre un JRE 21 slim, con usuario no-root y
# el jar en capas (layered jar de Spring Boot) para aprovechar el cache de Docker.
# =============================================================================

# -----------------------------------------------------------------------------
# Etapa 1 — build
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build

# Módulo Gradle de este servicio (ruta del proyecto en settings.gradle.kts).
ARG SERVICE_MODULE=:services:api-gateway
WORKDIR /workspace

# --- Copia primero los archivos de configuración del build para maximizar el
#     cache: mientras no cambien, Docker reutiliza la capa de dependencias. ---
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY build-logic build-logic
COPY common-domain common-domain
COPY services/api-gateway services/api-gateway

RUN chmod +x gradlew

# --- Compila el jar ejecutable. --no-daemon es lo recomendado en contenedores. ---
# Se cachea GRADLE_USER_HOME entre builds para acelerar la resolución de dependencias.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon "${SERVICE_MODULE}:bootJar"

# --- Extrae las capas del layered jar de Spring Boot (dependencias, loader,
#     snapshots y app) para copiarlas por separado en la etapa de runtime. ---
RUN mkdir -p /workspace/extracted \
    && java -Djarmode=tools \
        -jar services/api-gateway/build/libs/api-gateway.jar \
        extract --layers --launcher --destination /workspace/extracted

# -----------------------------------------------------------------------------
# Etapa 2 — runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Usuario no-root para reducir la superficie de ataque.
RUN groupadd --system spring && useradd --system --gid spring --home /app spring
WORKDIR /app

# Copia de capas de menor a mayor frecuencia de cambio (mejor cache de Docker).
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring

# Puerto HTTP del gateway (coincide con GATEWAY_PORT en application.yml).
ENV SERVER_PORT=8080
EXPOSE 8080

# El JVM respeta los límites de memoria del contenedor por defecto en Java 21.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
