package cl.antubank.transfer.saga;

/**
 * Estado de un paso individual de la saga de transferencia (reservar / asentar / confirmar).
 *
 * <p>Registrar el estado por paso —además del {@link SagaState} global— permite <em>auditar qué se
 * hizo</em> (Requisito 6, criterio 4) y habilita una compensación limpia en la tarea 6.2: al fallar,
 * el orquestador puede recorrer los pasos {@link #COMPLETADO} y compensarlos, marcándolos como
 * {@link #COMPENSADO}, sin ambigüedad sobre qué quedó pendiente.
 *
 * <p><strong>Alcance de la tarea 6.1 (camino feliz).</strong> En el flujo exitoso los pasos pasan de
 * {@link #PENDIENTE} a {@link #COMPLETADO}. {@link #FALLIDO} y {@link #COMPENSADO} quedan definidos
 * como puntos de extensión para la tarea 6.2.
 */
public enum SagaStepStatus {

    /** El paso aún no se ha ejecutado. */
    PENDIENTE,

    /** El paso se ejecutó con éxito. */
    COMPLETADO,

    /** El paso se intentó y falló (tarea 6.2). */
    FALLIDO,

    /** El paso, previamente completado, fue revertido por la compensación (tarea 6.2). */
    COMPENSADO
}
