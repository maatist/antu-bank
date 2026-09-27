-- Migración V2: contabilidad de doble entrada del ledger-service.
-- Crea las tablas ledger_transaction y ledger_entry y refuerza en base de datos el
-- invariante fundamental del libro mayor: por cada transacción, la suma de los asientos
-- (débitos positivos + créditos negativos, en minor units) debe ser exactamente cero.
-- Ver requirements.md, Requisito 3, criterios 1 (Σ = 0) y 2 (rechazar Σ ≠ 0).

-- Transacción contable: agrupa N asientos de débito/crédito.
CREATE TABLE ledger_transaction (
    id         UUID        PRIMARY KEY,
    reference  VARCHAR(100),
    currency   VARCHAR(3)  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Asiento individual (línea) de una transacción.
-- amount_minor: monto en minor units de la moneda, entero con signo según el tipo
-- (débito > 0, crédito < 0). Usar enteros permite verificar el balance con aritmética exacta.
CREATE TABLE ledger_entry (
    id             UUID        PRIMARY KEY,
    transaction_id UUID        NOT NULL REFERENCES ledger_transaction (id),
    account_id     UUID        NOT NULL,
    entry_type     VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    currency       VARCHAR(3)  NOT NULL,
    amount_minor   BIGINT      NOT NULL,
    -- Coherencia local: el signo de amount_minor debe corresponder al tipo de asiento,
    -- y ningún asiento puede ser de monto cero.
    CONSTRAINT chk_ledger_entry_sign CHECK (
        (entry_type = 'DEBIT'  AND amount_minor > 0) OR
        (entry_type = 'CREDIT' AND amount_minor < 0)
    )
);

-- Índice para derivar el saldo de una cuenta (Σ asientos por account_id) — tarea 3.3/3.4.
CREATE INDEX idx_ledger_entry_account ON ledger_entry (account_id);
CREATE INDEX idx_ledger_entry_transaction ON ledger_entry (transaction_id);

-- Refuerzo del invariante Σ = 0 a nivel de base de datos.
-- Un CHECK no puede sumar entre filas, por lo que se usa un CONSTRAINT TRIGGER DIFERIDO:
-- se evalúa al final de la transacción de base de datos, cuando todos los asientos ya
-- fueron insertados, y rechaza el commit si la suma neta por transacción no es cero.
CREATE OR REPLACE FUNCTION assert_ledger_transaction_balanced()
RETURNS TRIGGER AS $$
DECLARE
    net BIGINT;
BEGIN
    SELECT COALESCE(SUM(amount_minor), 0)
      INTO net
      FROM ledger_entry
     WHERE transaction_id = NEW.transaction_id;

    IF net <> 0 THEN
        RAISE EXCEPTION
            'Transacción contable desbalanceada (id=%): la suma de los asientos debe ser 0, pero fue %',
            NEW.transaction_id, net
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger diferido: por defecto se posterga hasta el commit (INITIALLY DEFERRED),
-- de modo que los múltiples INSERT de asientos de una misma transacción se validan
-- como un todo balanceado.
CREATE CONSTRAINT TRIGGER trg_ledger_transaction_balanced
    AFTER INSERT ON ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION assert_ledger_transaction_balanced();
