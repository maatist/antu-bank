/**
 * Utilidades de formateo por locale (tarea 10.2; conectadas al selector de idioma en la tarea
 * 10.3: las vistas pasan el locale del idioma activo — `es-CL` / `en-US` — resuelto vía
 * `localeDeFormato`). Español de Chile (`es-CL`) es el locale por defecto: miles con punto y CLP sin
 * decimales (ej. `$1.000.000`).
 *
 * Los montos llegan del BFF en minor units (`amountMinor`): para CLP son pesos enteros (scale 0),
 * para USD/UF son centésimos (scale 2). El formateo respeta la escala de cada moneda.
 */

/** Locale por defecto de la aplicación (Requisito 10, criterios 6 y 7). */
export const LOCALE_POR_DEFECTO = "es-CL";

/** Escala (decimales) por moneda, coherente con el dominio (`Currency`): CLP=0, USD/UF=2. */
const ESCALA_POR_MONEDA: Record<string, number> = {
  CLP: 0,
  USD: 2,
  UF: 2,
};

/** Devuelve la escala (nº de decimales) de una moneda; por defecto 2 si es desconocida. */
function escalaDeMoneda(moneda: string | null | undefined): number {
  if (!moneda) {
    return 2;
  }
  return ESCALA_POR_MONEDA[moneda.toUpperCase()] ?? 2;
}

/**
 * Formatea un monto en minor units a texto con la convención del locale.
 *
 * @param amountMinor monto en minor units, con signo.
 * @param moneda      código de moneda (CLP / USD / UF).
 * @param locale      locale BCP-47 (por defecto `es-CL`).
 */
export function formatearMonto(
  amountMinor: number,
  moneda: string | null | undefined,
  locale: string = LOCALE_POR_DEFECTO,
): string {
  const escala = escalaDeMoneda(moneda);
  const mayor = amountMinor / 10 ** escala;
  const codigo = (moneda ?? "CLP").toUpperCase();

  // UF no es una moneda ISO-4217; se formatea como número y se antepone la unidad.
  if (codigo === "UF") {
    const numero = new Intl.NumberFormat(locale, {
      minimumFractionDigits: escala,
      maximumFractionDigits: escala,
    }).format(mayor);
    return `UF ${numero}`;
  }

  try {
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: codigo,
      minimumFractionDigits: escala,
      maximumFractionDigits: escala,
    }).format(mayor);
  } catch {
    // Moneda no reconocida por Intl: degradar a número + código.
    const numero = new Intl.NumberFormat(locale, {
      minimumFractionDigits: escala,
      maximumFractionDigits: escala,
    }).format(mayor);
    return `${numero} ${codigo}`;
  }
}

/**
 * Formatea una fecha ISO-8601 (o epoch) a fecha y hora legible según el locale. Devuelve el valor
 * original si no es parseable, para no ocultar información en la vista.
 */
export function formatearFecha(
  iso: string | null | undefined,
  locale: string = LOCALE_POR_DEFECTO,
): string {
  if (!iso) {
    return "—";
  }
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(fecha);
}
