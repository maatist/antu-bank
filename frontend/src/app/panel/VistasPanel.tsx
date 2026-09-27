import { formatearMonto, formatearFecha } from "@/lib/format";
import type { Account, Transfer } from "@/lib/bff";

/**
 * Vistas presentacionales del panel: cuentas/saldos e historial de transferencias (tarea 10.2 /
 * 10.5, Requisito 10, criterios 2 y 7).
 *
 * Se extraen del `page.tsx` como funciones puras y **síncronas** para poder probarlas con Testing
 * Library sin montar un Server Component asíncrono ni el proveedor de next-intl. En vez de resolver
 * las traducciones internamente (`getTranslations`), reciben una función `t` por props: el panel le
 * pasa su `t` de servidor y los tests le pasan un stub. Así se verifica el render de saldos e
 * historial (formateo `es-CL`, movimiento enviado/recibido, estados vacíos) de forma aislada.
 */

/**
 * Claves del namespace `Panel` que estas vistas traducen. Se enumeran explícitamente para que el
 * tipo de `t` sea compatible tanto con el traductor tipado de next-intl (que devuelve `getTranslations("Panel")`)
 * como con un stub simple en los tests.
 */
export type ClavePanel =
  | "sinCuentas"
  | "cuentaGenerica"
  | "montoVacio"
  | "sinMovimientos"
  | "colFecha"
  | "colMovimiento"
  | "colEstado"
  | "colMonto"
  | "movimientoEnviada"
  | "movimientoRecibida";

/** Función de traducción del namespace `Panel` (equivalente a lo que devuelve `getTranslations`). */
export type TraducePanel = (clave: ClavePanel) => string;

/**
 * Vista de cuentas y saldos del titular. Los montos se formatean con el locale activo (`es-CL` por
 * defecto). Si no hay cuentas, muestra el mensaje vacío correspondiente.
 */
export function VistaCuentas({
  cuentas,
  locale,
  t,
}: {
  cuentas: Account[];
  locale: string;
  t: TraducePanel;
}) {
  if (cuentas.length === 0) {
    return <p className="texto-tenue">{t("sinCuentas")}</p>;
  }
  return (
    <ul className="lista-cuentas">
      {cuentas.map((cuenta) => (
        <li key={cuenta.id} className="cuenta">
          <div>
            <span className="cuenta-tipo">
              {cuenta.accountType ?? t("cuentaGenerica")}
            </span>
            <span className="texto-tenue">
              {cuenta.bankName ?? cuenta.bank ?? ""} · {cuenta.currency ?? "CLP"}
            </span>
            <span className="cuenta-id texto-tenue">{cuenta.id}</span>
          </div>
          <div className="cuenta-saldo">
            {cuenta.balance
              ? formatearMonto(
                  cuenta.balance.amountMinor,
                  cuenta.balance.currency,
                  locale,
                )
              : t("montoVacio")}
          </div>
        </li>
      ))}
    </ul>
  );
}

/**
 * Vista del historial de transferencias. Marca cada movimiento como enviado o recibido según si la
 * cuenta origen pertenece al titular, para que el signo del monto tenga sentido en la vista. Fechas
 * y montos se formatean con el locale activo.
 */
export function VistaHistorial({
  transferencias,
  idsCuentasPropias,
  locale,
  t,
}: {
  transferencias: Transfer[];
  idsCuentasPropias: Set<string>;
  locale: string;
  t: TraducePanel;
}) {
  if (transferencias.length === 0) {
    return <p className="texto-tenue">{t("sinMovimientos")}</p>;
  }
  return (
    <table className="tabla-historial">
      <thead>
        <tr>
          <th scope="col">{t("colFecha")}</th>
          <th scope="col">{t("colMovimiento")}</th>
          <th scope="col">{t("colEstado")}</th>
          <th scope="col" className="alinear-derecha">
            {t("colMonto")}
          </th>
        </tr>
      </thead>
      <tbody>
        {transferencias.map((tr) => {
          const enviada = tr.sourceAccountId
            ? idsCuentasPropias.has(tr.sourceAccountId)
            : false;
          const signo = enviada ? -1 : 1;
          return (
            <tr key={tr.id}>
              <td>{formatearFecha(tr.createdAt, locale)}</td>
              <td>
                {enviada ? t("movimientoEnviada") : t("movimientoRecibida")}
              </td>
              <td>
                <span className="estado-chip">{tr.status ?? t("montoVacio")}</span>
              </td>
              <td className="alinear-derecha">
                {formatearMonto(signo * tr.amountMinor, tr.currency, locale)}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
