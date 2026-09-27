package cl.antubank.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Proyección del saldo derivado tal como lo retorna ledger-service (BalanceResponse) para el BFF.
 *
 * <p>El monto se expone en minor units con signo ({@code amountMinor}) y en unidades mayores
 * ({@code amount}), coherente con el servicio. Se mapea al tipo GraphQL {@code Money}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceDto(
        String accountId,
        String currency,
        long amountMinor,
        BigDecimal amount) {
}
