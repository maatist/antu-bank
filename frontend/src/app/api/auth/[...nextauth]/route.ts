// Ruta catch-all de Auth.js (NextAuth v5). Maneja el inicio de sesión, el
// callback de Keycloak (canje del código + PKCE), el cierre de sesión y la sesión.
// Endpoints resultantes: /api/auth/signin, /api/auth/callback/keycloak, etc.
import { handlers } from "@/auth";

export const { GET, POST } = handlers;
