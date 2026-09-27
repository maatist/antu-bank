"use client";

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";

/**
 * Tour guiado para reclutadores en el panel (tarea 10.4 / 10.5, Requisito 10, criterio 5).
 *
 * Es un stepper ligero y propio (sin dependencias externas) que resalta secuencialmente las
 * secciones clave del panel: cuentas y saldos, historial e inicio de una transferencia. Cada paso
 * apunta a un elemento del DOM mediante el atributo `data-tour` y muestra un globo con el texto
 * localizado (es por defecto / en) y controles anterior / siguiente / finalizar.
 *
 * Detalles de UX y accesibilidad:
 *   - Se recalcula la posición del elemento resaltado ante scroll y resize.
 *   - `Escape` cierra el tour; el foco se lleva al globo al abrir.
 *   - Una vez completado o cerrado, se recuerda en `localStorage` para no repetirlo en cada visita;
 *     el reclutador puede reabrirlo con el botón flotante "Tour".
 */

/** Clave de `localStorage` para recordar que el tour ya se vio. */
const CLAVE_TOUR_VISTO = "antu-bank:tour-visto";

/** Identificadores de los pasos; cada uno referencia un `data-tour` en el panel. */
const PASOS = ["cuentas", "transferir", "historial"] as const;
type PasoTour = (typeof PASOS)[number];

/** Rectángulo del elemento resaltado (coordenadas de viewport). */
type Recuadro = { top: number; left: number; width: number; height: number };

export function TourGuiado() {
  const t = useTranslations("Tour");
  const [abierto, setAbierto] = useState(false);
  const [indice, setIndice] = useState(0);
  const [recuadro, setRecuadro] = useState<Recuadro | null>(null);
  const globoRef = useRef<HTMLDivElement | null>(null);

  const pasoActual: PasoTour = PASOS[indice];

  // Al montar, abre el tour automáticamente la primera vez (si no se ha visto antes).
  useEffect(() => {
    try {
      if (window.localStorage.getItem(CLAVE_TOUR_VISTO) !== "1") {
        setAbierto(true);
      }
    } catch {
      // Si localStorage no está disponible, simplemente no auto-abrimos.
    }
  }, []);

  const cerrar = useCallback(() => {
    setAbierto(false);
    setIndice(0);
    try {
      window.localStorage.setItem(CLAVE_TOUR_VISTO, "1");
    } catch {
      // Ignorar si localStorage no está disponible.
    }
  }, []);

  const abrir = useCallback(() => {
    setIndice(0);
    setAbierto(true);
  }, []);

  const siguiente = useCallback(() => {
    setIndice((i) => Math.min(i + 1, PASOS.length - 1));
  }, []);

  const anterior = useCallback(() => {
    setIndice((i) => Math.max(i - 1, 0));
  }, []);

  // Recalcula la posición del elemento resaltado del paso actual.
  const recalcular = useCallback(() => {
    if (!abierto) {
      return;
    }
    const objetivo = document.querySelector<HTMLElement>(
      `[data-tour="${pasoActual}"]`,
    );
    if (!objetivo) {
      setRecuadro(null);
      return;
    }
    const r = objetivo.getBoundingClientRect();
    setRecuadro({ top: r.top, left: r.left, width: r.width, height: r.height });
  }, [abierto, pasoActual]);

  // Al cambiar de paso, lleva el elemento a la vista y recalcula su posición.
  useLayoutEffect(() => {
    if (!abierto) {
      return;
    }
    const objetivo = document.querySelector<HTMLElement>(
      `[data-tour="${pasoActual}"]`,
    );
    objetivo?.scrollIntoView({ behavior: "smooth", block: "center" });
    recalcular();
  }, [abierto, pasoActual, recalcular]);

  // Reposiciona ante scroll/resize mientras el tour está abierto.
  useEffect(() => {
    if (!abierto) {
      return;
    }
    globoRef.current?.focus();
    window.addEventListener("scroll", recalcular, true);
    window.addEventListener("resize", recalcular);
    const alPresionar = (evento: KeyboardEvent) => {
      if (evento.key === "Escape") {
        cerrar();
      }
    };
    window.addEventListener("keydown", alPresionar);
    return () => {
      window.removeEventListener("scroll", recalcular, true);
      window.removeEventListener("resize", recalcular);
      window.removeEventListener("keydown", alPresionar);
    };
  }, [abierto, recalcular, cerrar]);

  // Botón flotante para (re)abrir el tour cuando está cerrado.
  if (!abierto) {
    return (
      <button
        type="button"
        className="tour-boton-flotante"
        onClick={abrir}
        aria-label={t("reabrir")}
      >
        {t("reabrir")}
      </button>
    );
  }

  const esUltimo = indice === PASOS.length - 1;
  const esPrimero = indice === 0;

  // Posición del globo: bajo el elemento resaltado si hay recuadro; centrado si no.
  const estiloGlobo: React.CSSProperties = recuadro
    ? {
        position: "fixed",
        top: Math.min(recuadro.top + recuadro.height + 12, window.innerHeight - 220),
        left: Math.max(12, Math.min(recuadro.left, window.innerWidth - 340)),
      }
    : {
        position: "fixed",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
      };

  return (
    <div className="tour-superposicion" role="presentation" onClick={cerrar}>
      {/* Recuadro de resaltado sobre el elemento del paso actual. */}
      {recuadro && (
        <div
          className="tour-resaltado"
          style={{
            top: recuadro.top - 6,
            left: recuadro.left - 6,
            width: recuadro.width + 12,
            height: recuadro.height + 12,
          }}
          aria-hidden="true"
        />
      )}

      <div
        ref={globoRef}
        className="tour-globo"
        style={estiloGlobo}
        role="dialog"
        aria-modal="true"
        aria-labelledby="tour-titulo"
        aria-describedby="tour-cuerpo"
        tabIndex={-1}
        // Evita que el clic dentro del globo cierre el tour (burbujea a la superposición).
        onClick={(evento) => evento.stopPropagation()}
      >
        <p className="tour-globo__paso texto-tenue">
          {t("progreso", { actual: indice + 1, total: PASOS.length })}
        </p>
        <h3 id="tour-titulo" className="tour-globo__titulo">
          {t(`${pasoActual}.titulo`)}
        </h3>
        <p id="tour-cuerpo" className="tour-globo__cuerpo">
          {t(`${pasoActual}.cuerpo`)}
        </p>
        <div className="tour-globo__acciones">
          <button
            type="button"
            className="boton boton--secundario tour-globo__omitir"
            onClick={cerrar}
          >
            {t("omitir")}
          </button>
          <div className="tour-globo__navegacion">
            {!esPrimero && (
              <button
                type="button"
                className="boton boton--secundario"
                onClick={anterior}
              >
                {t("anterior")}
              </button>
            )}
            <button
              type="button"
              className="boton"
              onClick={esUltimo ? cerrar : siguiente}
            >
              {esUltimo ? t("finalizar") : t("siguiente")}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
