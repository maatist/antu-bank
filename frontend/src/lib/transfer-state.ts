/**
 * Estado del formulario de transferencia, compartido entre la server action y el Client Component
 * que la consume vía `useActionState` (tarea 10.2, Requisito 10, criterio 3).
 *
 * Vive en su propio módulo (no en `actions.ts`) porque un archivo `"use server"` solo puede exportar
 * funciones async: los tipos y constantes deben residir fuera de esa frontera.
 *
 *   - `idle`    → aún no se ha enviado.
 *   - `success` → la transferencia fue aceptada (incluye estado e id devueltos por el backend).
 *   - `error`   → validación local o rechazo del backend (con mensaje y detalle).
 */
export type EstadoTransferencia =
  | { estado: "idle" }
  | {
      estado: "success";
      idTransferencia: string;
      estadoTransferencia: string;
      mensaje: string;
    }
  | { estado: "error"; mensaje: string; detalle?: string };

/** Estado inicial del formulario de transferencia. */
export const estadoTransferenciaInicial: EstadoTransferencia = { estado: "idle" };
