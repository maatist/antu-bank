# ADR-0007: Keycloak como IdP en lugar de Authorization Server propio

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-007)

## Contexto

Antu Bank necesita autenticación y autorización basadas en estándares: login de clientes,
emisión de tokens, roles (`CUSTOMER`, `ADMIN`) y validación de tokens en el gateway y en cada
servicio. Construir un **Authorization Server propio** (login, gestión de usuarios, emisión y
rotación de claves, endpoints OIDC, consentimiento, etc.) es un esfuerzo considerable y una
superficie de seguridad delicada de implementar y mantener correctamente.

La alternativa es adoptar un **Identity Provider** probado que hable OIDC/OAuth2 de fábrica.
**Keycloak** es el estándar de facto open source en el ecosistema JVM/Spring y cubre realms,
clients, roles, PKCE, JWKS y export/import de configuración.

## Decisión

Usar **Keycloak self-hosted** como IdP OIDC del sistema:

- Un **realm `antu-bank`** versionado en `infra/keycloak/realm-export.json` (config como código).
- Un **client público** para el frontend con Authorization Code + PKCE, y **clients confidential**
  por servicio (ver [ADR-0012](0012-oidc-authorization-code-pkce-cliente-publico.md)).
- Roles `CUSTOMER` y `ADMIN`.
- Los servicios actúan como **resource servers** que validan el JWT contra el **JWKS** del realm,
  sin llamar a Keycloak en cada request.

## Consecuencias

**Beneficios**

- Se apoya en un estándar de industria probado, con mucho menos boilerplate y menos riesgo que un
  Authorization Server casero.
- OIDC/OAuth2 completo de fábrica: PKCE, JWKS, roles, refresh tokens, etc.
- El realm versionado hace la configuración de identidad reproducible y revisable como código.
- Es una capacidad muy demandada y "vendible" en un portafolio.

**Trade-offs**

- Hay que **operar Keycloak** como componente adicional (levantarlo, configurarlo, mantenerlo
  actualizado). En la demo pública esto implica una dependencia más que gestionar.

El costo operativo se consideró menor que el de construir y asegurar un servidor de autorización
propio.
