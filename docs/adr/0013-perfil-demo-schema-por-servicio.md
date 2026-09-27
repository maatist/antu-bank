# ADR-0013: Perfil `demo` reducido con schema-por-servicio

- **Estado:** Aceptado
- **Fecha:** decisión reflejada en `infra/docker-compose.demo.yml` e `infra/postgres/demo-init.sql` (tarea 12a)

## Contexto

El [ADR-0004](0004-database-per-service.md) establece **database-per-service estricto**: cada
servicio con estado tiene su propia instancia de PostgreSQL. Ese modelo es correcto para local y
AWS, pero choca con el objetivo del track público always-on (ver
[ADR-0009](0009-despliegue-doble-track.md)): mantener varias instancias de PostgreSQL y un cluster
Kafka encendidos 24/7 en un PaaS gratuito es caro y desproporcionado para una demo.

El desafío es **reducir la huella de infraestructura del entorno público sin perder funcionalidad
testeable** ni el aislamiento lógico entre servicios.

## Decisión

Definir un **perfil `demo` reducido** para el track público:

- Un **único PostgreSQL** con **un schema por servicio con estado** (`account`, `ledger`,
  `transfer`), en lugar de varias instancias. El script `demo-init.sql` crea los schemas al
  inicializar el volumen y cada servicio se conecta con `currentSchema=<su schema>`, registrando su
  historial de Flyway en él. Los servicios sin estado (`fraud`, `notification`, `api-gateway`) no
  usan base.
- **Consolidación de contenedores** y **Kafka gestionado gratuito** (Upstash/Redpanda) en lugar de
  un broker propio.

Esto es una **excepción deliberada y acotada** al ADR-0004, válida solo en el entorno público. En
local y AWS se mantiene database-per-service estricto.

## Consecuencias

**Beneficios**

- Huella de infraestructura mucho menor: una sola base y broker gestionado permiten mantener la
  demo encendida 24/7 sin costo relevante.
- Se conserva **aislamiento lógico** entre servicios (schema por servicio) y toda la funcionalidad
  sigue siendo testeable end-to-end.
- La app no cambia su lógica: solo cambia la configuración de conexión (schema y URLs) por perfil.

**Trade-offs**

- El aislamiento es **lógico, no físico**: los servicios comparten una instancia de PostgreSQL en
  la demo, por lo que no hay el mismo grado de autonomía que con instancias separadas.
- Se mantiene **una configuración de despliegue adicional** (el perfil demo) además del stack
  completo, con el costo de que ambas sigan alineadas.

El aislamiento por schema es un punto intermedio pragmático: suficiente para demostrar el diseño y
económico para una demo pública permanente. El modelo estricto sigue disponible donde importa
demostrarlo.
