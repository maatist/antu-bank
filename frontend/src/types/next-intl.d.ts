import type { IDIOMA_POR_DEFECTO } from "@/i18n/config";
import type messages from "../../messages/es.json";

/**
 * Augmentación de tipos de next-intl (tarea 10.3): tipa las claves de mensajes a partir del
 * catálogo español (el idioma por defecto), de modo que `useTranslations`/`getTranslations`
 * ofrezcan autocompletado y verificación de claves inexistentes.
 */
declare module "next-intl" {
  interface AppConfig {
    Locale: typeof IDIOMA_POR_DEFECTO | "en";
    Messages: typeof messages;
  }
}
