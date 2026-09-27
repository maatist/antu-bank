import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";

/**
 * Configuración de Vitest para el frontend (tarea 10.5, Requisito 10).
 *
 * Framework de tests de componentes acordado en el diseño (§11 "Frontend: Testing Library"):
 * Vitest + @testing-library/react sobre un entorno jsdom. `vite-tsconfig-paths` respeta el alias
 * `@/*` de `tsconfig.json`, y `@vitejs/plugin-react` habilita JSX/TSX. El archivo de setup registra
 * los matchers de `@testing-library/jest-dom` y limpia el DOM entre pruebas.
 */
export default defineConfig({
  plugins: [react(), tsconfigPaths()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    // Solo los tests del frontend; se excluye el árbol de dependencias.
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
  },
});
