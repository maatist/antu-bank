# Guía de despliegue público 24/7 (tarea 12a.3)

Esta guía explica, paso a paso, cómo desplegar Antu Bank con **URL pública HTTPS accesible 24/7**
(Requisito 12, criterio 1):

- **Backend** en un PaaS de contenedores gratuito/barato: **Render** (Blueprint `infra/render.yaml`)
  o **Fly.io** (`infra/fly/*.fly.toml`).
- **Frontend** Next.js en **Vercel** (`frontend/vercel.json`).

Todo corre con el **perfil `demo`** reducido (tarea 12a.2): un solo PostgreSQL con schema por
servicio, Kafka gestionado gratuito (Upstash/Redpanda) y Keycloak. Ver `infra/README-demo.md` para
el detalle de la topología reducida.

> **Manejo de secretos (transversal):** ningún secreto vive en el repositorio. Los manifiestos
> versionados solo contienen config no sensible y **referencian** los secretos por nombre. Los
> valores reales se cargan en el panel/CLI del proveedor (env group de Render, `fly secrets set`,
> Environment Variables de Vercel).

---

## 0. Requisitos previos

- Cuenta en el proveedor de backend elegido (Render **o** Fly.io) y en Vercel.
- Un **Kafka gestionado gratuito** (Upstash o Redpanda Serverless). Anotar del panel del proveedor:
  - `KAFKA_BOOTSTRAP_SERVERS` (ej. `xxx.upstash.io:9092`)
  - usuario y clave SASL para armar `KAFKA_SASL_JAAS_CONFIG`
- Repositorio conectado al proveedor (Render/Fly leen los Dockerfile; el contexto de build es la
  **raíz del monorepo**).

### Variables de entorno del backend (todas por entorno, nunca hardcodeadas)

| Variable | Descripción | Dónde se define |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Debe ser `demo` | manifiesto (no secreto) |
| `DEMO_DB_URL` / `DEMO_DB_USER` / `DEMO_DB_PASSWORD` | Datasource del PostgreSQL gestionado | Render: cableado desde la base; Fly: `fly secrets set` |
| `KEYCLOAK_ISSUER_URI` | URL HTTPS pública del realm `antu-bank` | secreto/env |
| `KAFKA_BOOTSTRAP_SERVERS` | Endpoint del Kafka gestionado | secreto |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | manifiesto |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-256` | manifiesto |
| `KAFKA_SASL_JAAS_CONFIG` | `ScramLoginModule ... username/password` | secreto |
| `LEDGER_BASE_URL` | URL interna del ledger (solo transfer) | manifiesto (hostname interno) |
| `ACCOUNT_SERVICE_URI` / `LEDGER_SERVICE_URI` / `TRANSFER_SERVICE_URI` | URLs internas (solo gateway) | manifiesto (hostname interno) |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | Clave admin de Keycloak | secreto |

---

## Opción A — Backend en Render (Blueprint)

Render lee `infra/render.yaml` como **Blueprint** y crea de una vez: el PostgreSQL gestionado, el
env group de secretos, Keycloak y los 6 microservicios. Solo `api-gateway` y `keycloak` son
públicos; el resto son *Private Services* alcanzables por su hostname interno.

1. **Crear el Blueprint.** En Render: *New → Blueprint*, conectar el repositorio y seleccionar
   `infra/render.yaml`. Render mostrará el plan de recursos a crear.

2. **Completar los secretos** del env group `antu-bank-demo-secrets` (las claves con `sync:false`):
   - `KAFKA_BOOTSTRAP_SERVERS`
   - `KAFKA_SASL_JAAS_CONFIG` (una línea:
     `org.apache.kafka.common.security.scram.ScramLoginModule required username="USUARIO" password="CLAVE";`)
   - `KEYCLOAK_ISSUER_URI` — se completa **después** de conocer la URL pública de Keycloak (paso 4).
   - `KC_BOOTSTRAP_ADMIN_PASSWORD` (servicio `antu-bank-keycloak`).

   El datasource (`DEMO_DB_*`) NO se escribe a mano: el Blueprint lo **cablea automáticamente**
   desde la base `antu-bank-demo-postgres` (`fromDatabase`).

3. **Primer deploy.** Render construye las imágenes con los Dockerfile (contexto en la raíz) y
   levanta los servicios. Los servicios privados quedan disponibles por su hostname interno; el
   Blueprint cablea `ACCOUNT_SERVICE_URI`/`LEDGER_SERVICE_URI`/`TRANSFER_SERVICE_URI` y
   `LEDGER_BASE_URL` con `fromService`.

4. **Keycloak + issuer.** Una vez que `antu-bank-keycloak` tenga su URL pública
   (`https://antu-bank-keycloak.onrender.com`), fijar
   `KEYCLOAK_ISSUER_URI = https://antu-bank-keycloak.onrender.com/realms/antu-bank` en el env group
   y redeploy de los servicios que validan JWT y del gateway.
   - **Import del realm:** la imagen oficial de Keycloak necesita el `realm-export.json` de
     `infra/keycloak`. Recomendado: construir una imagen propia mínima que copie ese archivo a
     `/opt/keycloak/data/import` y arranque con `--import-realm` (misma idea que el compose demo).
     Alternativa: un *Disk* de Render con el realm montado en esa ruta.

5. **Verificar 24/7 + HTTPS.** El gateway responde en `https://antu-bank-gateway.onrender.com`.
   Comprobar `GET /actuator/health` (usado como `healthCheckPath`).

