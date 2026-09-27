package cl.antubank.transfer.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios de la máquina de estados de {@link TransferSagaEntity} (tareas 6.1 y 6.2), sin
 * Spring ni base de datos.
 *
 * <p>Cubren:
 * <ul>
 *   <li><strong>Camino feliz</strong> (reservar → asentar → confirmar) y la validación de orden de
 *       los pasos: una transición fuera de secuencia debe rechazarse (Requisito 6, criterio 1).</li>
 *   <li><strong>Máquina de compensación</strong> (Requisito 6, criterios 2 y 3): ante un fallo, la
 *       saga transita por {@link SagaState#COMPENSANDO} y compensa los pasos completados en
 *       <em>orden inverso</em> (asiento antes que reserva), registrando el {@code failure_reason} y
 *       cerrando como {@link SagaState#COMPENSADA} —o como {@link SagaState#FALLIDA} si el fallo
 *       ocurre al reservar, sin nada que compensar.</li>
 * </ul>
 *
 * <p>Estas pruebas unitarias son complementarias a los tests de integración de la saga: aquí se
 * verifica la <em>lógica de transición</em> de la máquina de estados de forma aislada (incluido el
 * estado intermedio {@code COMPENSANDO} y el rechazo de compensaciones fuera de secuencia), mientras
 * que la integración verifica el efecto extremo a extremo contra PostgreSQL y el ledger.
 */
class TransferSagaEntityTest {

    @Test
    void sagaNaceIniciadaConPasosPendientes() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());

        assertThat(saga.getState()).isEqualTo(SagaState.INICIADA);
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.PENDIENTE);
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.PENDIENTE);
        assertThat(saga.getConfirmStatus()).isEqualTo(SagaStepStatus.PENDIENTE);
    }

    @Test
    void caminoFelizAvanzaHastaConfirmada() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());

        saga.reservarFondos();
        assertThat(saga.getState()).isEqualTo(SagaState.FONDOS_RESERVADOS);
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPLETADO);

        saga.asentar();
        assertThat(saga.getState()).isEqualTo(SagaState.ASENTADA);
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.COMPLETADO);

        saga.confirmar();
        assertThat(saga.getState()).isEqualTo(SagaState.CONFIRMADA);
        assertThat(saga.getConfirmStatus()).isEqualTo(SagaStepStatus.COMPLETADO);
    }

    @Test
    void asentarSinReservarPrimeroEsRechazado() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());

        assertThatThrownBy(saga::asentar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asentar");
    }

    @Test
    void confirmarSinAsentarPrimeroEsRechazado() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());
        saga.reservarFondos();

        assertThatThrownBy(saga::confirmar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmar");
    }

    // ------------------------------------------------------------------------------------------
    // Máquina de compensación (tarea 6.2; Requisito 6, criterios 2 y 3; design.md 6.3)
    // ------------------------------------------------------------------------------------------

    @Test
    void fallarReservaTerminaFallidaSinNadaQueCompensar() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());

        // Fallo al reservar: antes de cualquier efecto externo (design.md 6.3: ReservarFondos --> [*]).
        saga.fallarReserva("saldo insuficiente");

        assertThat(saga.getState()).isEqualTo(SagaState.FALLIDA);
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.FALLIDO);
        // Los pasos posteriores nunca se intentaron: siguen pendientes (no hay nada que compensar).
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.PENDIENTE);
        assertThat(saga.getConfirmStatus()).isEqualTo(SagaStepStatus.PENDIENTE);
        assertThat(saga.getFailureReason()).contains("saldo insuficiente");
    }

    @Test
    void fallarAsentamientoCompensaSoloLaReservaYCierraCompensada() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());
        saga.reservarFondos();

        // Fallo al asentar: el asiento no llegó a registrarse; solo resta liberar la reserva
        // (design.md 6.3: Asentar --> CompensarReserva).
        saga.fallarAsentamiento("el ledger rechazó el asiento");
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSANDO);
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.FALLIDO);
        assertThat(saga.getFailureReason()).contains("ledger");

        saga.compensarReserva();
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSADA);
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPENSADO);
    }

    @Test
    void fallarConfirmacionCompensaAsientoYReservaEnOrdenInverso() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());
        saga.reservarFondos();
        saga.asentar();

        // Fallo al confirmar con el asiento YA registrado: se compensa en orden inverso —primero el
        // asiento (asiento inverso en el ledger), luego la reserva— (design.md 6.3:
        // Confirmar --> CompensarAsiento --> CompensarReserva).
        saga.fallarConfirmacion("verificación post-asentamiento falló");
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSANDO);
        assertThat(saga.getConfirmStatus()).isEqualTo(SagaStepStatus.FALLIDO);
        assertThat(saga.getFailureReason()).contains("post-asentamiento");
        // Antes de compensar, los pasos completados siguen COMPLETADO.
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.COMPLETADO);
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPLETADO);

        saga.compensarAsiento();
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.COMPENSADO);
        // Compensar el asiento no cierra la saga: aún resta compensar la reserva.
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSANDO);
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPLETADO);

        saga.compensarReserva();
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPENSADO);
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSADA);
    }

    @Test
    void compensarReservaAntesQueElAsientoEsRechazado() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());
        saga.reservarFondos();
        saga.asentar();
        saga.fallarConfirmacion("fallo de confirmación");

        // El orden inverso es obligatorio: no se puede compensar la reserva (que cerraría la saga)
        // mientras el asiento siga COMPLETADO. Debe compensarse el asiento primero.
        saga.compensarReserva();
        assertThatThrownBy(saga::compensarAsiento)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compensar asiento");
    }

    @Test
    void compensarSinHaberFalladoEsRechazado() {
        TransferSagaEntity saga = TransferSagaEntity.iniciar(UUID.randomUUID());
        saga.reservarFondos();
        saga.asentar();

        // Estando ASENTADA (no COMPENSANDO), compensar el asiento es una transición inválida.
        assertThatThrownBy(saga::compensarAsiento)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compensar asiento");
    }
}
