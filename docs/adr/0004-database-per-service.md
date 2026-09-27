# ADR-0004: Database-per-service estricto (local/AWS)

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-004)

## Contexto

Antu Bank es una arquitectura de **microservicios**. Uno de los principios centrales de ese estilo
es que cada servicio sea dueño de sus datos: si varios servicios comparten una misma base y tablas,
se acoplan por el esquema, pierden autonomía de despliegue y una migración de uno puede romper a
otro. El acoplamiento por base de datos es, en la práctica, un monolito distribuido.

En Antu Bank los servicios con estado son `account-service` (cuentas y titulares),
`ledger-service` (asientos de doble entrada) y `transfer-service` (transferencias, idempotencia y
outbox). Cada uno tiene un modelo de datos y un ciclo de vida propios. Los servicios `fraud`,
`notification` y `api-gateway` no tienen estado persistente.

Existe una tensión con el objetivo de portafolio: mantener múltiples instancias de PostgreSQL
encendidas 24/7 en un entorno público gratuito es caro e innecesario para una demo.

## Decisión

Aplicar **database-per-service estricto** en los entornos **local** y **AWS**: cada servicio con
estado tiene su **propia base de datos PostgreSQL**, con migraciones versionadas por Flyway.

Para el entorno **público de demo** se acepta una excepción deliberada, documentada en el
[ADR-0013](0013-perfil-demo-schema-por-servicio.md): un único PostgreSQL con **un schema por
servicio**, que preserva el aislamiento lógico sin el costo de varias instancias.

## Consecuencias

**Beneficios**

- Aislamiento real de datos: ningún servicio puede leer ni escribir el esquema de otro.
- Autonomía de despliegue: cada servicio evoluciona y migra su base sin coordinar con los demás.
- Fidelidad al estilo de microservicios que el proyecto busca demostrar.

**Trade-offs**

- Más infraestructura que un esquema compartido: varias instancias de PostgreSQL en local y en
  AWS (RDS).
- No hay transacciones ni joins entre servicios; la consistencia se resuelve con eventos, outbox y
  saga (ver [ADR-0005](0005-outbox-pattern.md) y [ADR-0006](0006-saga-orquestada.md)).

El costo de infraestructura en la demo pública se **mitiga** con el perfil `demo` (schema-per-
service), sin renunciar al modelo estricto donde importa demostrarlo (local/AWS).
