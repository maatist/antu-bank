# Revisión de seguridad — NeoBank (Antu Bank)

> Tarea 13.5 · Requisito 13, criterio 5: *EL SISTEMA DEBERÁ pasar una revisión de seguridad
> (security headers, CORS, manejo de secretos) y contar con pruebas end-to-end.*
>
> Este documento resume la postura de seguridad del proyecto, deja constancia de la revisión de
> cabeceras, CORS y manejo de secretos, y enlaza las pruebas automatizadas que la respaldan. Es una
> demo de portafolio, no un sistema en producción real; las recomendaciones para un despliegue
> productivo se marcan de forma explícita.

## Resumen ejecutivo

| Área                | Estado    | Nota breve |
|---------------------|-----------|------------|
| Cabeceras HTTP      | ✅ Pass   | HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` y CSP en el borde (gateway) y en el frontend (Vercel). |
| CORS                | ✅ Pass   | Política acotada por entorno (`ALLOWED_ORIGINS`), sin comodín con credenciales. |
| Manejo de secretos  | ⚠️ Pass con salvedad | Sin secretos reales versionados. `realm-export.json` contiene secretos y contraseñas **de demo** (aceptable para la demo; rotar/externalizar en cualquier despliegue real). |
| Autenticación/Autz  | ✅ Pass   | OIDC (Keycloak), JWT validado vía JWKS en el borde y en cada servicio; roles CUSTOMER/ADMIN (401 sin token, 403 sin rol). |
| Pruebas end-to-end  | ✅ Pass   | Cobertura de borde en el gateway + tests de integración por servicio (Testcontainers) + flujo de frontend (Testing Library). |

---

## 1. Cabeceras de seguridad

El gateway (`api-gateway`, Spring WebFlux) es el punto de entrada público de la API. La cadena de
seguridad de producción (`SecurityConfig#securityWebFilterChain`, perfil `!test`) fija estas
cabeceras en **todas** las respuestas, incluidas las rutas públicas:

- **`Strict-Transport-Security`** (HSTS): `max-age` de 1 año, `includeSubDomains`. Fuerza HTTPS en
  el navegador. Solo tiene efecto sobre conexiones seguras; en la demo el TLS lo termina el
  PaaS/Vercel/ALB en el borde, no el propio gateway, por lo que la cabecera es coherente con ese
  despliegue.
- **`X-Content-Type-Options: nosniff`**: impide el *MIME sniffing*.
- **`X-Frame-Options: DENY`**: el gateway no debe embeberse en iframes (defensa contra clickjacking).
- **`Referrer-Policy`**: evita filtrar la URL completa a orígenes cruzados.
- **`Content-Security-Policy`** conservadora:
  `default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'`.

**Contrapartida documentada (CSP + Swagger/GraphiQL):** el gateway sirve las UIs **GraphiQL** y
**Swagger UI**, que cargan scripts y estilos *inline*. Por eso la CSP permite `'unsafe-inline'` en
`script-src`/`style-src`; una CSP más estricta (con `nonce`/`sha256`) rompería esas UIs. El riesgo
XSS es bajo porque el gateway no renderiza HTML con datos de usuario arbitrarios (las respuestas de
negocio son JSON). `frame-ancestors 'none'` refuerza `X-Frame-Options`. Para un endpoint que sirviera
HTML con datos de usuario se recomendaría endurecer la CSP y separar las UIs de documentación tras
una CSP propia.

El **frontend en Vercel** aplica sus propias cabeceras (`frontend/vercel.json`): HSTS, `nosniff`,
`X-Frame-Options: DENY` y `Referrer-Policy`. Es decir, ambos bordes (frontend y API) quedan cubiertos.

## 2. CORS

El frontend Next.js corre en un **origen distinto** del gateway (en local `http://localhost:3000`;
en producción, el dominio de Vercel), por lo que las llamadas del navegador al borde de la API son
*cross-origin* y requieren una política CORS válida.

Política implementada en `SecurityConfig#corsConfigurationSource`:

- **Orígenes permitidos** vía variable de entorno `ALLOWED_ORIGINS` (lista separada por comas). Default
  de desarrollo: `http://localhost:3000`. El perfil `demo` usa un placeholder de Vercel
  (`https://antu-bank.vercel.app`) que **debe** sobreescribirse por entorno con el dominio real.
- **Sin comodín con credenciales.** Se usa `allowedOrigins` con orígenes exactos (no
  `allowedOriginPatterns` con `*`). Combinar `*` con `allowCredentials(true)` está prohibido por la
  especificación CORS y sería inseguro.
- **Métodos**: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- **Headers permitidos**: `Authorization` (Bearer del BFF), `Content-Type`, `Idempotency-Key`
  (transferencias), `Accept-Language` (i18n), `Accept`.
- **`allowCredentials(true)`** y `maxAge` de 1 hora para las respuestas preflight.
- Las peticiones **preflight** (`OPTIONS`) se permiten sin token en la cadena de autorización.

Recomendación productiva: fijar `ALLOWED_ORIGINS` al dominio exacto del frontend (sin subdominios
comodín) y revisar periódicamente la lista.

## 3. Manejo de secretos

**Hallazgo principal:** no hay secretos de producción versionados en el repositorio. La revisión
cubrió `application*.yml`, Terraform, Helm, Keycloak y archivos de entorno.

