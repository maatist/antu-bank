package cl.antubank.transfer.ledger;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import java.util.UUID;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP síncrono hacia el ledger-service.
 *
 * <p><strong>Alcance temporal (tarea 4.3):</strong> el transfer-service invoca directamente al
 * ledger para lograr un flujo end-to-end temprano (transferencia → asiento → saldo actualizado),
 * <em>antes</em> de introducir Kafka/Outbox en la tarea 5. Cuando exista el flujo event-driven,
 * este acoplamiento síncrono se reemplaza por la publicación de eventos.
 *
 * <p>Ofrece dos operaciones sobre el contrato del ledger (Requisito 3):
 * <ul>
 *   <li>{@link #balance(UUID, Currency)}: consulta el saldo derivado de una cuenta, base de la
 *       validación de fondos (Requisito 4, criterio 5).</li>
 *   <li>{@link #registerTransfer(UUID, UUID, UUID, Money)}: registra la doble entrada de una
 *       transferencia confirmada (débito origen, crédito destino).</li>
 *   <li>{@link #reverseTransfer(UUID, UUID, UUID, Money)}: registra el asiento inverso de
 *       compensación (reversa) cuando la saga debe deshacer un asentamiento (Requisito 6).</li>
 * </ul>
 */
public class LedgerClient {

    private final RestClient restClient;

    public LedgerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Consulta el saldo derivado de una cuenta en una moneda.
     *
     * @param accountId cuenta a consultar.
     * @param currency  moneda del saldo (CLP como moneda principal).
     * @return el saldo como {@link Money}.
     */
    public Money balance(UUID accountId, Currency currency) {
        BalanceResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/transactions/balances/{accountId}")
                        .queryParam("currency", currency.name())
                        .build(accountId))
                .retrieve()
                .body(BalanceResponse.class);
        if (response == null) {
            throw new IllegalStateException(
                    "El ledger-service no devolvió saldo para la cuenta " + accountId);
        }
        return response.toMoney();
    }

    /**
     * Registra en el ledger la doble entrada de una transferencia confirmada: debita la cuenta
     * origen y acredita la destino por el mismo monto, dejando la transacción balanceada (Σ = 0).
     *
     * @param transferId    id de la transferencia (viaja como {@code reference} de negocio).
     * @param source        cuenta origen (débito).
     * @param destination   cuenta destino (crédito).
     * @param amount        monto a mover (en CLP).
     */
    public void registerTransfer(UUID transferId, UUID source, UUID destination, Money amount) {
        long amountMinor = amount.toMinorUnits();
        RegisterTransactionRequest request = new RegisterTransactionRequest(
                "transfer-" + transferId,
                amount.currency(),
                java.util.List.of(
                        LedgerEntryRequest.debit(source, amountMinor),
                        LedgerEntryRequest.credit(destination, amountMinor)));

        restClient.post()
                .uri("/transactions")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Registra en el ledger el <strong>asiento inverso</strong> (reversa) de una transferencia ya
     * asentada, como <em>compensación</em> de la saga ante un fallo posterior (Requisito 6,
     * criterios 2 y 3; design.md 6.3, {@code CompensarAsiento}).
     *
     * <p>El ledger es <strong>inmutable</strong> (sin update/delete; Requisito 3, criterio 3): no se
     * puede borrar el asiento original. La compensación se materializa como una nueva transacción
     * balanceada con los signos intercambiados —débito y crédito se invierten respecto al asiento
     * original— de modo que el efecto neto sobre los saldos sea cero (como si la transferencia no
     * hubiese ocurrido). Se emplea una {@code reference} propia ({@code reversal-transfer-<id>}) que
     * referencia la transferencia original y la distingue del asiento que compensa, dejando ambos
     * movimientos auditables.
     *
     * @param transferId  id de la transferencia original que se compensa.
     * @param source      cuenta origen de la transferencia original (aquí se acredita la reversa).
     * @param destination cuenta destino de la transferencia original (aquí se debita la reversa).
     * @param amount      monto original (idéntico al asentado, en CLP).
     */
    public void reverseTransfer(UUID transferId, UUID source, UUID destination, Money amount) {
        long amountMinor = amount.toMinorUnits();
        // Signos intercambiados respecto a registerTransfer: se debita el destino y se acredita el
        // origen, revirtiendo exactamente el movimiento original (Σ = 0).
        RegisterTransactionRequest request = new RegisterTransactionRequest(
                "reversal-transfer-" + transferId,
                amount.currency(),
                java.util.List.of(
                        LedgerEntryRequest.debit(destination, amountMinor),
                        LedgerEntryRequest.credit(source, amountMinor)));

        restClient.post()
                .uri("/transactions")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
