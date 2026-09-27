import { describe, it, expect, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderConIntl } from "@/test/intl";
import { FormularioTransferencia } from "./FormularioTransferencia";
import type { EstadoTransferencia } from "@/lib/transfer-state";
import type { Account } from "@/lib/bff";

/**
 * Test del flujo de transferencia de extremo a extremo del lado cliente (tarea 10.5, Requisito 10,
 * criterio 3): idle → enviando → resultado.
 *
 * Complementa a `FormularioTransferencia.test.tsx` (que verifica el render y los estados finales de
 * éxito/error) asertando el **estado intermedio "en vuelo"**: mientras la server action está
 * pendiente, el botón se deshabilita, marca `aria-busy` y se anuncia el mensaje "Enviando…". Para
 * ello la acción mockeada se resuelve con una promesa diferida que controlamos manualmente.
 */
const accionMock =
  vi.fn<
    (previo: EstadoTransferencia, formData: FormData) => Promise<EstadoTransferencia>
  >();

vi.mock("../actions", () => ({
  iniciarTransferenciaAction: (previo: EstadoTransferencia, formData: FormData) =>
    accionMock(previo, formData),
}));

const CUENTAS: Account[] = [
  {
    id: "acc-11111111-2222",
    holderRut: "12.345.678-5",
    holderName: "Cliente Demo",
    accountType: "CORRIENTE",
    bank: "012",
    bankName: "Banco Estado",
    currency: "CLP",
    status: "ACTIVE",
    balance: { amountMinor: 500_000, amount: "500000", currency: "CLP" },
  },
];

/** Crea una promesa que se resuelve externamente, para controlar cuándo termina la acción. */
function promesaDiferida<T>() {
  let resolver!: (valor: T) => void;
  const promesa = new Promise<T>((res) => {
    resolver = res;
  });
  return { promesa, resolver };
}

describe("Flujo de transferencia (idle → enviando → éxito)", () => {
  it("deshabilita el botón y anuncia 'Enviando…' mientras la acción está pendiente, y muestra el éxito al resolverse", async () => {
    const diferida = promesaDiferida<EstadoTransferencia>();
    accionMock.mockReturnValue(diferida.promesa);

    const user = userEvent.setup();
    renderConIntl(<FormularioTransferencia cuentas={CUENTAS} />, { idioma: "es" });

    // Estado inicial: botón habilitado, sin feedback de proceso.
    const boton = screen.getByRole("button", { name: "Transferir" });
    expect(boton).toBeEnabled();
    expect(screen.queryByText("Enviando la transferencia…")).not.toBeInTheDocument();

    // Completa el formulario y envía.
    await user.selectOptions(
      screen.getByLabelText("Cuenta de origen"),
      "acc-11111111-2222",
    );
    await user.type(
      screen.getByLabelText("Cuenta de destino (id)"),
      "acc-99999999-0000",
    );
    await user.type(screen.getByLabelText("Monto (CLP)"), "25000");
    await user.click(boton);

    // Estado en vuelo: el botón queda deshabilitado con aria-busy y se anuncia el proceso.
    await waitFor(() => {
      const enVuelo = screen.getByRole("button");
      expect(enVuelo).toBeDisabled();
      expect(enVuelo).toHaveAttribute("aria-busy", "true");
    });
    expect(screen.getByText("Enviando la transferencia…")).toBeInTheDocument();

    // Resolvemos la acción con éxito.
    diferida.resolver({
      estado: "success",
      idTransferencia: "abcdef12-3456",
      estadoTransferencia: "COMPLETED",
      mensaje: "La transferencia fue procesada.",
    });

    // Estado final: feedback de éxito con estado e id; el botón vuelve a habilitarse.
    const exito = await screen.findByText(/✓/);
    expect(exito).toHaveTextContent("COMPLETED");
    expect(exito).toHaveTextContent("abcdef12");
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Transferir" })).toBeEnabled(),
    );
    expect(screen.queryByText("Enviando la transferencia…")).not.toBeInTheDocument();
  });
});
