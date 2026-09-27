import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderConIntl } from "@/test/intl";
import { FormularioTransferencia } from "./FormularioTransferencia";
import type { EstadoTransferencia } from "@/lib/transfer-state";
import type { Account } from "@/lib/bff";

/**
 * Tests del formulario de transferencia (tarea 10.5, Requisito 10, criterio 3).
 *
 * El componente usa `useActionState(iniciarTransferenciaAction, ...)`. Mockeamos la server action
 * (un archivo "use server" no es ejecutable directamente en jsdom) para controlar el estado de
 * feedback devuelto y verificar que el formulario:
 *   - renderiza los campos y las cuentas de origen,
 *   - muestra el feedback de éxito y de error según el `EstadoTransferencia`,
 *   - despacha la acción con los datos del formulario al enviar.
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
    balance: null,
  },
];

describe("FormularioTransferencia", () => {
  it("renderiza los campos y las cuentas de origen traducidos (es)", () => {
    accionMock.mockResolvedValue({ estado: "idle" });
    renderConIntl(<FormularioTransferencia cuentas={CUENTAS} />, { idioma: "es" });

    expect(screen.getByLabelText("Cuenta de origen")).toBeInTheDocument();
    expect(screen.getByLabelText("Cuenta de destino (id)")).toBeInTheDocument();
    expect(screen.getByLabelText("Monto (CLP)")).toBeInTheDocument();
    // La cuenta del titular aparece como opción de origen.
    expect(
      screen.getByRole("option", { name: /Banco Estado/ }),
    ).toBeInTheDocument();
    // Botón en estado inicial (no enviando).
    expect(screen.getByRole("button", { name: "Transferir" })).toBeEnabled();
  });

  it("no muestra feedback de éxito ni error en estado idle", () => {
    accionMock.mockResolvedValue({ estado: "idle" });
    renderConIntl(<FormularioTransferencia cuentas={CUENTAS} />, { idioma: "es" });

    expect(screen.queryByText(/✓/)).not.toBeInTheDocument();
    expect(screen.queryByText(/✕/)).not.toBeInTheDocument();
  });

  it("despacha la server action con los datos ingresados al enviar", async () => {
    accionMock.mockResolvedValue({ estado: "idle" });
    const user = userEvent.setup();
    renderConIntl(<FormularioTransferencia cuentas={CUENTAS} />, { idioma: "es" });

    await user.selectOptions(
      screen.getByLabelText("Cuenta de origen"),
      "acc-11111111-2222",
    );
    await user.type(
      screen.getByLabelText("Cuenta de destino (id)"),
      "acc-99999999-0000",
    );
    await user.type(screen.getByLabelText("Monto (CLP)"), "25000");
    await user.click(screen.getByRole("button", { name: "Transferir" }));

    expect(accionMock).toHaveBeenCalledTimes(1);
    const formData = accionMock.mock.calls[0][1];
    expect(formData.get("sourceAccountId")).toBe("acc-11111111-2222");
    expect(formData.get("destinationAccountId")).toBe("acc-99999999-0000");
    expect(formData.get("amount")).toBe("25000");
  });

  it("muestra el feedback de éxito devuelto por la acción", async () => {
    accionMock.mockResolvedValue({
      estado: "success",
      idTransferencia: "abcdef12-3456",
      estadoTransferencia: "COMPLETED",
      mensaje: "La transferencia fue procesada.",
    });
    const user = userEvent.setup();
    renderConIntl(<FormularioTransferencia cuentas={CUENTAS} />, { idioma: "es" });

    await user.selectOptions(
      screen.getByLabelText("Cuenta de origen"),
      "acc-11111111-2222",
    );
    await user.type(
      screen.getByLabelText("Cuenta de destino (id)"),
      "acc-99999999-0000",
    );
    await user.type(screen.getByLabelText("Monto (CLP)"), "25000");
    await user.click(screen.getByRole("button", { name: "Transferir" }));

    // El feedback de éxito incluye el símbolo ✓, el estado y el id (recortado a 8).
    const exito = await screen.findByText(/✓/);
    expect(exito).toHaveTextContent("COMPLETED");
    expect(exito).toHaveTextContent("abcdef12");
  });

  it("muestra el feedback de error devuelto por la acción", async () => {
    accionMock.mockResolvedValue({
      estado: "error",
      mensaje: "La cuenta de origen y la de destino deben ser distintas.",
    });
    const user = userEvent.setup();
    renderConIntl(<FormularioTransferencia cuentas={CUENTAS} />, { idioma: "es" });

    await user.selectOptions(
      screen.getByLabelText("Cuenta de origen"),
      "acc-11111111-2222",
    );
    await user.type(
      screen.getByLabelText("Cuenta de destino (id)"),
      "acc-99999999-0000",
    );
    await user.type(screen.getByLabelText("Monto (CLP)"), "25000");
    await user.click(screen.getByRole("button", { name: "Transferir" }));

    const error = await screen.findByText(/✕/);
    expect(error).toHaveTextContent("distintas");
  });
});
