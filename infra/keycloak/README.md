# Keycloak en Antu Bank — Guía para principiantes

Esta guía está pensada para alguien que **nunca ha usado Keycloak ni OIDC**. Primero
explica los conceptos, luego los aterriza en la configuración real de Antu Bank
(el archivo [`realm-export.json`](./realm-export.json) de esta misma carpeta) y termina
con una guía paso a paso para levantar todo en local y probar el acceso a la API.

> Todo el stack local vive en [`infra/docker-compose.yml`](../docker-compose.yml).
> El realm se **importa automáticamente** al arrancar Keycloak (flag `--import-realm`),
> así que no hay que configurar nada a mano en la consola.

---

## 1. Conceptos (para quien parte de cero)

### ¿Qué es un Identity Provider (IdP)?

Un **Identity Provider** es un servicio que se encarga de una sola cosa: **saber quién
eres**. En lugar de que cada aplicación guarde usuarios y contraseñas, todas delegan el
login en el IdP. El IdP autentica a la persona y luego les entrega a las aplicaciones una
"prueba" firmada de que el login ocurrió. En Antu Bank ese IdP es **Keycloak**.

### OAuth2 vs OIDC

Son dos capas complementarias:

- **OAuth2** es un protocolo de **autorización**: cómo una aplicación obtiene permiso para
  acceder a recursos (una API) en nombre de un usuario, sin manejar su contraseña. El
  resultado es un **access token**.
- **OIDC (OpenID Connect)** es una capa de **autenticación** construida **encima** de
  OAuth2. Añade la identidad: además del access token, entrega un **ID token** que dice
  quién es el usuario, y define endpoints estándar de descubrimiento (`/.well-known/...`).

Regla mnemotécnica: OAuth2 responde *"¿qué puede hacer?"*, OIDC responde *"¿quién es?"*.

### Realm

Un **realm** es un espacio aislado dentro de Keycloak: su propio conjunto de usuarios,
roles, clients y claves de firma. Un realm no ve a otro. Antu Bank usa un único realm
llamado **`antu-bank`**.

### Clients: público vs confidential

Un **client** es una aplicación registrada en el realm que puede pedir tokens. Hay dos
tipos:

- **Público (public client):** corre en un entorno donde **no puede guardar un secreto**
  (un navegador, una SPA). No tiene contraseña propia; su seguridad se apoya en **PKCE**
  (ver abajo) y en una lista blanca de URLs de redirección.
- **Confidential:** corre en un servidor donde **sí puede guardar un secreto** (un
  `client secret`). Puede autenticarse por sí mismo ante Keycloak, por ejemplo para pedir
  tokens de servicio-a-servicio.

### Authorization Code + PKCE

Es el flujo de login recomendado para aplicaciones con frontend:

1. La app redirige al usuario a la página de login de Keycloak.
2. El usuario se autentica en Keycloak (nunca en la app).
3. Keycloak devuelve un **código** de un solo uso a la app.
4. La app **canjea** ese código por tokens.

**PKCE** (Proof Key for Code Exchange, método `S256`) protege ese canje: la app genera un
secreto aleatorio (`code_verifier`), envía su hash al inicio y el original al canjear. Así,
aunque alguien intercepte el código, no puede usarlo sin el verifier. Es imprescindible en
clients públicos, que no tienen secreto propio.

### Roles

Un **rol** es una etiqueta de permiso que se asigna a un usuario (o a un client). Los
**realm roles** aplican a todo el realm. Antu Bank define dos: **`CUSTOMER`** (cliente del
banco) y **`ADMIN`** (administrador). Los roles viajan dentro del token y el backend los usa
para decidir qué puede hacer cada quien.

### JWT y su validación por JWKS

Los tokens de Keycloak son **JWT** (JSON Web Token): un texto en tres partes
(`header.payload.signature`) codificadas en Base64URL. El *payload* lleva "claims" (datos)
como el usuario, la expiración y los roles. La *signature* está firmada con la **clave
privada** del realm.

Para verificar que un token es auténtico, un servicio **no** necesita llamar a Keycloak en
cada request. Keycloak publica su **clave pública** en un endpoint estándar llamado
**JWKS** (JSON Web Key Set):

```
http://localhost:8081/realms/antu-bank/protocol/openid-connect/certs
```

El servicio descarga esas claves una vez, las **cachea** y valida la firma **localmente**.
Si la firma cuadra y el token no expiró ni fue emitido por otro emisor, se acepta.

### Access token vs refresh token

- **Access token:** el JWT de vida corta (en Antu Bank, **300 s = 5 min**, ver
  `accessTokenLifespan`) que se envía en cada llamada a la API. Cuando expira, deja de
  servir.
- **Refresh token:** de vida más larga; se usa para pedir un nuevo access token sin
  obligar al usuario a volver a loguearse. Lo maneja el frontend, no se manda a las APIs.

---

