import { describe, it, expect } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { VistaCuentas, VistaHistorial, type TraducePanel } from "./VistasPanel";
import type { Account, Transfer } from "@/lib/bff";

/**
 * Tests de las vistas de cuentas/saldos e historial del panel (tarea 10.5, Requisito 10, criterios
 * 2 y 7).
 *
 * Son componentes presentacionales puros: reciben datos y una función de traducción `t`. Aquí `t`
 * es un stub que devuelve la clave solicitada (así verificamos qué texto elige la vista sin acoplar
 * al catálogo), mientras que el formateo de montos/fechas usa el locale real `es-CL` para asertar la
 * convención chilena (miles con punto, CLP sin decimales).
 */
const t: TraducePanel = (clave) => clave;

/** Cuenta CLP con saldo, del titular. */
const CUENTA_CLP: Account = {
  id: "acc-11111111-2222",
  holderRut: "12.345.678-5",
  holderName: "Cliente Demo",
  accountType: "CORRIENTE",
  bank: "012",
  bankName: "Banco Estado",
  currency: "CLP",
  status: "ACTIVE",
  balance: { amountMinor: 1_250_000, amount: "1250000", currency: "CLP" },
};

describe("VistaCuentas", () => {
  it("muestra el mensaje vacío cuando no hay cuentas", () => {
    render(<VistaCuentas cuentas={[]} locale="es-CL" t={t} />);
    expect(screen.getByText("sinCuentas")).toBeInTheDocument();
  });

  it("lista las cuentas con su tipo, banco y saldo formateado en es-CL", () => {
    render(<VistaCuentas cuentas={[CUENTA_CLP]} locale="es-CL" t={t} />);

    expect(screen.getByText("CORRIENTE")).toBeInTheDocument();
    expect(screen.getByText(/Banco Estado/)).toBeInTheDocument();
    expect(screen.getByText("acc-11111111-2222")).toBeInTheDocument();

    // Saldo CLP: miles con punto, sin decimales (convención es-CL).
    const saldo = screen.getByText(/1\.250\.000/);
    const normalizado = saldo.textContent!.replace(/\u00a0/g, " ");
    expect(normalizado).toContain("$");
    expect(normalizado).not.toMatch(/[.,]\d{2}$/);
  });

  it("muestra el marcador de monto vacío cuando la cuenta no trae saldo", () => {
    const sinSaldo: Account = { ...CUENTA_CLP, balance: null };
    render(<VistaCuentas cuentas={[sinSaldo]} locale="es-CL" t={t} />);
    expect(screen.getByText("montoVacio")).toBeInTheDocument();
  });
});

describe("VistaHistorial", () => {
  const CUENTA_PROPIA = "acc-propia-0001";
  const CUENTA_AJENA = "acc-ajena-9999";

  const ENVIADA: Transfer = {
    id: "tx-enviada",
    status: "COMPLETED",
    sourceAccountId: CUENTA_PROPIA,
    destinationAccountId: CUENTA_AJENA,
    amountMinor: 30_000,
    currency: "CLP",
    createdAt: "2024-03-15T10:30:00Z",
  };

  const RECIBIDA: Transfer = {
    id: "tx-recibida",
    status: "COMPLETED",
    sourceAccountId: CUENTA_AJENA,
    destinationAccountId: CUENTA_PROPIA,
    amountMinor: 45_000,
    currency: "CLP",
    createdAt: "2024-03-16T09:00:00Z",
  };

  it("muestra el mensaje vacío cuando no hay movimientos", () => {
    render(
      <VistaHistorial
        transferencias={[]}
        idsCuentasPropias={new Set()}
        locale="es-CL"
        t={t}
      />,
    );
    expect(screen.getByText("sinMovimientos")).toBeInTheDocument();
  });

  it("marca como enviada el movimiento cuyo origen es una cuenta propia y muestra el monto en negativo", () => {
    render(
      <VistaHistorial
        transferencias={[ENVIADA]}
        idsCuentasPropias={new Set([CUENTA_PROPIA])}
        locale="es-CL"
        t={t}
      />,
    );

    const fila = screen.getByRole("row", { name: /tx-enviada|Enviada|movimientoEnviada/i });
    // Sin depender de un locator frágil: buscamos por la celda de movimiento.
    expect(screen.getByText("movimientoEnviada")).toBeInTheDocument();
    // Monto de una enviada se muestra en negativo.
    const monto = screen.getByText(/30\.000/);
    expect(monto.textContent).toMatch(/-|\(/);
    // Estado de la transferencia visible en un chip.
    expect(within(fila).getByText("COMPLETED")).toBeInTheDocument();
  });

  it("marca como recibida el movimiento cuyo origen no es propio y muestra el monto en positivo", () => {
    render(
      <VistaHistorial
        transferencias={[RECIBIDA]}
        idsCuentasPropias={new Set([CUENTA_PROPIA])}
        locale="es-CL"
        t={t}
      />,
    );

    expect(screen.getByText("movimientoRecibida")).toBeInTheDocument();
    const monto = screen.getByText(/45\.000/);
    expect(monto.textContent).not.toMatch(/-|\(/);
  });

  it("renderiza las cabeceras de la tabla y una fila por transferencia", () => {
    render(
      <VistaHistorial
        transferencias={[ENVIADA, RECIBIDA]}
        idsCuentasPropias={new Set([CUENTA_PROPIA])}
        locale="es-CL"
        t={t}
      />,
    );

    expect(screen.getByRole("columnheader", { name: "colFecha" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "colMonto" })).toBeInTheDocument();
    // 1 fila de cabecera + 2 de datos.
    expect(screen.getAllByRole("row")).toHaveLength(3);
  });
});
