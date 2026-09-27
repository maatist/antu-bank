-- Migración inicial: tabla de cuentas del account-service.
-- Contexto banca chilena: titular identificado por RUT, banco de la plaza local.

CREATE TABLE accounts (
    id           UUID         PRIMARY KEY,
    holder_rut   VARCHAR(20)  NOT NULL,
    holder_name  VARCHAR(120) NOT NULL,
    account_type VARCHAR(20)  NOT NULL,
    bank         VARCHAR(40)  NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Índice para búsquedas por RUT del titular (GET /accounts?rut=).
CREATE INDEX idx_accounts_holder_rut ON accounts (holder_rut);
