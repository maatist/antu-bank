import { redirect } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { auth } from "@/auth";
import { obtenerMe, BffError, type Account, type Transfer } from "@/lib/bff";
import { resolverRut } from "@/lib/identity";
import { localeDeFormato } from "@/i18n/config";
import { cerrarSesion } from "../actions";
import { SelectorIdioma } from "../SelectorIdioma";
import { FormularioTransferencia } from "./FormularioTransferencia";
import { TourGuiado } from "./TourGuiado";
import { VistaCuentas, VistaHistorial } from "./VistasPanel";

/**
 * Panel del cliente (tarea 10.2, Requisito 10, criterios 2 y 3).
 *
 * Consume el BFF GraphQL (`me { accounts, balances, transfers }`) del api-gateway, propagando el
 * access token de Keycloak de la sesión (server-side). Muestra tres vistas:
 *   1. Cuentas y saldos.
 *   2. Historial de transferencias.
 *   3. Formulario para iniciar una transferencia con feedback de estado del proceso.
 *
 * Los textos se localizan (es por defecto / en) y el formateo de montos/fechas usa el locale del
 * idioma activo (`es-CL` / `en-US`), Requisito 10, criterios 6 y 7 (tarea 10.3).
 */
export default async function Panel() {
  const session = await auth();
  const t = await getTranslations("Panel");
  const tc = await getTranslations("Comun");
  const locale = localeDeFormato(await getLocale());

  if (!session?.user) {
    // El middleware ya protege esta ruta; esta es una salvaguarda adicional.
    redirect("/api/auth/signin?callbackUrl=/panel");
  }

  const nombre =
    session.user.name ?? session.user.preferredUsername ?? t("clientePorDefecto");
  const rut = resolverRut(session);

  // Consulta al BFF; si falla, se muestra un aviso en vez de romper la vista.
  let cuentas: Account[] = [];
  let transferencias: Transfer[] = [];
  let errorBff: string | null = null;

  if (!rut) {
    errorBff = t("errorSinRut");
  } else {
    try {
      const me = await obtenerMe(rut, session.accessToken);
      cuentas = me.accounts;
      transferencias = me.transfers;
    } catch (error) {
      errorBff =
        error instanceof BffError
          ? `${error.message}${error.detalles ? ` (${error.detalles})` : ""}`
          : t("errorBffGenerico");
    }
  }

  return (
    <main className="contenedor contenedor--ancho">
      <header className="panel-cabecera">
        <div>
          <h1 className="marca">{t("titulo")}</h1>
          <p className="subtitulo">
            {nombre}
            {rut ? ` · ${rut}` : ""}
          </p>
        </div>
        <div className="panel-cabecera__acciones">
          <SelectorIdioma />
          <form action={cerrarSesion}>
            <button className="boton boton--secundario" type="submit">
              {tc("cerrarSesion")}
            </button>
          </form>
        </div>
      </header>

      {errorBff && (
        <div className="tarjeta aviso" role="alert">
          <strong>{t("errorTitulo")}</strong>
          <p>{errorBff}</p>
        </div>
      )}

      <section
        className="tarjeta"
        aria-labelledby="titulo-cuentas"
        data-tour="cuentas"
      >
        <h2 id="titulo-cuentas">{t("tituloCuentas")}</h2>
        <VistaCuentas cuentas={cuentas} locale={locale} t={t} />
      </section>

      <section
        className="tarjeta"
        aria-labelledby="titulo-transferir"
        data-tour="transferir"
      >
        <h2 id="titulo-transferir">{t("tituloTransferir")}</h2>
        {cuentas.length > 0 ? (
          <FormularioTransferencia cuentas={cuentas} />
        ) : (
          <p className="texto-tenue">{t("sinCuentasParaTransferir")}</p>
        )}
      </section>

      <section
        className="tarjeta"
        aria-labelledby="titulo-historial"
        data-tour="historial"
      >
        <h2 id="titulo-historial">{t("tituloHistorial")}</h2>
        <VistaHistorial
          transferencias={transferencias}
          idsCuentasPropias={new Set(cuentas.map((c) => c.id))}
          locale={locale}
          t={t}
        />
      </section>

      {/* Tour guiado para reclutadores (tarea 10.4 / 10.5, criterio 5). */}
      <TourGuiado />
    </main>
  );
}
