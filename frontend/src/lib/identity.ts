import "server-only";
import type { Session } from "next-auth";

/**
 * Resolución del RUT del cliente para consultar el BFF (tarea 10.2).
 *
 * El BFF GraphQL identifica al titular por su RUT (`me(rut: "...")`) porque los tokens demo de
 * Keycloak NO mapean el atributo `rut` como claim del JWT (ver realm-export.json y la doc del
 * MeGraphQlController en el gateway). La estrategia de resolución, en orden:
 *   1. Claim `rut` del token, si el realm llega a mapearlo (session.user.rut).
 *   2. Mapa demo por `preferred_username`, coherente con los usuarios sembrados en el realm.
 *   3. Variable de entorno `DEMO_RUT` como último recurso configurable.
 *
 * Devuelve `undefined` si no se puede determinar; la vista muestra un mensaje claro en ese caso.
 */

/**
 * Mapa de usuarios demo → RUT, alineado con infra/keycloak/realm-export.json. Es solo para la demo
 * (no hay claim de RUT en el token); en producción el RUT vendría en el token o de un perfil.
 */
const RUT_POR_USUARIO_DEMO: Record<string, string> = {
  "cliente.demo": "12.345.678-5",
  "admin.demo": "16.789.012-1",
};

/** Resuelve el RUT del titular a partir de la sesión, con fallbacks para la demo. */
export function resolverRut(session: Session | null): string | undefined {
  const rutClaim = session?.user?.rut;
  if (rutClaim) {
    return rutClaim;
  }

  const usuario = session?.user?.preferredUsername;
  if (usuario && RUT_POR_USUARIO_DEMO[usuario]) {
    return RUT_POR_USUARIO_DEMO[usuario];
  }

  return process.env.DEMO_RUT || undefined;
}
