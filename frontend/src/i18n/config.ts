/**
 * Configuración base de i18n para el frontend (tarea 10.3, Requisito 10, criterios 6 y 7).
 *
 * La app es bilingüe español/inglés con **español (es-CL) por defecto**. Se usa el enfoque de
 * next-intl "sin ruteo por locale" (las URLs no cambian): el idioma activo se persiste en una
 * cookie y se resuelve por request en Server Components.
 */

/** Idiomas soportados por la aplicación. El primero es el idioma por defecto. */
export const IDIOMAS = ["es", "en"] as const;

/** Tipo de un idioma soportado (`"es"` | `"en"`). */
export type Idioma = (typeof IDIOMAS)[number];

/** Idioma por defecto (español). */
export const IDIOMA_POR_DEFECTO: Idioma = "es";

/** Nombre de la cookie donde se persiste el idioma elegido por el usuario. */
export const COOKIE_IDIOMA = "NEXT_LOCALE";

/**
 * Locale BCP-47 que se usa para formatear montos y fechas según el idioma activo. Para español se
 * usa `es-CL` (miles con punto, CLP sin decimales, ej. `$1.000.000`); para inglés, `en-US`.
 */
const LOCALE_FORMATO: Record<Idioma, string> = {
  es: "es-CL",
  en: "en-US",
};

/** Indica si un valor arbitrario corresponde a un idioma soportado. */
export function esIdiomaSoportado(valor: string | undefined | null): valor is Idioma {
  return typeof valor === "string" && (IDIOMAS as readonly string[]).includes(valor);
}

/** Devuelve el locale BCP-47 de formateo asociado a un idioma (por defecto `es-CL`). */
export function localeDeFormato(idioma: string | undefined | null): string {
  return esIdiomaSoportado(idioma) ? LOCALE_FORMATO[idioma] : LOCALE_FORMATO[IDIOMA_POR_DEFECTO];
}
