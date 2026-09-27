# Manifiestos Fly.io — backend público 24/7 (tarea 12a.3, Requisito 12, criterio 1)

Alternativa a Render para desplegar el backend en un PaaS de contenedores gratuito/barato,
accesible 24/7 vía HTTPS. Fly.io despliega **una app por servicio** (cada una con su `fly.toml`)
dentro de una **red privada** (6PN) donde los servicios se alcanzan por `.internal`.

Este directorio incluye los `fly.toml` de:

- `gateway.fly.toml` — **api-gateway**, única app **pública** (HTTPS terminado por Fly en el borde).
- `transfer.fly.toml` — **transfer-service**, servicio interno representativo (Postgres schema
  `transfer` + Kafka gestionado + llamada al ledger por `.internal`). El resto de servicios
  internos (account/ledger/fraud/notification) se despliegan igual, cambiando `app`,
  `dockerfile`, `internal_port` y las variables propias (ver la tabla más abajo).
- `keycloak.fly.toml` — **Keycloak** (IdP) importando el realm versionado `antu-bank`.

> El detalle paso a paso (crear apps, Postgres gestionado, Kafka gestionado, secrets y wiring)
> está en `infra/README-deploy.md`.

## Puertos internos por servicio (coinciden con los Dockerfile / compose demo)

| App                 | Dockerfile                               | `internal_port` | Público |
|---------------------|------------------------------------------|-----------------|---------|
| antu-bank-gateway   | services/api-gateway/Dockerfile          | 8080            | **Sí**  |
| antu-bank-account   | services/account-service/Dockerfile      | 8082            | No      |
| antu-bank-ledger    | services/ledger-service/Dockerfile       | 8083            | No      |
| antu-bank-transfer  | services/transfer-service/Dockerfile     | 8084            | No      |
| antu-bank-fraud     | services/fraud-service/Dockerfile        | 8085            | No      |
| antu-bank-notification | services/notification-service/Dockerfile | 8086         | No      |

## Red interna (.internal)

Dentro de la organización de Fly, cada app resuelve por `<app>.internal`. Así, el gateway apunta
a `http://antu-bank-account.internal:8082`, etc., y transfer al ledger por
`http://antu-bank-ledger.internal:8083`. Solo el gateway define `[http_service]` público; los
servicios internos NO exponen puerto al exterior.

## Secretos (NUNCA en el repo)

Se cargan con `fly secrets set` por app (ver guía). Los `fly.toml` versionados solo contienen
config no sensible en `[env]` y **referencian** los secretos por nombre.
