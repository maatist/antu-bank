-- Migración V3: inmutabilidad de los asientos contables a nivel de base de datos.
-- Refuerza el Requisito 3, criterio 3: "una vez registrado un asiento, el sistema deberá
-- tratarlo como inmutable (sin updates ni deletes)".
--
-- La inmutabilidad ya se expresa en la capa de aplicación (columnas updatable=false en las
-- entidades JPA y ausencia de rutas de update/delete). Esta migración la garantiza también
-- en la base de datos: cualquier intento de UPDATE o DELETE sobre ledger_entry o
-- ledger_transaction es rechazado por un trigger que aborta la operación.
--
-- Un ledger contable es un registro append-only: solo se insertan asientos nuevos. Corregir
-- un error se hace con un asiento compensatorio (reversa), nunca modificando o borrando el
-- original, preservando así la auditabilidad completa.

-- Función que rechaza cualquier UPDATE o DELETE sobre las tablas del ledger.
CREATE OR REPLACE FUNCTION reject_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Los asientos del ledger son inmutables: la operación % sobre la tabla % no está permitida (Requisito 3, criterio 3).',
        TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

-- ledger_entry: rechazar modificaciones y borrados.
CREATE TRIGGER trg_ledger_entry_no_update
    BEFORE UPDATE ON ledger_entry
    FOR EACH ROW
    EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_entry_no_delete
    BEFORE DELETE ON ledger_entry
    FOR EACH ROW
    EXECUTE FUNCTION reject_ledger_mutation();

-- ledger_transaction: rechazar modificaciones y borrados.
CREATE TRIGGER trg_ledger_transaction_no_update
    BEFORE UPDATE ON ledger_transaction
    FOR EACH ROW
    EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_transaction_no_delete
    BEFORE DELETE ON ledger_transaction
    FOR EACH ROW
    EXECUTE FUNCTION reject_ledger_mutation();
