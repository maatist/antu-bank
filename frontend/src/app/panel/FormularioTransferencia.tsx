"use client";

import { useActionState } from "react";
import { useTranslations } from "next-intl";
import { iniciarTransferenciaAction } from "../actions";
import { estadoTransferenciaInicial } from "@/lib/transfer-state";
import type { Account } from "@/lib/bff";

/**
 * Formulario para iniciar una transferencia con feedback de estado del proceso (tarea 10.2,
 * Requisito 10, criterio 3).
 *
 * Es un Client Component porque usa `useActionState`: envía a la server action
 * `iniciarTransferenciaAction` y muestra el estado del envío (enviando / éxito / error). Al
 * aceptarse, la action revalida `/panel`, por lo que saldos e historial se actualizan solos.
 *
 * Los textos se localizan (es por defecto / en) vía `useTranslations` (tarea 10.3). Las cuentas de
 * origen se limitan a las del titular; el destino se ingresa por id.
 */
export function FormularioTransferencia({ cuentas }: { cuentas: Account[] }) {
  const t = useTranslations("FormularioTransferencia");
  const [estado, formAction, enviando] = useActionState(
    iniciarTransferenciaAction,
    estadoTransferenciaInicial,
  );

  return (
    <form action={formAction} className="formulario" aria-label={t("titulo")}>
      <div className="campo">
        <label htmlFor="sourceAccountId">{t("cuentaOrigen")}</label>
        <select id="sourceAccountId" name="sourceAccountId" required defaultValue="">
          <option value="" disabled>
            {t("seleccionaCuenta")}
          </option>
          {cuentas.map((cuenta) => (
            <option key={cuenta.id} value={cuenta.id}>
              {(cuenta.accountType ?? t("cuentaGenerica"))} ·{" "}
              {cuenta.bankName ?? cuenta.bank ?? ""} · {cuenta.id.slice(0, 8)}…
            </option>
          ))}
        </select>
      </div>

      <div className="campo">
        <label htmlFor="destinationAccountId">{t("cuentaDestino")}</label>
        <input
          id="destinationAccountId"
          name="destinationAccountId"
          type="text"
          inputMode="text"
          placeholder={t("cuentaDestinoPlaceholder")}
          required
        />
      </div>

      <div className="campo">
        <label htmlFor="amount">{t("monto")}</label>
        <input
          id="amount"
          name="amount"
          type="number"
          min={1}
          step={1}
          inputMode="numeric"
          placeholder={t("montoPlaceholder")}
          required
        />
      </div>

      <button className="boton" type="submit" disabled={enviando} aria-busy={enviando}>
        {enviando ? t("procesando") : t("transferir")}
      </button>

      {/* Feedback de estado del proceso. `aria-live` anuncia el resultado a lectores de pantalla. */}
      <div className="feedback" aria-live="polite">
        {enviando && <p className="feedback--proceso">{t("enviando")}</p>}
        {!enviando && estado.estado === "success" && (
          <p className="feedback--exito">
            {t("exito", {
              mensaje: estado.mensaje,
              estado: estado.estadoTransferencia,
              id: estado.idTransferencia.slice(0, 8),
            })}
          </p>
        )}
        {!enviando && estado.estado === "error" && (
          <p className="feedback--error">
            {t("error", {
              mensaje: estado.mensaje,
              detalle: estado.detalle ? ` (${estado.detalle})` : "",
            })}
          </p>
        )}
      </div>
    </form>
  );
}
