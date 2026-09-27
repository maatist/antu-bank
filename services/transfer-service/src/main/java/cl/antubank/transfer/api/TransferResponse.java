package cl.antubank.transfer.api;

import cl.antubank.domain.money.Currency;
import cl.antubank.transfer.persistence.TransferEntity;
import cl.antubank.transfer.persistence.TransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación de una transferencia en las respuestas de la API.
 *
 * <p>Es el "resultado" que se persiste junto a la {@code Idempotency-Key} y que se retorna sin
 * cambios ante un reintento con la misma clave (ver requirements.md, Requisito 4, criterios 1 y 2).
 * El monto se expone en minor units (para CLP, pesos enteros) para no perder precisión.
 */
@Schema(description = "Resultado de una transferencia")
public record TransferResponse(

        @Schema(description = "Identificador de la transferencia")
        UUID id,

        @Schema(description = "Estado de la transferencia")
        TransferStatus status,

        @Schema(description = "Cuenta origen")
        UUID sourceAccountId,

        @Schema(description = "Cuenta destino")
        UUID destinationAccountId,

        @Schema(description = "Monto en minor units (CLP: pesos enteros)", example = "50000")
        long amountMinor,

        @Schema(description = "Moneda")
        Currency currency,

        @Schema(description = "Fecha de creación")
        Instant createdAt
) {

    /**
     * Construye la respuesta a partir de la entidad persistida.
     */
    public static TransferResponse from(TransferEntity entity) {
        return new TransferResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getSourceAccountId(),
                entity.getDestinationAccountId(),
                entity.getAmountMinor(),
                entity.getCurrency(),
                entity.getCreatedAt()
        );
    }
}
