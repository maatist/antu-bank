import { describe, it, expect } from "vitest";
import { formatearMonto, formatearFecha, LOCALE_POR_DEFECTO } from "./format";

/**
 * Tests de las utilidades de formateo por locale (tarea 10.5, Requisito 10, criterio 7).
 *
 * Verifican la convención es-CL por defecto: miles con punto y CLP sin decimales (ej.
 * `$1.000.000`), además de USD/UF con escala 2 y el manejo de fechas inválidas/vacías.
 */
describe("formatearMonto", () => {
  it("formatea CLP sin decimales con miles separados por punto (es-CL)", () => {
    // 1.000.000 pesos → minor units == pesos enteros (scale 0).
    const texto = formatearMonto(1_000_000, "CLP");
    // Intl usa un espacio de ancho no separable tras el símbolo en es-CL; normalizamos.
    const normalizado = texto.replace(/\u00a0/g, " ");
    expect(normalizado).toContain("$");
    expect(normalizado).toContain("1.000.000");
    expect(normalizado).not.toContain(",");
  });

  it("no agrega decimales a montos CLP", () => {
    const texto = formatearMonto(25_000, "CLP");
    expect(texto).toContain("25.000");
    expect(texto).not.toMatch(/[.,]\d{2}$/);
  });

  it("formatea USD con dos decimales desde minor units (centavos)", () => {
    // 123456 centavos = 1234.56 USD.
    const texto = formatearMonto(123_456, "USD", "en-US");
    expect(texto).toContain("1,234.56");
  });

  it("formatea UF como número con la unidad antepuesta (no es moneda ISO)", () => {
    // 150000 minor units con scale 2 = 1500.00 UF.
    const texto = formatearMonto(150_000, "UF");
    expect(texto.startsWith("UF ")).toBe(true);
    expect(texto).toContain("1.500");
  });

  it("usa es-CL como locale por defecto", () => {
    expect(formatearMonto(1000, "CLP")).toBe(formatearMonto(1000, "CLP", LOCALE_POR_DEFECTO));
  });

  it("respeta el signo de montos negativos", () => {
    const texto = formatearMonto(-5000, "CLP");
    expect(texto).toContain("5.000");
    expect(texto).toMatch(/-|\(/); // signo menos o notación contable
  });
});

describe("formatearFecha", () => {
  it("devuelve un guion para valores vacíos", () => {
    expect(formatearFecha(null)).toBe("—");
    expect(formatearFecha(undefined)).toBe("—");
  });

  it("devuelve el valor original si no es parseable", () => {
    expect(formatearFecha("no-es-fecha")).toBe("no-es-fecha");
  });

  it("formatea una fecha ISO válida a texto legible", () => {
    const texto = formatearFecha("2024-03-15T10:30:00Z", "es-CL");
    // No fijamos el formato exacto (depende de zona horaria), pero debe incluir el año.
    expect(texto).toContain("2024");
    expect(texto).not.toBe("2024-03-15T10:30:00Z");
  });
});
