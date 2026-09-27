/**
 * Setup global de Vitest (tarea 10.5).
 *
 * - Registra los matchers de `@testing-library/jest-dom` (p. ej. `toBeInTheDocument`,
 *   `toBeDisabled`), disponibles en todas las pruebas.
 * - Limpia el DOM tras cada test para aislar los renders de Testing Library.
 */
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
  cleanup();
});