## 2. Cómo se usa en Antu Bank

Todo lo de abajo está definido en [`realm-export.json`](./realm-export.json).

### Realm

- **Nombre:** `antu-bank`
- **Idiomas:** internacionalización activa, `es` (por defecto) y `en`.
- **Emisor (issuer):** `http://localhost:8081/realms/antu-bank`
- Vida del access token: 300 s.

### Clients

| clientId | Tipo | Flujo(s) | Para qué sirve |
|---|---|---|---|
| `antu-bank-frontend` | **Público** | Authorization Code + PKCE (`S256`) | Login del frontend Next.js. `redirectUris` a `http://localhost:3000/*`. |
| `antu-bank-postman` | **Público** | Direct Access Grant (password) | **Solo pruebas** de la API desde Postman/Bruno (ver [`docs/api`](../../docs/api/README.md)). Emite tokens de usuario con roles. No usar en producción. |
| `api-gateway` | **Confidential** | `client_credentials` (service account) | El gateway: resource server y llamadas de servicio a servicio. |
| `account-service` | **Confidential** | `client_credentials` (service account) | Resource server; llamadas internas. |
| `ledger-service` | **Confidential** | `client_credentials` (service account) | Resource server; llamadas internas. |
| `transfer-service` | **Confidential** | `client_credentials` (service account) | Resource server; llamadas internas al ledger. |

> **Importante — flujos deshabilitados a propósito:** salvo el client de pruebas
> `antu-bank-postman`, en **todos** los clients está `directAccessGrantsEnabled: false`. Es
> decir, **no** se puede obtener un token pasando usuario y contraseña directamente (password
> grant). El frontend obtiene tokens por Authorization Code + PKCE, y los servicios entre sí
> por `client_credentials` (`serviceAccountsEnabled: true` en los confidential). Esto es
> intencional: el password grant está desaconsejado por seguridad.
>
> La **única excepción** es `antu-bank-postman`, un client público añadido **exclusivamente
> para la colección de pruebas** (`docs/api`): habilita el Direct Access Grant para que
> Postman/Bruno obtengan un token de usuario (`cliente.demo`/`admin.demo`) con sus roles
> `CUSTOMER`/`ADMIN` sin implementar el flujo PKCE en la herramienta. Es un facilitador de
> demo; en un entorno real este client no existiría.

Los `client secret` de los confidential son de **demo** (por ejemplo,
`account-service-demo-secret`) y solo válidos en local. No son secretos reales de
producción.

### Roles

- `CUSTOMER` — cliente del banco.
- `ADMIN` — administrador (en la demo, `admin.demo` tiene `ADMIN` **y** `CUSTOMER`).

Los servicios extraen los roles del claim `realm_access.roles` del JWT y los mapean a
authorities de Spring con prefijo `ROLE_` (ej. `ROLE_CUSTOMER`). Esto ocurre en el
`SecurityConfig` de cada servicio (por ejemplo,
`services/account-service/.../config/SecurityConfig.java`).

### Cómo validan el JWT los servicios (resource servers)

Cada servicio backend es un **OAuth2 Resource Server**. En su `application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/antu-bank}
```

- Por **OIDC discovery**, Spring descubre el `jwk-set-uri` (el endpoint JWKS) a partir del
  `issuer-uri`, descarga las claves públicas y las cachea.
- La firma del JWT se valida **localmente** en cada request (sin llamar a Keycloak).
- El emisor es **configurable por entorno** con la variable **`KEYCLOAK_ISSUER_URI`**; por
  defecto apunta al Keycloak local en el puerto **8081**.

---

## 3. Guía de setup local (paso a paso)

### 3.1 Levantar Keycloak

Desde la raíz del repositorio:

```bash
# Solo Keycloak
docker compose -f infra/docker-compose.yml up -d keycloak

# O el stack completo (bases de datos, Kafka y Keycloak)
docker compose -f infra/docker-compose.yml up -d
```

La imagen es `quay.io/keycloak/keycloak:26.0`, arranca con `start-dev --import-realm` y
monta esta carpeta (`./keycloak`) como directorio de importación, así que el realm
`antu-bank` queda **importado automáticamente**.

### 3.2 Entrar a la consola de administración

- URL: **http://localhost:8081**
- Usuario admin (bootstrap): **`admin`** / **`admin`**
  (definidos en el compose como `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`).

En la esquina superior izquierda, cambia el realm de `master` a **`antu-bank`** para ver
sus clients, roles y usuarios.

### 3.3 Verificar que el realm está arriba

El endpoint de descubrimiento OIDC debe responder `200`:

```bash
curl http://localhost:8081/realms/antu-bank/.well-known/openid-configuration
```

### 3.4 Usuarios demo

Ya vienen creados en el realm importado. Contraseña común: **`demo1234`**.

