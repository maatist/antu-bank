# Documentación de API — Antu Bank

> OpenAPI/Swagger navegable + colección de pruebas (Bruno) para probar la API de extremo a
> extremo. Cubre la tarea 13.3 y el Requisito 13, criterio 3: _"EL SISTEMA DEBERÁ publicar
> documentación de API interactiva (OpenAPI/Swagger) y una colección de pruebas (Postman/Bruno)."_

## Contenido

- [1. OpenAPI / Swagger navegable](#1-openapi--swagger-navegable)
- [2. Playground GraphQL (GraphiQL)](#2-playground-graphql-graphiql)
- [3. Colección de pruebas (Bruno)](#3-colección-de-pruebas-bruno)
- [4. Autenticación en las pruebas](#4-autenticación-en-las-pruebas)
- [5. Flujo end-to-end sugerido](#5-flujo-end-to-end-sugerido)
- [6. Referencia rápida de endpoints](#6-referencia-rápida-de-endpoints)

---

## 1. OpenAPI / Swagger navegable

Cada microservicio REST expone su contrato con **springdoc** (OpenAPI 3): una **Swagger UI**
navegable y el documento **OpenAPI JSON** en `/v3/api-docs`. Estas rutas son públicas
(`permitAll`), así que se abren sin token.

### En local (cada servicio en su puerto)

| Servicio | Swagger UI | OpenAPI JSON |
|---|---|---|
| account-service | <http://localhost:8082/swagger-ui.html> | <http://localhost:8082/v3/api-docs> |
| ledger-service | <http://localhost:8083/swagger-ui.html> | <http://localhost:8083/v3/api-docs> |
| transfer-service | <http://localhost:8084/swagger-ui.html> | <http://localhost:8084/v3/api-docs> |

Levanta cada servicio con `./gradlew :services:<servicio>:bootRun` (ver
[README §6.3](../../README.md#63-arrancar-los-microservicios)).

> El `api-gateway` corre sobre WebFlux (Spring Cloud Gateway) y **no** incorpora springdoc, por
> lo que no sirve Swagger por sí mismo en local.

### En la demo pública (a través del gateway)

En el perfil `demo`, el gateway es el único servicio público. `SwaggerProxyRouteConfig`
(activo solo con `@Profile("demo")`) **reenvía** las rutas de Swagger del `account-service` a
través del gateway, de modo que la Swagger UI es navegable en:

- `https://<gateway>/swagger-ui.html`
- `https://<gateway>/v3/api-docs`

Los detalles del proxy de Swagger en demo están en
[`infra/README-demo.md`](../../infra/README-demo.md).

## 2. Playground GraphQL (GraphiQL)

El BFF GraphQL vive en el gateway:

- Endpoint GraphQL: `POST /graphql` (ej. <http://localhost:8080/graphql>)
- Playground interactivo (GraphiQL): <http://localhost:8080/graphiql>

La UI de GraphiQL carga sin token, pero **ejecutar** la query `me` exige
`Authorization: Bearer` (expone datos del usuario). Ese token se propaga a los servicios
internos que agrega el BFF.

## 3. Colección de pruebas (Bruno)

La colección está en [`collection/`](collection) en formato **[Bruno](https://www.usebruno.com/)**
(archivos `.bru` en texto plano, versionables en git y fáciles de revisar en PRs). Se eligió
Bruno sobre Postman precisamente por ser _plain-text_ y no depender de la nube.

### Abrirla

1. Instala Bruno (app de escritorio o `npm i -g @usebruno/cli`).
2. **Open Collection** → selecciona la carpeta `docs/api/collection`.
3. Elige el entorno arriba a la derecha: **local** o **demo**.

### Estructura

```
collection/
├── bruno.json                 # metadatos de la colección
├── environments/
│   ├── local.bru              # localhost: servicios directos + gateway + Keycloak (8081)
│   └── demo.bru               # gateway público HTTPS (ajusta las URLs a tu despliegue)
├── 01-Auth/                   # obtener token de usuario (CUSTOMER / ADMIN)
├── 02-Account/                # crear cuenta, consultar por id, listar por RUT
├── 03-Ledger/                 # registrar asiento balanceado, consultar saldo
├── 04-Transfer/               # crear transferencia, repetir (idempotencia), historial
├── 05-GraphQL BFF/            # query me { accounts, balances, transfers }
└── 06-OpenAPI/                # descargar el OpenAPI JSON de cada servicio
```

### Variables de entorno

| Variable | local | demo |
|---|---|---|
| `keycloakUrl` | `http://localhost:8081` | `https://<keycloak>` |
| `realm` | `antu-bank` | `antu-bank` |
| `authClientId` | `antu-bank-postman` | `antu-bank-postman` |
| `gatewayUrl` | `http://localhost:8080` | `https://<gateway>` |
| `accountUrl` / `ledgerUrl` / `transferUrl` | puertos directos `8082/8083/8084` | `https://<gateway>/api` |
| `customerUser` / `customerPass` | `cliente.demo` / `demo1234` | idem |
| `adminUser` / `adminPass` | `admin.demo` / `demo1234` | idem |

Las variables encadenadas (`token`, `adminToken`, `accountId`, `destinationAccountId`,
`transactionId`, `idempotencyKey`) las rellenan automáticamente los _scripts_ de cada petición.

> **Base URLs local vs demo.** En **local** las peticiones van directo a cada servicio en su
> puerto (`accountUrl=http://localhost:8082`, etc.). En **demo** todo el tráfico de negocio
> entra por el gateway bajo `/api/**`, por lo que `accountUrl=https://<gateway>/api` y la ruta
> `/accounts` resuelve a `https://<gateway>/api/accounts`. Ajusta los dominios de
> `environments/demo.bru` a tu despliegue real (Render/Fly/Vercel).
>
> **Nota sobre `06-OpenAPI` en demo.** El proxy de Swagger del gateway expone el OpenAPI JSON en
> la raíz (`/v3/api-docs`), no bajo `/api`, así que esas peticiones están pensadas para el
> entorno **local**. En demo usa directamente `https://<gateway>/v3/api-docs`.

> ⚠️ **Credenciales demo.** Los archivos de entorno contienen credenciales **demo**
> (`demo1234`), aceptables por el Requisito 13, criterio 4 (credenciales demo visibles). No son
> secretos reales: no reutilizar en ningún entorno con información sensible.

## 4. Autenticación en las pruebas

Los endpoints de negocio exigen un **JWT de usuario** con el rol adecuado:

| Operación | Rol requerido | Token de la colección |
|---|---|---|
| `POST /accounts` (crear cuenta) | `CUSTOMER` | `{{token}}` (cliente.demo) |
| `POST /transfers` (transferir) | `CUSTOMER` | `{{token}}` (cliente.demo) |
| `POST /transactions` (asiento contable) | `ADMIN` | `{{adminToken}}` (admin.demo) |
| Consultas `GET ...` y query `me` | autenticado | `{{token}}` |

Como los clients de aplicación tienen el _password grant_ deshabilitado por diseño (el frontend
usa Authorization Code + PKCE), el realm incluye un client público **`antu-bank-postman`** con
Direct Access Grant habilitado **solo para pruebas**. Las peticiones de `01-Auth` lo usan para
canjear usuario+contraseña por un token de usuario (con sus roles) y guardarlo en `{{token}}` /
`{{adminToken}}`. Ver detalle en [`infra/keycloak/README.md`](../../infra/keycloak/README.md)
(sección "Obtener un token para pruebas", Opción C).

## 5. Flujo end-to-end sugerido

Con la infraestructura y los servicios arriba (o contra la demo pública), ejecuta en orden:

1. **01-Auth › Token CUSTOMER** — guarda `{{token}}`.
2. **01-Auth › Token ADMIN** — guarda `{{adminToken}}` (para el ledger).
3. **02-Account › Crear cuenta** — guarda `{{accountId}}` (cuenta origen).
4. **02-Account › Crear cuenta destino** — guarda `{{destinationAccountId}}`.
5. **02-Account › Consultar cuenta por id** / **Listar cuentas por RUT**.
6. **03-Ledger › Registrar transacción balanceada** — fondea la cuenta origen (Σ = 0).
7. **03-Ledger › Consultar saldo de cuenta** — verifica el saldo derivado.
8. **04-Transfer › Crear transferencia** — 201; genera una `Idempotency-Key`.
9. **04-Transfer › Repetir transferencia** — 200, mismo resultado sin duplicar (idempotencia).
10. **04-Transfer › Listar historial**.
11. **05-GraphQL BFF › me** — agrega cuentas + saldos + historial en una sola respuesta.

## 6. Referencia rápida de endpoints

| Método | Ruta (nativa del servicio) | Vía gateway | Servicio | Auth |
|---|---|---|---|---|
| `POST` | `/accounts` | `/api/accounts` | account | `CUSTOMER` |
| `GET` | `/accounts/{id}` | `/api/accounts/{id}` | account | autenticado |
| `GET` | `/accounts?rut=` | `/api/accounts?rut=` | account | autenticado |
| `POST` | `/transactions` | `/api/transactions` | ledger | `ADMIN` |
| `GET` | `/transactions/balances/{accountId}?currency=` | `/api/transactions/balances/{accountId}` | ledger | autenticado |
| `POST` | `/transfers` (header `Idempotency-Key`) | `/api/transfers` | transfer | `CUSTOMER` |
| `GET` | `/transfers?accountId=` | `/api/transfers?accountId=` | transfer | autenticado |
| `POST` | `/graphql` (`me`) | `/graphql` | gateway (BFF) | autenticado |

Datos chilenos de referencia (usuarios/seed demo): RUT `12.345.678-5` (Javiera González,
`cliente.demo`), RUT `16.789.012-1` (Sebastián Muñoz, `admin.demo`). Bancos: enum `ChileanBank`
(`BANCO_ESTADO`, `BANCO_DE_CHILE`, `BCI`, `SANTANDER_CHILE`, ...). Tipos de cuenta: `CORRIENTE`,
`VISTA`, `AHORRO`. Monedas: `CLP` (sin decimales), `USD`, `UF`.
