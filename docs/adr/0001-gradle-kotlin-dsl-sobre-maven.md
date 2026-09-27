# ADR-0001: Gradle (Kotlin DSL) sobre Maven

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-001)

## Contexto

Antu Bank es un **monorepo multi-módulo**: un dominio compartido (`common-domain`), seis servicios
Spring Boot (`api-gateway`, `account-service`, `ledger-service`, `transfer-service`,
`fraud-service`, `notification-service`) y un frontend Next.js. Un monorepo de este tamaño necesita
una herramienta de build capaz de compilar solo lo que cambió, cachear resultados entre módulos y
compartir convenciones (versión de Java, plugins de Spring, configuración de testing) sin duplicar
configuración en cada módulo.

Las dos opciones naturales en el ecosistema JVM son **Maven** (declarativo, XML, muy difundido) y
**Gradle** (imperativo/declarativo híbrido, con builds incrementales y cache de tareas). La
gestión centralizada de versiones y las convenciones compartidas eran requisitos duros para
mantener el monorepo coherente a medida que crece el número de módulos.

## Decisión

Usar **Gradle con Kotlin DSL** como sistema de build, apoyado en dos mecanismos:

- **Version catalog** (`gradle/libs.versions.toml`) como fuente única de versiones para todo el
  monorepo (Java 21, Spring Boot 3.3.6, Spring Cloud 2023.0.4, Testcontainers, etc.).
- **Convention plugins** en `build-logic/` (`antubank.java-conventions`,
  `antubank.spring-boot-conventions`) que encapsulan la configuración compartida y se aplican por
  módulo, evitando duplicación.

## Consecuencias

**Beneficios**

- Builds incrementales y cache de tareas superiores en un monorepo multi-módulo: solo se recompila
  y se re-testea lo afectado por un cambio.
- El Kotlin DSL aporta tipado y autocompletado en la configuración del build, reduciendo errores
  respecto de un DSL sin tipos.
- Las convenciones compartidas viven en un solo lugar (`build-logic`), de modo que agregar un
  servicio nuevo es aplicar un convention plugin, no copiar decenas de líneas de configuración.
- El version catalog elimina la deriva de versiones entre módulos.

**Trade-offs**

- Curva de aprendizaje de los **convention plugins** y del modelo de build de Gradle, superior a
  la de un `pom.xml` declarativo de Maven.
- Menos "convención sobre configuración" que Maven: el equipo debe definir y mantener sus propias
  convenciones (que es justamente lo que `build-logic` centraliza).

Este trade-off se consideró aceptable: el costo inicial de las convenciones se amortiza con la
velocidad de build y la mantenibilidad a lo largo de la vida del monorepo.
