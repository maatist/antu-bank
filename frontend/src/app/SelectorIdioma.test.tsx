import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderConIntl } from "@/test/intl";
import { SelectorIdioma } from "./SelectorIdioma";

/**
 * Tests del selector de idioma (tarea 10.5, Requisito 10, criterio 6).
 *
 * `cambiarIdioma` es una server action ("use server"): la mockeamos para verificar el
 * comportamiento del Client Component (render de strings traducidos y despacho al elegir idioma) sin
 * tocar cookies ni revalidación de Next.
 */
const cambiarIdiomaMock = vi.fn();
vi.mock("./actions", () => ({
  cambiarIdioma: (idioma: string) => cambiarIdiomaMock(idioma),
}));

describe("SelectorIdioma", () => {
  beforeEach(() => {
    cambiarIdiomaMock.mockReset();
  });

  it("renderiza la etiqueta y las opciones traducidas en español (por defecto)", () => {
    renderConIntl(<SelectorIdioma />, { idioma: "es" });

    // Etiqueta y aria-label del control provienen del catálogo es.
    expect(screen.getByLabelText("Idioma")).toBeInTheDocument();
    // Opciones con nombres de idioma.
    expect(screen.getByRole("option", { name: "Español" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "English" })).toBeInTheDocument();
  });

  it("renderiza la etiqueta traducida en inglés cuando el locale es en", () => {
    renderConIntl(<SelectorIdioma />, { idioma: "en" });
    expect(screen.getByLabelText("Language")).toBeInTheDocument();
  });

  it("invoca la server action cambiarIdioma con el idioma elegido", async () => {
    const user = userEvent.setup();
    renderConIntl(<SelectorIdioma />, { idioma: "es" });

    await user.selectOptions(screen.getByLabelText("Idioma"), "en");

    expect(cambiarIdiomaMock).toHaveBeenCalledWith("en");
  });
});
