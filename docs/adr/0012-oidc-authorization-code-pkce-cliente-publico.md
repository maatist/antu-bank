# ADR-0012: OIDC Authorization Code + PKCE con cliente público para el frontend

- **Estado:** Aceptado
- **Fecha:** decisión reflejada en el realm `antu-bank` (tarea 8)

## Contexto

El frontend de Antu Bank es una aplicación **Next.js que se ejecuta en el navegador**. Una app de
este tipo no puede guardar un **client secret** de forma segura: cualquier secreto embebido en
código que llega al navegador es, en la práctica, público. El flujo OAuth2 tradicional para
clientes confidenciales (Authorization Code con client secret) no aplica a un cliente que no puede
guardar secretos.

El flujo *implicit*, que históricamente se usaba para SPAs, está **desaconsejado** por la industria
por exponer tokens en la URL y carecer de protección contra intercepción del código de
autorización. La recomendación actual de OAuth 2.0 para clientes públicos es **Authorization Code
+ PKCE**.

Por otro lado, los **servicios backend** sí pueden custodiar secretos, y cada uno se comunica en
un contexto server-to-server donde un client confidencial es apropiado.

## Decisión

En el realm `antu-bank` de Keycloak:

- El frontend usa un **client público `antu-bank-frontend`** con **Authorization Code + PKCE**
  (`pkce.code.challenge.method = S256`), sin client secret.
- Cada servicio backend usa un **client confidential** propio (`api-gateway`, `account-service`,
  `ledger-service`, `transfer-service`, etc.).
- Los servicios actúan como **resource servers** que validan el JWT contra el JWKS del realm.

## Consecuencias

**Beneficios**

- Flujo seguro y recomendado para SPAs: PKCE (S256) protege contra intercepción del código de
  autorización sin necesidad de un secreto en el cliente.
- Cada tipo de cliente usa el modelo que le corresponde: público con PKCE para el navegador,
  confidencial para los servicios.
- Se evita el flujo implicit, obsoleto e inseguro.

**Trade-offs**

- El client público implica que el frontend **no autentica al cliente** (solo al usuario); la
  seguridad recae en PKCE, el redirect URI registrado y la validación del token en el borde.
- Más clients que gestionar en el realm (uno público + uno confidencial por servicio), a cambio de
  un modelo de confianza correcto.

Es el trade-off estándar y correcto para un frontend de navegador con backend de microservicios.
