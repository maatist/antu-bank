import "server-only";

/**
 * Cliente del BFF GraphQL de Antu Bank (tarea 10.2, Requisito 10, criterio 2).
 *
 * El api-gateway expone una capa GraphQL (Spring for GraphQL, tarea 9.3) cuya query raíz `me`
 * agrega en una sola respuesta las cuentas del titular, su saldo derivado (ledger-service) y el
 * historial de transferencias (transfer-service). Ver el schema en:
 *   services/api-gateway/src/main/resources/graphql/schema.graphqls
 *
 * Este cliente vive SOLO en el servidor (`server-only`): así el `access_token` de Keycloak nunca
 * llega al navegador. El token se toma de la sesión de Auth.js (server-side) y se propaga como
 * `Authorization: Bearer` al gateway, que a su vez lo propaga a los servicios internos.
 *
 * Identidad del cliente: los tokens demo de Keycloak NO mapean el atributo `rut` como claim del
 * JWT, por lo que la query `me(rut: "...")` recibe el RUT explícito (ver `resolverRut` en auth).
 */

/** URL del gateway. `NEXT_PUBLIC_GATEWAY_URL` apunta al api-gateway (por defecto local :8080). */
const GATEWAY_URL =
  process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:8080";

/** Endpoint GraphQL del BFF en el gateway. */
const GRAPHQL_ENDPOINT = `${GATEWAY_URL}/graphql`;

/** Saldo/monto de dinero en una moneda (tipo GraphQL `Money`). */
export interface Money {
  /** Monto en minor units con signo (para CLP, pesos enteros). */
  amountMinor: number;
  /** Monto en unidades mayores con signo, como cadena para no perder precisión. */
  amount: string;
  /** Moneda (CLP / USD / UF). */
  currency: string;
}

/** Cuenta bancaria del titular con su saldo derivado (tipo GraphQL `Account`). */
export interface Account {
  id: string;
  holderRut: string;
  holderName: string | null;
  accountType: string | null;
  bank: string | null;
  bankName: string | null;
  currency: string | null;
  status: string | null;
  balance: Money | null;
}

/** Transferencia del historial del cliente (tipo GraphQL `Transfer`). */
export interface Transfer {
  id: string;
  status: string | null;
  sourceAccountId: string | null;
  destinationAccountId: string | null;
  amountMinor: number;
  currency: string | null;
  createdAt: string | null;
}

/** Agregado del cliente: identidad + cuentas + historial (tipo GraphQL `Me`). */
export interface Me {
  rut: string;
  accounts: Account[];
  transfers: Transfer[];
}

/** Error de comunicación o de negocio al consultar el BFF. */
export class BffError extends Error {
  constructor(
    message: string,
    readonly detalles?: string,
  ) {
    super(message);
    this.name = "BffError";
  }
}

/**
 * Query `me`: trae cuentas (con saldo anidado) e historial de transferencias en una sola llamada.
 * Los montos viajan en minor units (`amountMinor`) y también como `amount` (unidades mayores) para
 * el formateo por locale que refina la tarea 10.3.
 */
const ME_QUERY = /* GraphQL */ `
  query Me($rut: String) {
    me(rut: $rut) {
      rut
      accounts {
        id
        holderRut
        holderName
        accountType
        bank
        bankName
        currency
        status
        balance {
          amountMinor
          amount
          currency
        }
      }
      transfers {
        id
        status
        sourceAccountId
        destinationAccountId
        amountMinor
        currency
        createdAt
      }
    }
  }
`;

interface GraphQlError {
  message: string;
}

interface GraphQlResponse<T> {
  data?: T;
  errors?: GraphQlError[];
}

/**
 * Ejecuta una operación GraphQL contra el BFF, propagando el Bearer token.
 *
 * @param query        documento GraphQL.
 * @param variables    variables de la operación.
 * @param accessToken  access token de Keycloak (de la sesión server-side).
 */
async function ejecutarGraphQl<T>(
  query: string,
  variables: Record<string, unknown>,
  accessToken: string | undefined,
): Promise<T> {
  let respuesta: Response;
  try {
    respuesta = await fetch(GRAPHQL_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify({ query, variables }),
      // Datos por cliente y sesión: nunca cachear la respuesta del BFF.
      cache: "no-store",
    });
  } catch (causa) {
    throw new BffError(
      "No se pudo conectar con el gateway/BFF.",
      causa instanceof Error ? causa.message : String(causa),
    );
  }

  if (!respuesta.ok) {
    throw new BffError(
      `El BFF respondió con estado ${respuesta.status}.`,
      await respuesta.text().catch(() => undefined),
    );
  }

  const cuerpo = (await respuesta.json()) as GraphQlResponse<T>;
  if (cuerpo.errors && cuerpo.errors.length > 0) {
    throw new BffError(
      "El BFF devolvió errores.",
      cuerpo.errors.map((e) => e.message).join(" | "),
    );
  }
  if (!cuerpo.data) {
    throw new BffError("El BFF no devolvió datos.");
  }
  return cuerpo.data;
}

/**
 * Obtiene la vista agregada del cliente (cuentas, saldos e historial) desde el BFF GraphQL.
 *
 * @param rut          RUT del titular (formato chileno). Requerido en la demo (ver auth).
 * @param accessToken  access token de Keycloak para propagar como Bearer.
 */
export async function obtenerMe(
  rut: string | undefined,
  accessToken: string | undefined,
): Promise<Me> {
  const data = await ejecutarGraphQl<{ me: Me | null }>(
    ME_QUERY,
    { rut: rut ?? null },
    accessToken,
  );
  if (!data.me) {
    throw new BffError("El BFF no devolvió información del cliente.");
  }
  return data.me;
}
