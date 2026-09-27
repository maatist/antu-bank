import { cookies } from "next/headers";
import { getRequestConfig } from "next-intl/server";
import {
  COOKIE_IDIOMA,
  IDIOMA_POR_DEFECTO,
  esIdiomaSoportado,
  type Idioma,
} from "./config";

/**
 * Configuración por request de next-intl (tarea 10.3). Se ejecuta durante el render de Server
 * Components, por lo que puede leer la cookie con el idioma elegido.
 *
 * Al no usar ruteo por locale, el idioma se resuelve desde la cookie `NEXT_LOCALE`; si no existe o
 * es inválido, se cae al idioma por defecto (español). Los mensajes se cargan del catálogo JSON
 * correspondiente.
 */
export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const valorCookie = cookieStore.get(COOKIE_IDIOMA)?.value;
  const locale: Idioma = esIdiomaSoportado(valorCookie)
    ? valorCookie
    : IDIOMA_POR_DEFECTO;

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
  };
});
