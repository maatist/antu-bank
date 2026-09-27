package cl.antubank.transfer.saga;

import cl.antubank.transfer.persistence.TransferEntity;
import org.springframework.stereotype.Component;

/**
 * Acción del paso <em>confirmar</em> de la saga, aislada como colaborador para que el orquestador
 * pueda invocar una verificación/efecto de confirmación tras haber asentado la doble entrada.
 *
 * <p>En un sistema real la confirmación puede implicar un efecto adicional (notificar a otro
 * servicio, revalidar contra el ledger, marcar un contrato, etc.) que puede fallar. Modelarla como
 * un colaborador permite:
 * <ul>
 *   <li>mantener el orquestador enfocado en la coordinación y la compensación, y</li>
 *   <li>ejercitar en tests el escenario clave de la tarea 6.2: un fallo <strong>después</strong> de
 *       un asentamiento exitoso, que exige compensación con asiento inverso (Requisito 6,
 *       criterios 2 y 3).</li>
 * </ul>
 *
 * <p>La implementación por defecto ({@link Default}) no hace nada: en el flujo actual la
 * confirmación es una transición local de estado (la ejecuta el orquestador). Se deja como punto de
 * extensión explícito.
 */
@FunctionalInterface
public interface SagaConfirmationStep {

    /**
     * Ejecuta el efecto de confirmación para la transferencia dada. Puede lanzar una excepción para
     * señalar que la confirmación falló, disparando la compensación de la saga.
     *
     * @param transfer la transferencia ya asentada que se está confirmando.
     */
    void confirmar(TransferEntity transfer);

    /** Implementación por defecto: la confirmación no tiene efecto adicional (no-op). */
    @Component
    class Default implements SagaConfirmationStep {
        @Override
        public void confirmar(TransferEntity transfer) {
            // Sin efecto adicional: la confirmación es una transición local de estado en la saga.
        }
    }
}
