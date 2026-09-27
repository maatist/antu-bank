-- Migración inicial (baseline) del ledger-service.
-- Establece el versionado de esquema con Flyway para esta base PostgreSQL propia
-- (database-per-service). Las tablas del libro mayor (LedgerTransaction / LedgerEntry)
-- se agregan en migraciones posteriores junto con sus entidades (tarea 3.2).

-- Extensión para generación de UUID en Postgres (usada por futuras tablas de asientos).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
