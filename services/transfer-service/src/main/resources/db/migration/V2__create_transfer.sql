-- Migración V2: transferencias idempotentes del transfer-service.
-- Crea las tablas transfer e idempotency_key.
-- Ver requirements.md, Requisito 4:
--   * criterio 1: persistir la Idempotency-Key junto al resultado (transfer_id).
--   * criterio 3: unicidad de la clave para que solicitudes concurrentes apliquen un solo movimiento.
--   * criterio 4: montos en CLP (amount_minor en minor units, entero, sin punto flotante).

-- Transferencia entre dos cuentas.
-- amount_minor: monto en minor units de la moneda (para CLP, pesos enteros).
CREATE TABLE transfer (
    id                     UUID        PRIMARY KEY,
    source_account_id      UUID        NOT NULL,
    destination_account_id UUID        NOT NULL,
    amount_minor           BIGINT      NOT NULL,
    currency               VARCHAR(3)  NOT NULL,
    status                 VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- No se permite transferir hacia la misma cuenta ni montos no positivos.
    CONSTRAINT chk_transfer_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT chk_transfer_amount_positive  CHECK (amount_minor > 0)
);

-- Clave de idempotencia persistida junto al resultado (la transferencia que produjo).
-- La restricción única sobre idem_key es el mecanismo que garantiza "misma key = mismo
-- resultado sin duplicar", incluso ante solicitudes concurrentes.
CREATE TABLE idempotency_key (
    id          UUID         PRIMARY KEY,
    idem_key    VARCHAR(200) NOT NULL,
    transfer_id UUID         NOT NULL REFERENCES transfer (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_key UNIQUE (idem_key)
);

-- Índice para recuperar por transferencia asociada.
CREATE INDEX idx_idempotency_key_transfer ON idempotency_key (transfer_id);
