import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import mensajesEs from "../../messages/es.json";

/**
 * Tests de la portada / landing de portafolio (tarea 13.4, Requisito 13, criterio 4).
 *
 * `Home` es un Server Component asíncrono que usa `auth()` (sesión) y `getTranslations`
 * (next-intl/server). Mockeamos ambos colaboradores del lado servidor:
 *   - `@/auth`             → controla si hay o no sesión,
 *   - `next-intl/server`   → resuelve los strings reales del catálogo español (es por defecto),
 *     de modo que asertemos el texto que verá el usuario.
 *   - `@/lib/demo`         → credenciales/enlaces fijos e independientes del entorno.
 *   - `./actions`          → server actions (no ejecutables en jsdom); solo interesan los botones.
 *
 * Verifica que un reclutador sin sesión vea: el hero, los highlights de arquitectura, la mención
 * del tour guiado, los botones de ingreso (login + invitado) y las credenciales demo visibles
 * (cliente y admin).
 */
const authMock = vi.fn();

vi.mock("@/auth", () => ({
  auth: () => authMock(),
  signIn: vi.fn(),
  signOut: vi.fn(),
}));

vi.mock("./actions", () => ({
  iniciarSesion: vi.fn(),
  iniciarSesionDemo: vi.fn(),
  cerrarSesion: vi.fn(),
}));

// El selector de idioma es un Client Component con sus propios hooks/acciones; lo sustituimos por
// un marcador para aislar el render del Server Component de la portada.
vi.mock("./SelectorIdioma", () => ({
  SelectorIdioma: () => <div data-testid="selector-idioma" />,
}));

vi.mock("@/lib/demo", () => ({
  USUARIO_DEMO: "cliente.demo",
  CLAVE_DEMO: "demo1234",
  USUARIO_ADMIN_DEMO: "admin.demo",
  CLAVE_ADMIN_DEMO: "demo1234",
  ENLACES_DEMO: { repositorio: null, swagger: null, graphiql: null },
}));

// Resuelve las traducciones reales del catálogo español para el namespace pedido.
vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: keyof typeof mensajesEs) => {
    const grupo = mensajesEs[namespace] as Record<string, string>;
    return (clave: string, valores?: Record<string, string>) => {
      const plantilla = grupo[clave] ?? clave;
      return valores
        ? plantilla.replace(/\{(\w+)\}/g, (_, k) => valores[k] ?? `{${k}}`)
        : plantilla;
    };
  },
}));

import Home from "./page";

/** Renderiza el Server Component asíncrono resolviendo su promesa. */
async function renderHome() {
  const ui = await Home();
  return render(ui);
}

describe("Portada (landing de portafolio)", () => {
  beforeEach(() => {
    authMock.mockReset();
  });

  it("sin sesión, muestra el hero y los highlights de arquitectura", async () => {
    authMock.mockResolvedValue(null);
    await renderHome();

    expect(screen.getByText(mensajesEs.Portada.heroDescripcion)).toBeInTheDocument();

    const highlights = screen.getByRole("region", {
      name: mensajesEs.Portada.highlightsTitulo,
    });
    expect(
      within(highlights).getByText(mensajesEs.Portada.highlightLedger),
    ).toBeInTheDocument();
    expect(
      within(highlights).getByText(mensajesEs.Portada.highlightEventos),
    ).toBeInTheDocument();
    expect(
      within(highlights).getByText(mensajesEs.Portada.highlightGraphql),
    ).toBeInTheDocument();
  });

  it("sin sesión, mantiene los botones de iniciar sesión y de invitado y menciona el tour", async () => {
    authMock.mockResolvedValue(null);
    await renderHome();

    expect(
      screen.getByRole("button", { name: mensajesEs.Portada.iniciarSesion }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: mensajesEs.Portada.entrarComoInvitado }),
    ).toBeInTheDocument();
    expect(screen.getByText(mensajesEs.Portada.tourNota)).toBeInTheDocument();
  });

  it("sin sesión, muestra visibles las credenciales demo de cliente y admin", async () => {
    authMock.mockResolvedValue(null);
    await renderHome();

    const nota = screen.getByRole("note");
    // Usuarios demo visibles (Requisito 13, criterio 4).
    expect(within(nota).getByText("cliente.demo")).toBeInTheDocument();
    expect(within(nota).getByText("admin.demo")).toBeInTheDocument();
    // La contraseña demo aparece para ambos roles.
    expect(within(nota).getAllByText("demo1234")).toHaveLength(2);
    // Ambos roles etiquetados.
    expect(
      within(nota).getByText(mensajesEs.Portada.demoRolCliente),
    ).toBeInTheDocument();
    expect(
      within(nota).getByText(mensajesEs.Portada.demoRolAdmin),
    ).toBeInTheDocument();
  });

  it("con sesión, saluda al usuario y ofrece ir al panel", async () => {
    authMock.mockResolvedValue({ user: { name: "Javiera González" } });
    await renderHome();

    expect(screen.getByText(/Javiera González/)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: mensajesEs.Portada.irAlPanel }),
    ).toBeInTheDocument();
    // Sin sesión-less landing content.
    expect(
      screen.queryByText(mensajesEs.Portada.heroDescripcion),
    ).not.toBeInTheDocument();
  });
});
