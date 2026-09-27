package cl.antubank.transfer.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Solicitud para iniciar una transferencia entre dos cuentas.
 *
 * <p>El monto se expresa en <em>minor units</em> de la moneda; para CLP (moneda principal, ver
 * requirements.md, Requisito 4, criterio 4) son pesos enteros. Guardar minor units enteros evita
 * cualquier error de precisión de punto flotante (ver también {@code TransferEntity}).
 *
 * <p>La clave de idempotencia no viaja en el cuerpo: se recibe en el header {@code Idempotency-Key}
 * (Requisito 4, criterio 1).
 *
 * @param sourceAccountId      cuenta origen (identificador de account-service).
 * @param destinationAccountId cuenta destino (identificador de account-service).
 * @param amountMinor          monto a transferir en minor units; entero positivo (CLP: pesos).
 */
@Schema(description = "Datos para iniciar una transferencia en CLP")
public record CreateTransferRequest(

        @Schema(description = "Cuenta origen",
                example = "3f1e5b2a-1c4d-4e9a-9b7c-2a6f8d0e1234")
        @NotNull(message = "{field.sourceAccountId.notnull}")
        UUID sourceAccountId,

        @Schema(description = "Cuenta destino",
                example = "8b2c7d1e-9f0a-4b3c-8d5e-1a2b3c4d5e6f")
        @NotNull(message = "{field.destinationAccountId.notnull}")
        UUID destinationAccountId,

        @Schema(description = "Monto a transferir en minor units (CLP: pesos enteros)",
                example = "50000")
        @NotNull(message = "{field.amountMinor.notnull}")
        @Positive(message = "{field.amountMinor.positive}")
        Long amountMinor
) {
}