- **`.gitignore`** excluye `.env`, `.env.*` (salvo `*.example`), `*.tfvars` y `*.tfvars.json` (salvo
  `*.example.tfvars`), además de estado de Terraform. Verificado: `frontend/.env.local` e
  `infra/.env.demo` están ignorados; solo se versionan las plantillas `*.example`.
- **Terraform** (`infra/terraform`): las credenciales de RDS y MSK se generan con `random_password`
  y se guardan en **AWS Secrets Manager** (`aws_secretsmanager_secret`), no en el estado en texto
  plano ni en el repo. Los outputs emiten *ARNs*, no los valores.
- **Helm** (`infra/helm/antu-bank`): patrón `existingSecret` — en producción el `Secret` se crea
  fuera del chart (External Secrets Operator, AWS Secrets Manager, SealedSecrets, `kubectl create
  secret`) y se referencia por nombre. `values.example.yaml` solo trae placeholders.
- **Frontend**: `AUTH_SECRET` y demás vienen de `.env.local` (ignorado); el `.env.example` trae
  instrucciones para generarlo (`openssl rand`/`npx auth secret`), no un valor real.

**Salvedad — secretos de DEMO en `infra/keycloak/realm-export.json`:** este archivo, versionado a
propósito para poder levantar el realm `antu-bank` con un solo comando, contiene:

- **Secretos de clients confidential de demo**: `gateway-demo-secret`, `account-service-demo-secret`,
  `ledger-service-demo-secret`, `transfer-service-demo-secret`.
- **Contraseñas de usuarios de demo**: `demo1234` para `cliente.demo` (CUSTOMER) y `admin.demo`
  (ADMIN). Estas credenciales son visibles a propósito en la landing (tarea 13.4) para que
  reclutadores prueben sin fricción.

**Evaluación:** aceptable para una demo pública desechable, cuyo objetivo es que cualquiera pueda
probar el sistema. El realm `antu-bank` es un entorno de demostración, no un IdP real, y los datos
son sintéticos (RUT y nombres chilenos de ejemplo).

**Recomendación para un despliegue real:** rotar y **externalizar** estos secretos — importar el
realm sin credenciales embebidas y suministrar los client secrets y las contraseñas por variables de
entorno / gestor de secretos; deshabilitar los usuarios de demo o forzar cambio de contraseña; no
versionar `realm-export.json` con secretos.

## 4. Autenticación y autorización (contexto)

- **IdP**: Keycloak (OIDC), realm versionado `antu-bank`. Client público para el frontend (Auth Code
  + PKCE), clients confidential por servicio.
- **Validación JWT vía JWKS**: el gateway valida la firma del token en el borde y **propaga** el
  header `Authorization` a los servicios downstream, que a su vez lo validan como resource servers.
- **Roles** `CUSTOMER`/`ADMIN` mapeados desde `realm_access.roles` a authorities `ROLE_*`.
- **401 sin token / 403 sin rol** verificado en los `AuthorizationSecurityTest` de account, ledger y
  transfer, y en las pruebas de borde del gateway.
- **Aislamiento de datos**: database-per-service (una base PostgreSQL por servicio).
- **TLS**: terminado en el borde (Fly/Render/ALB para el backend, Vercel para el frontend).

## 5. Pruebas end-to-end

- **Borde del gateway** (`SecurityConfigTest`, `EdgeSecuritySmokeTest`): presencia de las cabeceras de
  seguridad en las respuestas; preflight CORS desde un origen permitido responde OK y desde un origen
  no permitido se rechaza; rutas públicas (GraphiQL, health) accesibles sin token; `/api/**` y
  `/graphql` responden `401` sin token.
- **Enrutamiento y resiliencia** (`GatewayRoutingIntegrationTest`, `CircuitBreakerFallbackIntegrationTest`):
  propagación de token, StripPrefix y fallback del circuit breaker.
- **Integración por servicio** (Testcontainers): account (PostgreSQL), ledger (doble entrada e
  inmutabilidad), transfer (idempotencia/concurrencia), outbox → Kafka → asiento, saga y
  compensación, reglas de fraude.
- **Frontend** (Testing Library, tarea 10.5): flujo login → cuentas/saldos → transferencia →
  historial.

### Qué NO cubren las pruebas automatizadas (limitaciones)

- El comportamiento CORS real de un **navegador** contra el despliegue público (los tests verifican la
  respuesta del servidor, no la aplicación de la política por el navegador).
- Un **E2E vivo de los 6 microservicios** simultáneos con Keycloak, Kafka y las 3 bases levantados a
  la vez (se cubre por partes con Testcontainers y con el flujo de frontend, no en un único arnés
  full-stack).
- La efectividad del **HSTS** depende de servir siempre por HTTPS en el borde (responsabilidad del
  PaaS/Vercel/ALB), no verificable en test unitario.

## Checklist final

- [x] **Cabeceras**: HSTS, nosniff, frame DENY, referrer, CSP en gateway; cabeceras en Vercel.
- [x] **CORS**: acotado por `ALLOWED_ORIGINS`, sin comodín con credenciales, `Authorization` permitido.
- [x] **Secretos**: sin secretos reales versionados; `.gitignore` cubre `.env*`/`*.tfvars`; Terraform
      (Secrets Manager) y Helm (`existingSecret`) correctos. ⚠️ `realm-export.json` trae secretos de
      demo → rotar/externalizar para producción.
- [x] **Autz**: JWT vía JWKS, roles CUSTOMER/ADMIN, 401 sin token / 403 sin rol.
- [x] **Pruebas E2E**: borde del gateway + integración por servicio + flujo de frontend.
