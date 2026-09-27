package cl.antubank.domain.money;

/**
 * Se lanza al intentar operar dos valores {@link Money} de monedas distintas.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("No se pueden operar montos de monedas distintas: " + left + " y " + right);
    }
}
