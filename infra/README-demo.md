# Perfil `demo` — stack reducido público (tarea 12a.2)

Este documento describe el **perfil `demo`**: la configuración reducida con la que Antu Bank corre
en el despliegue público 24/7 (Requisito 12, criterios 2 y 7). El objetivo es mantener la app
**100% testeable** gastando la mínima infraestructura posible en un PaaS gratuito/barato
(Render/Fly.io), sin perder ninguna funcionalidad demoable.

> Local y AWS **no** usan este perfil: conservan el stack fiel con *database-per-service* estricto
> y todos los contenedores (Requisito 12, criterio 7). Ver `infra/docker-compose.yml` para local.

> **Despliegue público (tarea 12a.3):** para llevar este stack reducido a una URL pública HTTPS
> 24/7 (backend en Render/Fly.io, frontend en Vercel), ver la guía paso a paso en
> [`infra/README-deploy.md`](./README-deploy.md). Manifiestos: `infra/render.yaml` (Render),
> `infra/fly/*.fly.toml` (Fly.io) y `frontend/vercel.json` (Vercel).

## ¿Qué se reduce respecto del stack local?

| Recurso | Local (`local`) | Demo reducido (`demo`) |
|---|---|---|
| PostgreSQL | Uno por servicio (database-per-service) | **Uno solo**, un **schema** por servicio |
| Kafka | Contenedor propio (KRaft) | **Gestionado gratuito** (Upstash/Redpanda) vía SASL_SSL |
| Observabilidad | Prometheus + Grafana + Loki + Jaeger | Fuera de alcance (no se despliega) |
| Keycloak | Contenedor | Contenedor (mismo realm `antu-bank`) |
| Microservicios | 6 (en host) | 6 (en contenedores) |

## Aislamiento por schema (un solo PostgreSQL)

En vez de una base por servicio, la demo usa **una base (`neobank`)** con un **schema por servicio
con estado**:

- `account` → account-service
- `ledger` → ledger-service
- `transfer` → transfer-service

(`fraud`, `notification` y `api-gateway` no tienen base de datos.)

Cómo se logra el aislamiento sin colisiones de migraciones:

1. Cada servicio se conecta con `?currentSchema=<schema>` en la URL JDBC
   (ver `application-demo.yml` de cada servicio).
2. Flyway se configura con `schemas`, `default-schema` y `create-schemas: true`, de modo que cada
   servicio ejecuta sus migraciones y registra su propio `flyway_schema_history` **dentro de su
   schema**. Como las migraciones usan nombres de tabla sin calificar, quedan confinadas al schema
   activo y no chocan entre servicios.
3. `infra/postgres/demo-init.sql` crea los tres schemas al inicializar el volumen del PostgreSQL
   (además, Flyway los crearía por `create-schemas`).

## Kafka gestionado gratuito (Upstash/Redpanda)

En vez de un broker propio, la demo apunta a un Kafka **gestionado gratuito**. Los servicios que
usan Kafka (`transfer`, `ledger`, `fraud`, `notification`) se conectan por **SASL_SSL** con
credenciales **inyectadas por variables de entorno** — nunca hardcodeadas ni versionadas.

Variables relevantes (todas por entorno):

- `KAFKA_BOOTSTRAP_SERVERS` — endpoint del proveedor.
- `KAFKA_SECURITY_PROTOCOL` — por defecto `SASL_SSL`.
- `KAFKA_SASL_MECHANISM` — por defecto `SCRAM-SHA-256` (Upstash/Redpanda).
- `KAFKA_SASL_JAAS_CONFIG` — `ScramLoginModule` con usuario/clave del proveedor.

## Swagger y playground GraphQL públicos (tarea 12a.5)

En la demo el **api-gateway** es el único servicio con puerto público; los microservicios REST son
privados. Para que la documentación de API sea navegable sin fricción (Requisito 12, criterio 4),
el gateway expone de forma **pública** (sin token) las UIs de documentación, manteniendo protegidos
los endpoints de negocio.

| Recurso | URL pública (a través del gateway) | Sirve |
|---|---|---|
| **Playground GraphQL (GraphiQL)** | `https://<gateway>/graphiql` | El propio gateway (Spring for GraphQL). |
| **Endpoint GraphQL** | `https://<gateway>/graphql` | El propio gateway. **Requiere Bearer** para ejecutar `me`. |
| **Swagger UI** | `https://<gateway>/swagger-ui.html` | Reenviado al `account-service` (contrato REST de referencia). |
| **OpenAPI JSON** | `https://<gateway>/v3/api-docs` | Reenviado al `account-service`. |

Cómo funciona:

- **GraphiQL** ya es público en todos los perfiles: el gateway lo sirve y su `SecurityConfig`
  permite `/graphiql/**` sin token. Las **queries** de `me` sí exigen `Authorization: Bearer`
  (exponen datos del usuario), y ese token se propaga a los servicios internos que agrega el BFF.
- **Swagger** no lo sirve el gateway (corre sobre WebFlux, sin springdoc). Bajo el perfil `demo`,
  `SwaggerProxyRouteConfig` añade rutas que **reenvían** `/swagger-ui.html`, `/swagger-ui/**`,
  `/v3/api-docs` y `/v3/api-docs/**` al `account-service`. Esas rutas solo existen en `demo`
  (en local cada servicio expone su Swagger directo en su puerto).

Qué **no** se debilita: solo se exponen los paths de documentación, que en el account-service son
`permitAll`. Los endpoints de negocio (`/api/**` en el gateway, `/accounts/**` en el servicio)
siguen exigiendo un JWT válido; sin token responden `401`.

## Cómo levantar el stack reducido

1. Copiar la plantilla de variables y completarla con credenciales reales:

   ```bash
   cp infra/.env.demo.example infra/.env.demo
   # editar infra/.env.demo (contraseña de Postgres, admin de Keycloak, broker y credenciales SASL)
   ```

2. Construir y levantar (el contexto de build de los Dockerfile es la raíz del monorepo):

   ```bash
   docker compose --env-file infra/.env.demo -f infra/docker-compose.demo.yml up -d --build
   ```

3. Puntos de entrada:
   - **api-gateway** en `http://localhost:8080` (único puerto publicado hacia afuera).
   - **Keycloak** en `http://localhost:8081` (realm `antu-bank`).

4. Derribar:

   ```bash
   docker compose --env-file infra/.env.demo -f infra/docker-compose.demo.yml down
   # añadir -v para borrar también el volumen de datos de PostgreSQL
   ```

## Manejo de secretos

- Ningún secreto vive en el repositorio. `infra/.env.demo.example` es solo una **plantilla** con
  valores de ejemplo (`cambia-esta-clave`, `TU_USUARIO`/`TU_CLAVE`).
- El archivo real `infra/.env.demo` queda ignorado por `.gitignore`/`.dockerignore` (patrón
  `**/.env.*`, con excepción de los `.example`).
- En el PaaS, estas variables se cargan como **secrets/env vars** del servicio, no como archivo.
- El `docker-compose.demo.yml` marca como obligatorias (`:?`) las variables sin default seguro
  (contraseñas y credenciales de Kafka), de modo que el arranque falla explícito si faltan.
