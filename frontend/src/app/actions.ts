"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { getTranslations } from "next-intl/server";
import { auth, signIn, signOut } from "@/auth";
import { iniciarTransferencia } from "@/lib/transfer";
import type { EstadoTransferencia } from "@/lib/transfer-state";
import { COOKIE_IDIOMA, esIdiomaSoportado } from "@/i18n/config";
import { USUARIO_DEMO } from "@/lib/demo";

/**
 * Inicia el flujo de login contra Keycloak (Authorization Code + PKCE).
 * Tras autenticarse, redirige al panel protegido.
 */
export async function iniciarSesion() {
  await signIn("keycloak", { redirectTo: "/panel" });
}

/**
 * Ingreso "como invitado" / demo para reclutadores (tarea 10.4, Requisito 10, criterio 4).
 *
 * El client de Keycloak es público (Auth Code + PKCE) y no permite direct grants, así que no hay
 * auto-login totalmente silencioso: se pasa por la pantalla de Keycloak. Para que el ingreso sea
 * de un solo paso, iniciamos el flujo estándar con el parámetro OIDC `login_hint`, que hace que
 * Keycloak **pre-rellene el nombre de usuario** (`cliente.demo`); el reclutador solo confirma con la
 * contraseña demo (visible en la portada). Al autenticarse, redirige al panel protegido.
 */
export async function iniciarSesionDemo() {
  await signIn(
    "keycloak",
    { redirectTo: "/panel" },
    // Parámetros extra para el endpoint de autorización de Keycloak.
    { login_hint: USUARIO_DEMO },
  );
}

/**
 * Cierra la sesión local. Se redirige a la portada.
 * (El cierre de sesión federado en Keycloak se refina en tareas posteriores.)
 */
export async function cerrarSesion() {
  await signOut({ redirectTo: "/" });
}

/**
 * Cambia el idioma de la aplicación (tarea 10.3, Requisito 10, criterio 6). Persiste el idioma
 * elegido en la cookie `NEXT_LOCALE` (un año) y revalida la vista para que Server Components y
 * Client Components se re-rendericen con el catálogo y el locale de formato correspondientes. Si el
 * valor no es un idioma soportado, no realiza cambios.
 */
export async function cambiarIdioma(idioma: string) {
  if (!esIdiomaSoportado(idioma)) {
    return;
  }
  const cookieStore = await cookies();
  cookieStore.set(COOKIE_IDIOMA, idioma, {
    path: "/",
    maxAge: 60 * 60 * 24 * 365,
    sameSite: "lax",
  });
  revalidatePath("/", "layout");
}

/**
 * Server action del formulario "iniciar transferencia". Valida la entrada, adjunta el access token
 * de la sesión y llama al gateway. Al aceptarse, revalida `/panel` para que saldos e historial se
 * refresquen. Devuelve un `EstadoTransferencia` para el feedback de estado en la UI.
 */
export async function iniciarTransferenciaAction(
  _estadoPrevio: EstadoTransferencia,
  formData: FormData,
): Promise<EstadoTransferencia> {
  const t = await getTranslations("Transferencia");
  const session = await auth();
  if (!session?.user) {
    return {
      estado: "error",
      mensaje: t("sesionExpirada"),
    };
  }

  const sourceAccountId = String(formData.get("sourceAccountId") ?? "").trim();
  const destinationAccountId = String(
    formData.get("destinationAccountId") ?? "",
  ).trim();
  const montoTexto = String(formData.get("amount") ?? "").trim();

  // Validación local: cuentas presentes y distintas, monto entero positivo (CLP en pesos).
  if (!sourceAccountId || !destinationAccountId) {
    return {
      estado: "error",
      mensaje: t("seleccionaCuentas"),
    };
  }
  if (sourceAccountId === destinationAccountId) {
    return {
      estado: "error",
      mensaje: t("cuentasDistintas"),
    };
  }
  const amountMinor = Number(montoTexto);
  if (!Number.isInteger(amountMinor) || amountMinor <= 0) {
    return {
      estado: "error",
      mensaje: t("montoInvalido"),
    };
  }

  const respuesta = await iniciarTransferencia(
    { sourceAccountId, destinationAccountId, amountMinor },
    session.accessToken,
  );

  if (!respuesta.ok) {
    return {
      estado: "error",
      mensaje: respuesta.mensaje,
      detalle: respuesta.detalle,
    };
  }

  // Refresca los datos del panel (saldos e historial) tras aplicar la transferencia.
  revalidatePath("/panel");

  return {
    estado: "success",
    idTransferencia: respuesta.transferencia.id,
    estadoTransferencia: respuesta.transferencia.status,
    mensaje: t("procesada"),
  };
}
