package cl.antubank.transfer.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Estado persistido de la <strong>saga de transferencia</strong> (Requisito 6, criterio 4;
 * design.md, sección 6.3).
 *
 * <p>Cada fila es una instancia de saga asociada 1:1 a una {@code TransferEntity}. Guarda el estado
 * global ({@link SagaState}) y el estado por paso ({@code reserveStatus}, {@code postStatus},
 * {@code confirmStatus}) para que el progreso sobreviva reinicios y sea auditable: siempre se puede
 * saber qué pasos ya se ejecutaron.
 *
 * <p>Las transiciones se realizan a través de métodos de dominio ({@link #reservarFondos()},
 * {@link #asentar()}, {@link #confirmar()}) que encapsulan la máquina de estados y validan el orden
 * de los pasos. La entidad no invoca servicios externos: el orquestador ({@code TransferSagaOrchestrator})
 * ejecuta el efecto de cada paso y luego registra la transición aquí.
 *
 * <p><strong>Camino feliz (tarea 6.1).</strong> Transiciones de éxito {@link #reservarFondos()} →
 * {@link #asentar()} → {@link #confirmar()}.
 *
 * <p><strong>Compensación por paso (tarea 6.2; Requisito 6, criterios 2 y 3).</strong> Ante un
 * fallo, la máquina de estados transita por {@link SagaState#COMPENSANDO} y compensa los pasos ya
 * completados en orden inverso: {@link #fallarConfirmacion(String)}/{@link #fallarAsentamiento(String)}
 * llevan a compensar, {@link #compensarAsiento()} revierte el asiento (el orquestador registra el
 * asiento inverso en el ledger, que es inmutable) y {@link #compensarReserva()} libera la reserva y
 * cierra la saga como {@link SagaState#COMPENSADA}. Un fallo al reservar termina en
 * {@link SagaState#FALLIDA} sin nada que compensar ({@link #fallarReserva(String)}).
 */
@Entity
@Table(name = "transfer_saga")
public class TransferSagaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Transferencia coordinada por esta saga (1:1). */
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private SagaState state;

    /** Estado del paso "reservar fondos". */
    @Enumerated(EnumType.STRING)
    @Column(name = "reserve_status", nullable = false, length = 20)
    private SagaStepStatus reserveStatus;

    /** Estado del paso "asentar" (doble entrada / outbox). */
    @Enumerated(EnumType.STRING)
    @Column(name = "post_status", nullable = false, length = 20)
    private SagaStepStatus postStatus;

    /** Estado del paso "confirmar". */
    @Enumerated(EnumType.STRING)
    @Column(name = "confirm_status", nullable = false, length = 20)
    private SagaStepStatus confirmStatus;

    /** Motivo del último fallo (diagnóstico/compensación); nulo en camino feliz. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferSagaEntity() {
        // Requerido por JPA.
    }

    private TransferSagaEntity(UUID id, UUID transferId) {
        this.id = id;
        this.transferId = transferId;
        this.state = SagaState.INICIADA;
        this.reserveStatus = SagaStepStatus.PENDIENTE;
        this.postStatus = SagaStepStatus.PENDIENTE;
        this.confirmStatus = SagaStepStatus.PENDIENTE;
    }

    /**
     * Crea una saga recién iniciada para una transferencia, con todos sus pasos pendientes.
     *
     * @param transferId identificador de la transferencia coordinada.
     * @return la nueva saga en estado {@link SagaState#INICIADA}.
     */
    public static TransferSagaEntity iniciar(UUID transferId) {
        return new TransferSagaEntity(UUID.randomUUID(), transferId);
    }

    /**
     * Registra la finalización del paso 1 (reservar fondos): {@link SagaState#INICIADA} →
     * {@link SagaState#FONDOS_RESERVADOS}.
     *
     * @throws IllegalStateException si la saga no está en el estado esperado para este paso.
     */
    public void reservarFondos() {
        exigirEstado(SagaState.INICIADA, "reservar fondos");
        this.reserveStatus = SagaStepStatus.COMPLETADO;
        this.state = SagaState.FONDOS_RESERVADOS;
    }

    /**
     * Registra la finalización del paso 2 (asentar la doble entrada): {@link SagaState#FONDOS_RESERVADOS}
     * → {@link SagaState#ASENTADA}.
     *
     * @throws IllegalStateException si el paso previo (reservar fondos) no se completó.
     */
    public void asentar() {
        exigirEstado(SagaState.FONDOS_RESERVADOS, "asentar");
        this.postStatus = SagaStepStatus.COMPLETADO;
        this.state = SagaState.ASENTADA;
    }

    /**
     * Registra la finalización del paso 3 (confirmar): {@link SagaState#ASENTADA} →
     * {@link SagaState#CONFIRMADA}. Estado final del camino feliz.
     *
     * @throws IllegalStateException si el paso previo (asentar) no se completó.
     */
    public void confirmar() {
        exigirEstado(SagaState.ASENTADA, "confirmar");
        this.confirmStatus = SagaStepStatus.COMPLETADO;
        this.state = SagaState.CONFIRMADA;
    }

    // ------------------------------------------------------------------------------------------
    // Transiciones de fallo y compensación (tarea 6.2; design.md 6.3; Requisito 6, criterios 2 y 3)
    // ------------------------------------------------------------------------------------------

    /**
     * Registra que el paso 1 (reservar fondos) falló: la saga termina como {@link SagaState#FALLIDA}
     * sin nada que compensar (design.md 6.3: {@code ReservarFondos --> [*]: fallo (nada que
     * compensar)}). El fallo ocurre antes de cualquier efecto externo, por lo que no hay
     * compensación que ejecutar (Requisito 6, criterio 2).
     *
     * @param motivo descripción del fallo (se registra en {@code failure_reason}).
     */
    public void fallarReserva(String motivo) {
        exigirEstado(SagaState.INICIADA, "fallar reserva");
        this.reserveStatus = SagaStepStatus.FALLIDO;
        this.state = SagaState.FALLIDA;
        this.failureReason = truncar(motivo);
    }

    /**
     * Registra que el paso 2 (asentar) falló habiendo ya reservado los fondos: se pasa a
     * {@link SagaState#COMPENSANDO} para compensar el paso previo (la reserva). Según design.md 6.3
     * ({@code Asentar --> CompensarReserva: fallo}), el asiento no llegó a registrarse, de modo que
     * solo resta liberar la reserva.
     *
     * @param motivo descripción del fallo.
     */
    public void fallarAsentamiento(String motivo) {
        exigirEstado(SagaState.FONDOS_RESERVADOS, "fallar asentamiento");
        this.postStatus = SagaStepStatus.FALLIDO;
        this.state = SagaState.COMPENSANDO;
        this.failureReason = truncar(motivo);
    }

    /**
     * Registra que el paso 3 (confirmar) falló habiendo ya asentado la doble entrada: se pasa a
     * {@link SagaState#COMPENSANDO} para compensar los pasos completados en orden inverso —primero
     * el asiento (asiento inverso en el ledger), luego la reserva— según design.md 6.3
     * ({@code Confirmar --> CompensarAsiento --> CompensarReserva}).
     *
     * @param motivo descripción del fallo.
     */
    public void fallarConfirmacion(String motivo) {
        exigirEstado(SagaState.ASENTADA, "fallar confirmación");
        this.confirmStatus = SagaStepStatus.FALLIDO;
        this.state = SagaState.COMPENSANDO;
        this.failureReason = truncar(motivo);
    }

    /**
     * Compensa el paso 2 (asentar): marca el asiento como {@link SagaStepStatus#COMPENSADO}. El
     * orquestador ya registró el asiento inverso en el ledger (el ledger es inmutable: se compensa
     * con una transacción de reversa, nunca borrando/actualizando asientos). Requiere estar
     * compensando y que el asiento estuviera completado.
     */
    public void compensarAsiento() {
        exigirEstado(SagaState.COMPENSANDO, "compensar asiento");
        if (this.postStatus != SagaStepStatus.COMPLETADO) {
            throw new IllegalStateException(
                    "No se puede compensar el asiento: su estado es " + this.postStatus
                            + " (se esperaba COMPLETADO)");
        }
        this.postStatus = SagaStepStatus.COMPENSADO;
    }

    /**
     * Compensa el paso 1 (reservar fondos) y cierra la saga como {@link SagaState#COMPENSADA}: los
     * saldos quedan íntegros como si la transferencia no hubiese ocurrido (Requisito 6, criterio 3;
     * design.md 6.3: {@code CompensarReserva --> [*]: saldos íntegros}).
     *
     * <p>En el modelo actual la "reserva" es solo una validación de saldo (no bloquea fondos en el
     * ledger), por lo que su compensación es una liberación lógica: basta marcar el paso como
     * {@link SagaStepStatus#COMPENSADO}. Debe invocarse tras {@link #compensarAsiento()} cuando el
     * asiento se había completado, para respetar el orden inverso.
     */
    public void compensarReserva() {
        exigirEstado(SagaState.COMPENSANDO, "compensar reserva");
        this.reserveStatus = SagaStepStatus.COMPENSADO;
        this.state = SagaState.COMPENSADA;
    }

    private static String truncar(String motivo) {
        if (motivo == null) {
            return null;
        }
        return motivo.length() <= 500 ? motivo : motivo.substring(0, 500);
    }

    private void exigirEstado(SagaState esperado, String paso) {
        if (this.state != esperado) {
            throw new IllegalStateException(
                    "Transición de saga inválida al ejecutar '" + paso + "': se esperaba estado "
                            + esperado + " pero la saga está en " + this.state);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public SagaState getState() {
        return state;
    }

    public SagaStepStatus getReserveStatus() {
        return reserveStatus;
    }

    public SagaStepStatus getPostStatus() {
        return postStatus;
    }

    public SagaStepStatus getConfirmStatus() {
        return confirmStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
