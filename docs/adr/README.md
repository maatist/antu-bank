# Architecture Decision Records (ADRs) — Antu Bank

Este directorio consolida las **decisiones de arquitectura** del proyecto Antu Bank (NeoBank),
cada una registrada como un ADR independiente y versionado junto al código.

Toda la documentación del proyecto está en español (ver
[Requisito 13, criterio 6](../../.kiro/specs/neobank/requirements.md)).

## ¿Qué es un ADR?

Un **Architecture Decision Record** captura una decisión arquitectónica significativa junto con
su contexto y sus consecuencias. La idea es dejar por escrito *por qué* se tomó una decisión —no
solo *qué* se hizo—, de modo que quien llegue al proyecto (o el propio autor meses después)
entienda las fuerzas en juego y los trade-offs aceptados, sin tener que reconstruirlos leyendo el
código.

Los ADRs son **inmutables** en su intención: una decisión no se edita para cambiar su rumbo, se
**supersede** con un ADR nuevo que la reemplaza. Un ADR aceptado refleja el estado real del
sistema en el momento en que se escribió.

## Plantilla

Cada ADR de este repositorio sigue la misma estructura (variante en español de la plantilla clásica
de [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)):

- **Título** — `ADR-NNNN: <decisión en una línea>`.
- **Estado** — `Propuesto`, `Aceptado`, `Supersedido por ADR-XXXX` o `Deprecado`.
- **Contexto** — las fuerzas técnicas y de negocio en juego; el problema a resolver.
- **Decisión** — la postura adoptada, en voz activa.
- **Consecuencias** — lo que resulta de la decisión: beneficios, costos y **trade-offs** aceptados.

## Índice de ADRs

| # | Decisión | Estado |
|---|---|---|
| [ADR-0001](0001-gradle-kotlin-dsl-sobre-maven.md) | Gradle (Kotlin DSL) sobre Maven | Aceptado |
| [ADR-0002](0002-money-bigdecimal-scale-por-moneda.md) | `Money` con `BigDecimal` y scale por moneda (nunca `double`) | Aceptado |
| [ADR-0003](0003-rut-value-object-modulo-11.md) | `Rut` como value object con validación módulo 11 | Aceptado |
| [ADR-0004](0004-database-per-service.md) | Database-per-service estricto (local/AWS) | Aceptado |
| [ADR-0005](0005-outbox-pattern.md) | Outbox pattern para consistencia Kafka + base de datos | Aceptado |
| [ADR-0006](0006-saga-orquestada.md) | Saga orquestada para transferencias | Aceptado |
| [ADR-0007](0007-keycloak-como-idp.md) | Keycloak como IdP en lugar de Authorization Server propio | Aceptado |
| [ADR-0008](0008-graphql-bff-en-el-gateway.md) | GraphQL como BFF en el gateway | Aceptado |
| [ADR-0009](0009-despliegue-doble-track.md) | Despliegue doble track (público always-on + AWS on-demand) | Aceptado |
| [ADR-0010](0010-i18n-es-en.md) | i18n es/en con español por defecto; documentación en español | Aceptado |
| [ADR-0011](0011-stack-de-observabilidad-dual.md) | Stack de observabilidad dual (métricas + trazas + logs) | Aceptado |
| [ADR-0012](0012-oidc-authorization-code-pkce-cliente-publico.md) | OIDC Authorization Code + PKCE con cliente público para el frontend | Aceptado |
| [ADR-0013](0013-perfil-demo-schema-por-servicio.md) | Perfil `demo` reducido con schema-por-servicio | Aceptado |

Los ADR-0001 a ADR-0010 consolidan las decisiones originalmente esbozadas en la
[sección 12 del documento de diseño](../../.kiro/specs/neobank/design.md#12-decisiones-de-arquitectura-adrs),
expandiendo su contexto y consecuencias. Los ADR-0011 a ADR-0013 registran decisiones
adicionales significativas ya reflejadas en el código y la infraestructura.
