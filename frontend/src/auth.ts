import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

/**
 * Configuración central de autenticación (Auth.js v5 / NextAuth) para Antu Bank.
 *
 * Flujo: OpenID Connect **Authorization Code + PKCE (S256)** contra Keycloak.
 *
 * El client `antu-bank-frontend` está definido en `infra/keycloak/realm-export.json`
 * como **client público** (`publicClient: true`): no tiene `client secret`. Por eso
 * configuramos el proveedor sin secreto y forzamos las verificaciones `pkce` + `state`,
 * que es exactamente lo que protege el canje del código en clients públicos.
 *
 * Variables de entorno (ver `.env.example`):
 *   - AUTH_KEYCLOAK_ISSUER    → issuer del realm (ej. http://localhost:8081/realms/antu-bank)
 *   - AUTH_KEYCLOAK_ID        → clientId del client público (antu-bank-frontend)
 *   - AUTH_SECRET             → secreto propio de Auth.js para firmar la cookie de sesión
 *   - NEXTAUTH_URL / AUTH_URL → URL base del frontend (http://localhost:3000)
 *
 * El `access_token` de Keycloak se guarda en el JWT de sesión y se expone en la sesión
 * del lado servidor para poder llamar más adelante al gateway/BFF GraphQL (tarea 10.2).
 */
export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    Keycloak({
      issuer: process.env.AUTH_KEYCLOAK_ISSUER,
      clientId: process.env.AUTH_KEYCLOAK_ID,
      // Client público: sin secreto. Auth.js exige la propiedad, así que la dejamos vacía.
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET ?? "",
      // Authorization Code + PKCE (S256). `state` protege contra CSRF en el redirect.
      checks: ["pkce", "state"],
      // El proveedor puede omitir client_secret al canjear el código (client público).
      client: {
        token_endpoint_auth_method: "none",
      },
    }),
  ],
  callbacks: {
    /**
     * Se ejecuta al crear/actualizar el JWT de sesión. Al iniciar sesión, `account`
     * trae los tokens emitidos por Keycloak; los persistimos para reutilizarlos.
     */
    async jwt({ token, account, profile }) {
      if (account) {
        token.accessToken = account.access_token;
        token.idToken = account.id_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt = account.expires_at;
      }
      if (profile) {
        const perfil = profile as {
          preferred_username?: string;
          rut?: string;
        };
        // `preferred_username` es el nombre de usuario en Keycloak (ej. cliente.demo).
        token.preferredUsername = perfil.preferred_username;
        // El RUT del titular puede venir como claim si el realm mapea el atributo `rut`
        // (hoy los tokens demo no lo mapean; ver realm-export.json). Se guarda si existe para
        // que el BFF pueda resolver `me(rut:)` sin depender de un fallback (tarea 10.2).
        if (perfil.rut) {
          token.rut = perfil.rut;
        }
      }
      return token;
    },
    /**
     * Expone el access token en el objeto de sesión para que el servidor pueda
     * adjuntarlo como `Authorization: Bearer` al llamar al gateway más adelante.
     */
    async session({ session, token }) {
      session.accessToken = token.accessToken as string | undefined;
      if (session.user) {
        session.user.preferredUsername = token.preferredUsername as
          | string
          | undefined;
        session.user.rut = token.rut as string | undefined;
      }
      return session;
    },
  },
});
