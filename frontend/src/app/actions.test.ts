import { describe, it, expect, vi, beforeEach } from "vitest";
import type { EstadoTransferencia } from "@/lib/transfer-state";

/**
 * Tests del flujo de transferencia a nivel de server action (tarea 10.5, Requisito 10, criterio 3).
 *
 * `iniciarTransferenciaAction` valida la entrada localmente y, si es válida, llama al cliente REST
 * del gateway (`iniciarTransferencia`). Aquí verificamos esa lógica de forma aislada mockeando sus
 * colaboradores del lado servidor:
 *   - `@/auth`         → sesión y access token,
 *   - `@/lib/transfer` → cliente del gateway,
 *   - `next/cache`     → revalidación (no-op en test),
 *   - `next-intl/server` → traducciones (devolvemos la clave para asertar el mensaje elegido).
 *
 * Se cubren: rechazo por sesión ausente, cuentas faltantes, misma cuenta origen/destino, monto
 * inválido (no entero / no positivo), camino de éxito y propagación de rechazo del backend.
 */

const authMock = vi.fn();
const iniciarTransferenciaMock = vi.fn();
const revalidatePathMock = vi.fn();

vi.mock("@/auth", () => ({
  auth: () => authMock(),
  signIn: vi.fn(),
  signOut: vi.fn(),
}));

vi.mock("@/lib/transfer", () => ({
  iniciarTransferencia: (...args: unknown[]) => iniciarTransferenciaMock(...args),
}));

vi.mock("next/cache", () => ({
  revalidatePath: (...args: unknown[]) => revalidatePathMock(...args),
}));

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

// Traducciones: devolvemos la clave solicitada para verificar qué mensaje eligió la acción.
vi.mock("next-intl/server", () => ({
  getTranslations: async () => (clave: string) => clave,
}));

import { iniciarTransferenciaAction } from "./actions";

const ESTADO_INICIAL: EstadoTransferencia = { estado: "idle" };

/** Crea un FormData de transferencia con los campos indicados. */
function formularioTransferencia(campos: {
  sourceAccountId?: string;
  destinationAccountId?: string;
  amount?: string;
}): FormData {
  const fd = new FormData();
  if (campos.sourceAccountId !== undefined)
    fd.set("sourceAccountId", campos.sourceAccountId);
  if (campos.destinationAccountId !== undefined)
    fd.set("destinationAccountId", campos.destinationAccountId);
  if (campos.amount !== undefined) fd.set("amount", campos.amount);
  return fd;
}

describe("iniciarTransferenciaAction", () => {
  beforeEach(() => {
    authMock.mockReset();
    iniciarTransferenciaMock.mockReset();
    revalidatePathMock.mockReset();
    // Sesión válida por defecto; los tests que prueban el rechazo la sobreescriben.
    authMock.mockResolvedValue({ user: { name: "Demo" }, accessToken: "token-123" });
  });

  it("rechaza si no hay sesión de usuario", async () => {
    authMock.mockResolvedValue(null);

    const resultado = await iniciarTransferenciaAction(
      ESTADO_INICIAL,
      formularioTransferencia({
        sourceAccountId: "a",
        destinationAccountId: "b",
        amount: "1000",
      }),
    );

    expect(resultado).toEqual({ estado: "error", mensaje: "sesionExpirada" });
    expect(iniciarTransferenciaMock).not.toHaveBeenCalled();
  });

  it("rechaza si faltan las cuentas", async () => {
    const resultado = await iniciarTransferenciaAction(
      ESTADO_INICIAL,
      formularioTransferencia({ amount: "1000" }),
    );

    expect(resultado).toEqual({ estado: "error", mensaje: "seleccionaCuentas" });
    expect(iniciarTransferenciaMock).not.toHaveBeenCalled();
  });

  it("rechaza si la cuenta de origen y destino son iguales", async () => {
    const resultado = await iniciarTransferenciaAction(
      ESTADO_INICIAL,
      formularioTransferencia({
        sourceAccountId: "misma",
        destinationAccountId: "misma",
        amount: "1000",
      }),
    );

    expect(resultado).toEqual({ estado: "error", mensaje: "cuentasDistintas" });
    expect(iniciarTransferenciaMock).not.toHaveBeenCalled();
  });

  it("rechaza montos no enteros o no positivos", async () => {
    for (const monto of ["0", "-100", "1000.5", "abc", ""]) {
      const resultado = await iniciarTransferenciaAction(
        ESTADO_INICIAL,
        formularioTransferencia({
          sourceAccountId: "a",
          destinationAccountId: "b",
          amount: monto,
        }),
      );
      expect(resultado).toEqual({ estado: "error", mensaje: "montoInvalido" });
    }
    expect(iniciarTransferenciaMock).not.toHaveBeenCalled();
  });

  it("en el camino feliz llama al gateway, revalida el panel y devuelve éxito", async () => {
    iniciarTransferenciaMock.mockResolvedValue({
      ok: true,
      transferencia: { id: "tx-1", status: "COMPLETED" },
    });

    const resultado = await iniciarTransferenciaAction(
      ESTADO_INICIAL,
      formularioTransferencia({
        sourceAccountId: "origen",
        destinationAccountId: "destino",
        amount: "25000",
      }),
    );

    // Llama al cliente con los datos parseados y el access token de la sesión.
    expect(iniciarTransferenciaMock).toHaveBeenCalledWith(
      {
        sourceAccountId: "origen",
        destinationAccountId: "destino",
        amountMinor: 25000,
      },
      "token-123",
    );
    // Refresca saldos e historial.
    expect(revalidatePathMock).toHaveBeenCalledWith("/panel");
    expect(resultado).toEqual({
      estado: "success",
      idTransferencia: "tx-1",
      estadoTransferencia: "COMPLETED",
      mensaje: "procesada",
    });
  });

  it("propaga el rechazo del backend como estado de error", async () => {
    iniciarTransferenciaMock.mockResolvedValue({
      ok: false,
      mensaje: "La transferencia fue rechazada (estado 422).",
      detalle: "Fondos insuficientes",
    });

    const resultado = await iniciarTransferenciaAction(
      ESTADO_INICIAL,
      formularioTransferencia({
        sourceAccountId: "origen",
        destinationAccountId: "destino",
        amount: "25000",
      }),
    );

    expect(resultado).toEqual({
      estado: "error",
      mensaje: "La transferencia fue rechazada (estado 422).",
      detalle: "Fondos insuficientes",
    });
    expect(revalidatePathMock).not.toHaveBeenCalled();
  });
});
