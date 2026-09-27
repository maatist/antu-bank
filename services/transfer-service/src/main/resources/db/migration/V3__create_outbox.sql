-- Migración V3: tabla outbox del transfer-service (Outbox pattern).
-- Ver requirements.md, Requisito 5:
--   * criterio 1: al confirmar una transferencia, el evento se escribe en esta tabla DENTRO de la
--     misma transacción de base de datos que el resultado de negocio (Transfer + Idempotency-Key),
--     resolviendo el problema de dual-write DB↔Kafka.
--   * criterio 4: el esquema de eventos se versiona (columnas event_type + event_version).
-- Ver design.md, sección 6.2 (secuencia del Outbox pattern) y ADR-005.
--
-- El relay (tarea 5.2) leerá las filas pendientes (published = false), las publicará a Kafka y las
-- marcará como publicadas (published = true, published_at). ledger-service (tarea 5.3) consumirá el
-- evento para generar el asiento de doble entrada.

CREATE TABLE outbox (
    -- Identificador del evento (también sirve como clave de deduplicación aguas abajo).
    id             UUID         PRIMARY KEY,
    -- Tipo de agregado que originó el evento (ej. "Transfer").
    aggregate_type VARCHAR(100) NOT NULL,
    -- Identificador del agregado (ej. el id de la transferencia). Sirve como clave de partición.
    aggregate_id   UUID         NOT NULL,
    -- Tipo de evento (ej. "TransferConfirmed") y su versión de esquema (versionado, criterio 4).
    event_type     VARCHAR(100) NOT NULL,
    event_version  INTEGER      NOT NULL,
    -- Payload del evento serializado como JSON.
    payload        JSONB        NOT NULL,
    -- Momento de ocurrencia del evento de negocio (dominio) y de creación de la fila (auditoría).
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Estado de publicación a Kafka: el relay marca published = true y fija published_at.
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMPTZ,
    CONSTRAINT chk_outbox_event_version_positive CHECK (event_version > 0),
    CONSTRAINT chk_outbox_published_at CHECK (
        (published = FALSE AND published_at IS NULL)
        OR (published = TRUE AND published_at IS NOT NULL)
    )
);

-- Índice para que el relay recupere eficientemente los eventos pendientes en orden de ocurrencia.
CREATE INDEX idx_outbox_pending ON outbox (occurred_at) WHERE published = FALSE;
