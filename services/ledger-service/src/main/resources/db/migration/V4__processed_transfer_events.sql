-- Migración V4: soporte de consumo idempotente de eventos de transferencia.
-- Refuerza el Requisito 5, criterio 3 (ledger consume el evento y genera el asiento) y
-- criterio 5 (los saldos deben cuadrar), garantizando que un mismo evento entregado más de
-- una vez (entrega "al menos una vez" de Kafka) NO produzca asientos duplicados.
--
-- Diseño: tabla de eventos procesados (processed-events table) en lugar de una restricción de
-- unicidad sobre `ledger_transaction.reference`. El motivo es que `reference` es un campo de
-- negocio libre y NO único: transacciones registradas manualmente por la API REST pueden
-- repetir la misma referencia legítimamente. La idempotencia del consumo de eventos se aísla
-- así en su propia tabla, sin alterar la semántica del ledger general.
--
-- El consumer, al recibir un TransferConfirmed, registra el transferId en esta tabla dentro de la
-- misma transacción en que asienta. La PRIMARY KEY sobre transfer_id convierte la verificación en
-- una garantía de base de datos: aunque dos entregas concurrentes del mismo evento superen la
-- verificación de lectura, solo una podrá insertar la marca; la otra fallará por violación de
-- clave primaria y el consumer la tratará como ya procesada. Como la marca y el asiento se
-- escriben en la misma transacción, ambos existen o ninguno (atomicidad).

CREATE TABLE processed_transfer_event (
    transfer_id  UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