> **Nota sobre el plan free de Render:** los servicios free pueden dormir por inactividad. Para
> *always-on* estricto (Requisito 12, criterio 1), subir al menos el `api-gateway` y `keycloak`
> a un plan que no duerma, o usar un *cron*/pinger que los mantenga despiertos.

---

## Opción B — Backend en Fly.io (una app por servicio)

Fly despliega **una app por servicio**, todas en una **red privada** (6PN) donde se resuelven por
`<app>.internal`. Solo `api-gateway` y `keycloak` definen servicio HTTP público (HTTPS por Fly).
Ver `infra/fly/README.md` para la tabla de puertos internos.

1. **Postgres gestionado.** Crear un Postgres de Fly y anotar la cadena de conexión:
   ```bash
   fly postgres create --name antu-bank-demo-postgres
   ```

2. **Crear las apps** (una por servicio). Para el gateway, transfer y keycloak ya hay `fly.toml`;
   el resto se clona del de transfer cambiando `app`, `dockerfile`, `internal_port` y variables:
   ```bash
   fly apps create antu-bank-gateway
   fly apps create antu-bank-transfer
   fly apps create antu-bank-keycloak
   # ... account, ledger, fraud, notification
   ```

3. **Cargar secretos por app** (nunca en el repo). Ejemplo para transfer-service:
   ```bash
   fly secrets set -a antu-bank-transfer \
     DEMO_DB_URL="jdbc:postgresql://.../neobank" \
     DEMO_DB_USER="neobank" DEMO_DB_PASSWORD="..." \
     KAFKA_BOOTSTRAP_SERVERS="xxx.upstash.io:9092" \
     KAFKA_SASL_JAAS_CONFIG='org.apache.kafka.common.security.scram.ScramLoginModule required username="USUARIO" password="CLAVE";' \
     KEYCLOAK_ISSUER_URI="https://antu-bank-keycloak.fly.dev/realms/antu-bank"

   fly secrets set -a antu-bank-keycloak KC_BOOTSTRAP_ADMIN_PASSWORD="..."
   ```

4. **Desplegar** cada app con su `fly.toml` y su Dockerfile (contexto = raíz del monorepo):
   ```bash
   fly deploy -c infra/fly/keycloak.fly.toml
   fly deploy -c infra/fly/transfer.fly.toml --dockerfile services/transfer-service/Dockerfile
   fly deploy -c infra/fly/gateway.fly.toml   --dockerfile services/api-gateway/Dockerfile
   # ... account, ledger, fraud, notification (análogos a transfer)
   ```
   > Ejecutar `fly deploy` desde la **raíz del monorepo** para que el Dockerfile encuentre
   > `settings.gradle.kts`, `build-logic`, `common-domain`, etc.

5. **Import del realm de Keycloak:** igual que en Render, construir una imagen propia que copie
   `infra/keycloak/realm-export.json` a `/opt/keycloak/data/import`, o montar un volumen de Fly.

6. **Verificar 24/7 + HTTPS.** Con `min_machines_running = 1` en gateway y keycloak, quedan
   *always-on*. El gateway responde en `https://antu-bank-gateway.fly.dev` (health en
   `/actuator/health`); los servicios internos NO son accesibles desde internet.

---

## Frontend en Vercel

1. **Importar el proyecto** en Vercel apuntando al directorio `frontend/` (framework Next.js
   autodetectado; `frontend/vercel.json` fija el build y cabeceras de seguridad, HTTPS por defecto).

2. **Cargar las Environment Variables** (entorno *Production*) según
   `frontend/.env.production.example`:
   - `AUTH_SECRET` (marcar como *Sensitive*), `AUTH_URL` = URL pública de Vercel.
   - `AUTH_KEYCLOAK_ISSUER` = **mismo** issuer HTTPS del Keycloak público del backend.
   - `AUTH_KEYCLOAK_ID` = `antu-bank-frontend`, `AUTH_KEYCLOAK_SECRET` vacío (client público).
   - `NEXT_PUBLIC_GATEWAY_URL` = URL pública HTTPS del `api-gateway`.
   - `DEMO_RUT`, `DEMO_USERNAME`, `DEMO_PASSWORD`.

3. **Deploy.** Vercel construye con `next build` y publica en HTTPS.

4. **Wiring de retorno (CORS + redirect URIs).** En el client `antu-bank-frontend` del realm
   Keycloak, registrar la URL de Vercel como *Valid Redirect URI* y *Web Origin*. En el gateway,
   permitir el origen de Vercel en CORS (revisión de seguridad, tarea 13.5).

---

## Checklist de verificación 24/7 + HTTPS

- [ ] `https://<gateway>/actuator/health` responde `UP`.
- [ ] `https://<gateway>/swagger-ui.html` (Swagger del account-service reenviado por el gateway) y
      `https://<gateway>/graphiql` (playground GraphQL) cargan sin token (tarea 12a.5). Ver detalle
      en `infra/README-demo.md`.
- [ ] `https://<keycloak>/realms/antu-bank/.well-known/openid-configuration` responde 200.
- [ ] El frontend en Vercel abre por HTTPS, permite login/guest y consulta el BFF.
- [ ] Gateway y Keycloak quedan *always-on* (plan sin sleep en Render / `min_machines_running=1` en Fly).
- [ ] Ningún secreto quedó en el repositorio (todos por env/secret del proveedor).
