"use client";

import { useLocale, useTranslations } from "next-intl";
import { useTransition } from "react";
import { IDIOMAS } from "@/i18n/config";
import { cambiarIdioma } from "./actions";

/**
 * Selector de idioma (tarea 10.3, Requisito 10, criterio 6). Permite alternar entre español (por
 * defecto) e inglés.
 *
 * Es un Client Component porque reacciona a la interacción del usuario. Al elegir un idioma, invoca
 * la server action `cambiarIdioma`, que persiste la elección en la cookie `NEXT_LOCALE` y revalida
 * la vista; `useTransition` mantiene la UI responsiva y deshabilita el control mientras se aplica el
 * cambio.
 */
export function SelectorIdioma() {
  const t = useTranslations("SelectorIdioma");
  const idiomaActivo = useLocale();
  const [pendiente, iniciarTransicion] = useTransition();

  return (
    <label className="selector-idioma">
      <span className="selector-idioma__etiqueta">{t("etiqueta")}</span>
      <select
        value={idiomaActivo}
        disabled={pendiente}
        aria-label={t("etiqueta")}
        onChange={(evento) => {
          const nuevo = evento.target.value;
          iniciarTransicion(() => {
            cambiarIdioma(nuevo);
          });
        }}
      >
        {IDIOMAS.map((idioma) => (
          <option key={idioma} value={idioma}>
            {t(idioma)}
          </option>
        ))}
      </select>
    </label>
  );
}
