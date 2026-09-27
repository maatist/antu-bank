-- Migración V4: estado persistido de la saga de transferencia (orquestación).
-- Ver requirements.md, Requisito 6:
--   * criterio 1: la saga ejecuta los pasos reservar fondos → asentar → confirmar.
--   * criterio 4: el estado de la saga se persiste (para sobrevivir reinicios y ser auditable).
-- Ver design.md, sección 6.3 (diagrama de estados de la saga) y ADR-006 (saga orquestada).
--
-- Alcance de la tarea 6.1 (CAMINO FELIZ con estado persistido): la saga avanza
-- INICIADA → FONDOS_RESERVADOS → ASENTADA → CONFIRMADA, registrando el estado global (state) y el
-- estado por paso (reserve_status / post_status / confirm_status) tras cada transición. Los estados
-- de fallo y compensación (COMPENSANDO/COMPENSADA/FALLIDA y los *_status COMPENSATED) quedan
-- modelados desde ya para que la tarea 6.2 (compensaciones) los use sin cambiar el esquema.

CREATE TABLE transfer_saga (
    -- Identificador de la instancia de saga.
    id             UUID         PRIMARY KEY,
    -- Transferencia que coordina esta saga (1:1). FK a transfer con borrado en cascada: la saga
    -- carece de sentido sin su transferencia, de modo que al eliminar una transferencia se elimina
    -- su saga asociada (evita filas huérfanas y simplifica la limpieza en tests).
    transfer_id    UUID         NOT NULL REFERENCES transfer (id) ON DELETE CASCADE,
    -- Estado global de la saga (máquina de estados de design.md 6.3).
    state          VARCHAR(30)  NOT NULL CHECK (state IN (
                       'INICIADA',
                       'FONDOS_RESERVADOS',
                       'ASENTADA',
                       'CONFIRMADA',
                       'COMPENSANDO',
                       'COMPENSADA',
                       'FALLIDA')),
    -- Estado por paso: permite auditar qué se hizo y habilita la compensación limpia (tarea 6.2).
    reserve_status VARCHAR(20)  NOT NULL CHECK (reserve_status IN (
                       'PENDIENTE', 'COMPLETADO', 'FALLIDO', 'COMPENSADO')),
    post_status    VARCHAR(20)  NOT NULL CHECK (post_status IN (
                       'PENDIENTE', 'COMPLETADO', 'FALLIDO', 'COMPENSADO')),
    confirm_status VARCHAR(20)  NOT NULL CHECK (confirm_status IN (
                       'PENDIENTE', 'COMPLETADO', 'FALLIDO', 'COMPENSADO')),
    -- Detalle del último error (para diagnóstico/compensación); nulo en camino feliz.
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Una única saga por transferencia (la orquestación es 1:1 con la transferencia).
    CONSTRAINT uq_transfer_saga_transfer UNIQUE (transfer_id)
);

-- Índice para localizar la saga por transferencia (además de la restricción única).
CREATE INDEX idx_transfer_saga_transfer ON transfer_saga (transfer_id);

-- Índice parcial para recuperar sagas en curso (útil para reanudación/compensación tras reinicio).
CREATE INDEX idx_transfer_saga_en_curso ON transfer_saga (state)
    WHERE state IN ('INICIADA', 'FONDOS_RESERVADOS', 'ASENTADA', 'COMPENSANDO');
