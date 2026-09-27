import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { auth } from "@/auth";
import { iniciarSesion, iniciarSesionDemo, cerrarSesion } from "./actions";
import { SelectorIdioma } from "./SelectorIdioma";
import {
  USUARIO_DEMO,
  CLAVE_DEMO,
  USUARIO_ADMIN_DEMO,
  CLAVE_ADMIN_DEMO,
  ENLACES_DEMO,
} from "@/lib/demo";

/**
 * Portada de Antu Bank.
 *
 * - Sin sesión: landing orientada a reclutadores (tarea 13.4, Requisito 13, criterio 4). Presenta
 *   qué es el proyecto (core bancario chileno) y sus puntos destacados, mantiene el ingreso
 *   "Iniciar sesión" (Authorization Code + PKCE) y "Entrar como invitado (demo)", muestra las
 *   credenciales demo visibles (cliente y admin) y enlaces opcionales a la API/código público.
 * - Con sesión: saluda al usuario por su nombre y ofrece ir al panel o cerrar sesión.
 *
 * Los textos se localizan (es por defecto / en) y se ofrece el selector de idioma (tarea 10.3).
 * El tour guiado en sí vive dentro del panel (`panel/TourGuiado.tsx`); aquí solo se lo menciona.
 */
export default async function Home() {
  const session = await auth();
  const t = await getTranslations("Portada");
  const tc = await getTranslations("Comun");

  return (
    <main className="contenedor">
      <div className="tarjeta">
        <div className="tarjeta-cabecera">
          <h1 className="marca">Antu Bank</h1>
          <SelectorIdioma />
        </div>
        <p className="subtitulo">{t("subtitulo")}</p>

        {session?.user ? (
          <>
            <p>
              {t("saludo", {
                nombre:
                  session.user.name ??
                  session.user.preferredUsername ??
                  t("clientePorDefecto"),
              })}
            </p>
            <div className="acciones">
              <Link className="boton" href="/panel">
                {t("irAlPanel")}
              </Link>
              <form action={cerrarSesion}>
                <button className="boton boton--secundario" type="submit">
                  {tc("cerrarSesion")}
                </button>
              </form>
            </div>
          </>
        ) : (
          <>
            {/* Hero: qué es Antu Bank (portafolio, tarea 13.4). */}
            <p className="hero-descripcion">{t("heroDescripcion")}</p>

            {/* Puntos destacados de arquitectura, escaneables para un reclutador. */}
            <section className="highlights" aria-labelledby="highlights-titulo">
              <h2 id="highlights-titulo" className="highlights__titulo">
                {t("highlightsTitulo")}
              </h2>
              <ul className="highlights__lista">
                <li>{t("highlightLedger")}</li>
                <li>{t("highlightIdempotencia")}</li>
                <li>{t("highlightEventos")}</li>
                <li>{t("highlightSeguridad")}</li>
                <li>{t("highlightGraphql")}</li>
                <li>{t("highlightObservabilidad")}</li>
              </ul>
            </section>

            <p>{t("invitacion")}</p>
            <div className="acciones">
              <form action={iniciarSesion}>
                <button className="boton" type="submit">
                  {t("iniciarSesion")}
                </button>
              </form>
              {/* Ingreso como invitado / demo para reclutadores (tarea 10.4, criterio 4). */}
              <form action={iniciarSesionDemo}>
                <button className="boton boton--secundario" type="submit">
                  {t("entrarComoInvitado")}
                </button>
              </form>
            </div>

            {/* El tour guiado vive dentro del panel; aquí solo se lo menciona (tarea 13.4). */}
            <p className="texto-tenue tour-nota">{t("tourNota")}</p>

            {/* Credenciales demo visibles: el ingreso "invitado" pre-rellena el usuario en
                Keycloak y basta con confirmar con esta contraseña (client público, sin registro).
                Se muestran ambos usuarios (cliente/admin) intencionalmente (Requisito 13, criterio 4). */}
            <div className="credenciales-demo" role="note">
              <p className="credenciales-demo__titulo">{t("demoTitulo")}</p>
              <p className="texto-tenue">{t("demoDescripcion")}</p>
              <div className="credenciales-demo__cuenta">
                <p className="credenciales-demo__rol">{t("demoRolCliente")}</p>
                <dl className="credenciales-demo__lista">
                  <div>
                    <dt>{t("demoUsuario")}</dt>
                    <dd>
                      <code>{USUARIO_DEMO}</code>
                    </dd>
                  </div>
                  <div>
                    <dt>{t("demoClave")}</dt>
                    <dd>
                      <code>{CLAVE_DEMO}</code>
                    </dd>
                  </div>
                </dl>
              </div>
              <div className="credenciales-demo__cuenta">
                <p className="credenciales-demo__rol">{t("demoRolAdmin")}</p>
                <dl className="credenciales-demo__lista">
                  <div>
                    <dt>{t("demoUsuario")}</dt>
                    <dd>
                      <code>{USUARIO_ADMIN_DEMO}</code>
                    </dd>
                  </div>
                  <div>
                    <dt>{t("demoClave")}</dt>
                    <dd>
                      <code>{CLAVE_ADMIN_DEMO}</code>
                    </dd>
                  </div>
                </dl>
              </div>
              <p className="texto-tenue credenciales-demo__nota">
                {t("demoSoloDemo")}
              </p>
            </div>

            {/* Enlaces públicos opcionales (README §7): solo se renderizan si hay URL configurada. */}
            {(ENLACES_DEMO.repositorio ||
              ENLACES_DEMO.swagger ||
              ENLACES_DEMO.graphiql) && (
              <section className="enlaces-demo" aria-labelledby="enlaces-titulo">
                <h2 id="enlaces-titulo" className="enlaces-demo__titulo">
                  {t("enlacesTitulo")}
                </h2>
                <ul className="enlaces-demo__lista">
                  {ENLACES_DEMO.repositorio && (
                    <li>
                      <a
                        href={ENLACES_DEMO.repositorio}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {t("enlaceRepositorio")}
                      </a>
                    </li>
                  )}
                  {ENLACES_DEMO.swagger && (
                    <li>
                      <a
                        href={ENLACES_DEMO.swagger}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {t("enlaceSwagger")}
                      </a>
                    </li>
                  )}
                  {ENLACES_DEMO.graphiql && (
                    <li>
                      <a
                        href={ENLACES_DEMO.graphiql}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {t("enlaceGraphiql")}
                      </a>
                    </li>
                  )}
                </ul>
              </section>
            )}
          </>
        )}
      </div>
    </main>
  );
}
