package cl.antubank.gateway.graphql.model;

import java.util.List;

/**
 * Modelo del tipo GraphQL raíz {@code Me}: identidad del cliente + sus cuentas + su historial de
 * transferencias. Es el resultado agregado que arma el resolver de la query {@code me}.
 *
 * @param rut       RUT del titular en formato chileno.
 * @param accounts  cuentas del titular (cada una resuelve su saldo de forma anidada).
 * @param transfers historial de transferencias del cliente.
 */
public record MeModel(String rut, List<AccountModel> accounts, List<TransferModel> transfers) {
}
