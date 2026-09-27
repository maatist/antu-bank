package cl.antubank.transfer.saga;

/**
 * Estado global de la <strong>saga de transferencia</strong> (orquestación con compensación).
 *
 * <p>Modela la máquina de estados de design.md, sección 6.3 (ver requirements.md, Requisito 6):
 * la saga avanza por los pasos <em>reservar fondos → asentar → confirmar</em> y, ante un fallo,
 * ejecuta las compensaciones de los pasos ya completados.
 *
 * <p><strong>Alcance de la tarea 6.1 (camino feliz).</strong> El orquestador solo transita los
 * estados de éxito: {@link #INICIADA} → {@link #FONDOS_RESERVADOS} → {@link #ASENTADA} →
 * {@link #CONFIRMADA}. Los estados de fallo y compensación ({@link #COMPENSANDO},
 * {@link #COMPENSADA}, {@link #FALLIDA}) quedan definidos desde ya como <em>puntos de extensión</em>
 * para la tarea 6.2 (compensaciones por paso), de modo que agregarla no requiera tocar el esquema
 * ni este enum.
 */
public enum SagaState {

    /** Saga creada; aún no se ha reservado fondos. Estado inicial. */
    INICIADA,

    /** Paso 1 completado: los fondos fueron validados/reservados contra el saldo del ledger. */
    FONDOS_RESERVADOS,

    /** Paso 2 completado: la doble entrada quedó asentada (o encolada vía outbox). */
    ASENTADA,

    /** Paso 3 completado: la transferencia quedó confirmada. Estado final del camino feliz. */
    CONFIRMADA,

    /** Un paso falló y se están ejecutando las compensaciones de los pasos previos (tarea 6.2). */
    COMPENSANDO,

    /** La saga fue compensada: los saldos quedaron íntegros como si no hubiese ocurrido (tarea 6.2). */
    COMPENSADA,

    /** La saga falló sin dejar nada que compensar (p. ej. fallo al reservar fondos) (tarea 6.2). */
    FALLIDA
}
