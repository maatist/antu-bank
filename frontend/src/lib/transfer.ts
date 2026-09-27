import "server-only";

/**
 * Cliente REST para iniciar transferencias vía el api-gateway (tarea 10.2, Requisito 10, criterio 3).
 *
 * El gateway enruta `POST /api/transfers` a transfer-service (`POST /transfers`), que exige el
 * header `Idempotency-Key`: un reintento con la misma clave nunca duplica el movimiento
 * (Requisito 4). El cuerpo lleva `sourceAccountId`, `destinationAccountId` y `amountMinor` (CLP en
 * pesos enteros). Ver services/transfer-service/.../api/CreateTransferRequest.java.
 *
 * Vive SOLO en el servidor: el access token de Keycloak se propaga como Bearer y no llega al
 * navegador.
 */

const GATEWAY_URL =
  process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:8080";

/** Endpoint de transferencias del gateway (enruta a transfer-service). */
const TRANSFERS_ENDPOINT = `${GATEWAY_URL}/api/transfers`;

/** Datos mínimos para iniciar una transferencia en CLP. */
export interface DatosTransferencia {
  sourceAccountId: string;
  destinationAccountId: string;
  /** Monto en minor units (CLP: pesos enteros). */
  amountMinor: number;
}

/** Respuesta REST de transfer-service (TransferResponse). */
export interface ResultadoTransferencia {
  id: string;
  status: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amountMinor: number;
  currency: string;
  createdAt?: string;
}

/** Resultado de iniciar una transferencia: éxito con datos o error con detalle localizado. */
export type RespuestaIniciarTransferencia =
  | { ok: true; transferencia: ResultadoTransferencia }
  | { ok: false; mensaje: string; detalle?: string };

/** Cuerpo ProblemDetail (RFC 7807) que devuelven los servicios ante error. */
interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}

/**
 * Inicia una transferencia contra el gateway con una `Idempotency-Key` generada por solicitud.
 *
 * @param datos        cuentas y monto.
 * @param accessToken  access token de Keycloak (Bearer).
 * @param idempotencyKey clave de idempotencia; si no se entrega, se genera una nueva (UUID).
 */
export async function iniciarTransferencia(
  datos: DatosTransferencia,
  accessToken: string | undefined,
  idempotencyKey: string = crypto.randomUUID(),
): Promise<RespuestaIniciarTransferencia> {
  let respuesta: Response;
  try {
    respuesta = await fetch(TRANSFERS_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": idempotencyKey,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify(datos),
      cache: "no-store",
    });
  } catch (causa) {
    return {
      ok: false,
      mensaje: "No se pudo conectar con el gateway para procesar la transferencia.",
      detalle: causa instanceof Error ? causa.message : String(causa),
    };
  }

  if (respuesta.ok) {
    const transferencia = (await respuesta.json()) as ResultadoTransferencia;
    return { ok: true, transferencia };
  }

  // Error: intentar leer ProblemDetail (RFC 7807) para un mensaje localizado del backend.
  let detalle: string | undefined;
  try {
    const problema = (await respuesta.json()) as ProblemDetail;
    detalle = problema.detail ?? problema.title;
  } catch {
    detalle = await respuesta.text().catch(() => undefined);
  }

  return {
    ok: false,
    mensaje: `La transferencia fue rechazada (estado ${respuesta.status}).`,
    detalle,
  };
}
