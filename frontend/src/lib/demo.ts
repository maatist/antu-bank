/**
 * Credenciales y datos del usuario demo para el ingreso "como invitado" (tarea 10.4,
 * Requisito 10, criterio 4).
 *
 * El client `antu-bank-frontend` de Keycloak es **público** (Authorization Code + PKCE) y tiene
 * `directAccessGrantsEnabled: false` en `infra/keycloak/realm-export.json`. Eso significa que NO
 * es posible un auto-login 100% silencioso desde el navegador: obtener un token requiere pasar por
 * la pantalla de login de Keycloak (que es justamente lo que protege a un client público).
 *
 * Lo que sí podemos hacer para un reclutador es:
 *   1. Iniciar el flujo estándar pasando `login_hint` con el usuario demo, de modo que Keycloak
 *      **pre-rellene el campo de usuario** y solo quede confirmar/escribir la contraseña.
 *   2. Mostrar las credenciales demo de forma visible para que el ingreso sea de un solo paso.
 *
 * Los valores coinciden con el usuario `cliente.demo` sembrado en el realm (rol CUSTOMER).
 */

/** Nombre de usuario demo en Keycloak (rol CUSTOMER). */
export const USUARIO_DEMO = process.env.DEMO_USERNAME ?? "cliente.demo";

/** Contraseña demo del usuario `cliente.demo` (ver realm-export.json). Solo para la demo. */
export const CLAVE_DEMO = process.env.DEMO_PASSWORD ?? "demo1234";

/**
 * Usuario administrador demo (rol ADMIN + CUSTOMER). Se muestra en la portada junto al usuario
 * cliente para que un reclutador pueda probar también las operaciones de administración. Coincide
 * con `admin.demo` sembrado en `infra/keycloak/realm-export.json`.
 */
export const USUARIO_ADMIN_DEMO = process.env.DEMO_ADMIN_USERNAME ?? "admin.demo";

/** Contraseña del usuario administrador demo `admin.demo` (ver realm-export.json). Solo demo. */
export const CLAVE_ADMIN_DEMO = process.env.DEMO_ADMIN_PASSWORD ?? "demo1234";

/**
 * Enlaces públicos opcionales a la documentación/demo (portafolio, tarea 13.4). Se muestran en la
 * portada solo si la variable de entorno correspondiente está definida, de modo que en local (sin
 * URLs reales) no aparezcan enlaces rotos. Coinciden con los placeholders del README (§7).
 */
export const ENLACES_DEMO = {
  /** Repositorio del proyecto en GitHub. */
  repositorio: process.env.NEXT_PUBLIC_REPO_URL ?? null,
  /** Swagger UI / OpenAPI a través del gateway público. */
  swagger: process.env.NEXT_PUBLIC_SWAGGER_URL ?? null,
  /** Playground GraphQL (GraphiQL) a través del gateway público. */
  graphiql: process.env.NEXT_PUBLIC_GRAPHIQL_URL ?? null,
} as const;
