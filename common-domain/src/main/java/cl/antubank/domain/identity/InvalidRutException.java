package cl.antubank.domain.identity;

/**
 * Se lanza cuando un RUT no es válido (formato incorrecto o dígito verificador erróneo).
 */
public class InvalidRutException extends RuntimeException {

    public InvalidRutException(String message) {
        super(message);
    }
}
