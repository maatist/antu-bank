import { auth } from "@/auth";

/**
 * Middleware de sesión. Protege el área privada: si se accede a `/panel` sin
 * sesión, Auth.js redirige al login de Keycloak.
 */
export default auth((req) => {
  const estaAutenticado = Boolean(req.auth?.user);
  const esRutaProtegida = req.nextUrl.pathname.startsWith("/panel");

  if (esRutaProtegida && !estaAutenticado) {
    const urlLogin = new URL("/api/auth/signin", req.nextUrl.origin);
    urlLogin.searchParams.set("callbackUrl", req.nextUrl.pathname);
    return Response.redirect(urlLogin);
  }
});

export const config = {
  // Excluye assets estáticos y las propias rutas de Auth.js del middleware.
  matcher: ["/((?!api/auth|_next/static|_next/image|favicon.ico).*)"],
};
