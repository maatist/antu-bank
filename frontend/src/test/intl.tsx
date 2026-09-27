import type { ReactElement, ReactNode } from "react";
import { render, type RenderOptions } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import mensajesEs from "../../messages/es.json";
import mensajesEn from "../../messages/en.json";

/**
 * Utilidades de test para componentes que dependen de next-intl (tarea 10.5).
 *
 * Los Client Components de la app usan `useTranslations`/`useLocale`, que requieren un
 * `NextIntlClientProvider` en el árbol. Este helper envuelve el render con el proveedor y los
 * catálogos reales (`messages/es.json` / `messages/en.json`), de modo que las pruebas verifiquen los
 * strings traducidos que verá el usuario (es-CL por defecto). El locale es configurable para cubrir
 * el render en inglés.
 */
type Idioma = "es" | "en";

const CATALOGOS: Record<Idioma, Record<string, unknown>> = {
  es: mensajesEs,
  en: mensajesEn,
};

/**
 * Envuelve un árbol en el proveedor de i18n con el idioma indicado (español por defecto). Se pasa el
 * idioma (`"es"`/`"en"`) como `locale`, igual que en la app: el enfoque de next-intl "sin ruteo"
 * usa el idioma como locale y resuelve aparte el locale BCP-47 de formateo (`localeDeFormato`).
 */
export function ProveedorIntl({
  children,
  idioma = "es",
}: {
  children: ReactNode;
  idioma?: Idioma;
}) {
  return (
    <NextIntlClientProvider locale={idioma} messages={CATALOGOS[idioma]}>
      {children}
    </NextIntlClientProvider>
  );
}

/**
 * `render` de Testing Library que ya incluye el proveedor de i18n. Acepta el idioma a probar y las
 * opciones estándar de render.
 */
export function renderConIntl(
  ui: ReactElement,
  { idioma = "es", ...opciones }: RenderOptions & { idioma?: Idioma } = {},
) {
  return render(<ProveedorIntl idioma={idioma}>{ui}</ProveedorIntl>, opciones);
}