| Usuario | Contraseña | Roles | RUT (atributo) |
|---|---|---|---|
| `cliente.demo` | `demo1234` | `CUSTOMER` | 12.345.678-5 |
| `admin.demo` | `demo1234` | `ADMIN`, `CUSTOMER` | 16.789.012-1 |

Estos usuarios se usan para el login del frontend (Authorization Code + PKCE) y para ver la
diferencia entre un `CUSTOMER` y un `ADMIN` al probar los roles.

### 3.5 Obtener un token para pruebas

Como el **password grant está deshabilitado** en todos los clients, hay dos caminos según
lo que quieras probar:

**Opción A — Token de servicio (`client_credentials`).** Es la forma más práctica de
obtener un access token por línea de comandos, usando un client confidential con service
account (los cuatro servicios lo tienen). Este token representa al **servicio**, no a un
usuario, y sirve para llamar a los endpoints protegidos por autenticación:

```bash
curl -s -X POST \
  http://localhost:8081/realms/antu-bank/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=account-service" \
  -d "client_secret=account-service-demo-secret"
```

La respuesta incluye `access_token`. Para extraerlo directo con `jq`:

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8081/realms/antu-bank/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=account-service" \
  -d "client_secret=account-service-demo-secret" | jq -r .access_token)
```

> **Ojo con los roles:** un token de `client_credentials` **no** trae los roles de realm de
> un usuario (`CUSTOMER`/`ADMIN`) a menos que se asignen al service account del client. Por
> lo tanto sirve para probar autenticación (que un token válido pasa y uno ausente da 401),
> pero **no** para probar autorización por rol de usuario.

**Opción B — Token de un usuario real (`cliente.demo` / `admin.demo`).** Para obtener un
token que **sí** lleve los roles del usuario, hay que pasar por el flujo **Authorization
Code + PKCE**, que es el que usa el frontend:

1. El frontend Next.js (`client_id=antu-bank-frontend`) redirige al login de Keycloak.
2. El usuario se autentica con `cliente.demo` / `demo1234`.
3. Keycloak devuelve un código y el frontend lo canjea (con PKCE) por el access token.

Para hacerlo a mano en pruebas, lo más simple es iniciar sesión en la app frontend y copiar
el token desde ahí, o usar una herramienta que soporte PKCE (Postman/Bruno/Insomnia tienen
"Authorization Code with PKCE"). El password grant por `curl` con los clients de aplicación
**no** está disponible por diseño.

**Opción C — Token de usuario por password grant con el client de pruebas.** Para probar la
API cómodamente desde Postman/Bruno o `curl`, el realm incluye el client público
`antu-bank-postman` con Direct Access Grant habilitado (solo demo). Emite un token del
**usuario** (con sus roles `CUSTOMER`/`ADMIN`), a diferencia del token de servicio de la
Opción A:

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8081/realms/antu-bank/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=antu-bank-postman" \
  -d "username=cliente.demo" \
  -d "password=demo1234" | jq -r .access_token)
```

Usa `cliente.demo` para operaciones `CUSTOMER` (crear cuenta, transferir) y `admin.demo`
para las `ADMIN` (registrar asientos en el ledger). Esta es la vía que usa la colección de
pruebas en [`docs/api`](../../docs/api/README.md).

---

## 4. Cómo probar acceso a la API

Con un token en la variable `TOKEN`, llama a un endpoint protegido. Ejemplo con
`account-service` (puerto **8082**), endpoint `GET /accounts?rut=...`:

```bash
curl -i \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/accounts?rut=12.345.678-5"
```

Endpoints de `account-service` (`AccountController`):

- `POST /accounts` — crear cuenta.
- `GET /accounts/{id}` — consultar por id.
- `GET /accounts?rut=` — listar por RUT del titular.

Rutas **abiertas** (no requieren token) según el `SecurityConfig`: health de actuator,
`/v3/api-docs/**`, `/swagger-ui/**` y `/swagger-ui.html`.

### Qué significan 401 y 403

- **401 Unauthorized:** falta el token, está mal formado, expiró o la firma no valida. En
  resumen: **Keycloak no te reconoce**. Solución: obtener un token fresco y válido.
- **403 Forbidden:** el token es válido (estás autenticado), pero **no tienes el rol
  necesario** para esa operación. Solución: usar un usuario con el rol requerido (por
  ejemplo, `admin.demo` para acciones de `ADMIN`).

### Puertos de referencia

| Servicio | Puerto (por defecto) |
|---|---|
| Keycloak | 8081 |
| account-service | 8082 |
| ledger-service | 8083 |
| transfer-service | 8084 |
| fraud-service | 8085 |
| notification-service | 8086 |

---

## Referencias

- Realm versionado: [`realm-export.json`](./realm-export.json)
- Stack local: [`infra/docker-compose.yml`](../docker-compose.yml)
- Config de resource server por servicio: `services/<servicio>/src/main/resources/application.yml`
- Diseño (sección 7, seguridad): `.kiro/specs/neobank/design.md`
