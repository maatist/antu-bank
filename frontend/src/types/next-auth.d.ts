import type { DefaultSession } from "next-auth";

/**
 * Ampliación de tipos de Auth.js para exponer el access token de Keycloak y el
 * nombre de usuario en la sesión, y para tiparlos también en el JWT interno.
 */
declare module "next-auth" {
  interface Session {
    /** Access token JWT emitido por Keycloak (para llamar al gateway/BFF). */
    accessToken?: string;
    user: {
      /** `preferred_username` del token de Keycloak (ej. cliente.demo). */
      preferredUsername?: string;
      /** RUT del titular (formato chileno) si el realm lo mapea como claim. */
      rut?: string;
    } & DefaultSession["user"];
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    idToken?: string;
    refreshToken?: string;
    expiresAt?: number;
    preferredUsername?: string;
    rut?: string;
  }
}
