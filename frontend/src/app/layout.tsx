import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getLocale } from "next-intl/server";
import { localeDeFormato } from "@/i18n/config";
import "./globals.css";

export const metadata: Metadata = {
  title: "Antu Bank",
  description: "Banca digital chilena — panel de cliente",
};

/**
 * Layout raíz. Resuelve el idioma activo (cookie `NEXT_LOCALE`, español por defecto) y:
 *   - fija `<html lang>` al locale de formato correspondiente (`es-CL` / `en-US`) para
 *     accesibilidad y coherencia con el formateo de montos/fechas (Requisito 10, criterios 6 y 7);
 *   - expone los mensajes a los Client Components vía `NextIntlClientProvider`.
 */
export default async function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const idioma = await getLocale();

  return (
    <html lang={localeDeFormato(idioma)}>
      <body>
        <NextIntlClientProvider>{children}</NextIntlClientProvider>
      </body>
    </html>
  );
}
