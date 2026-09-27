-- Migración inicial (baseline) del transfer-service.
-- Establece el versionado de esquema con Flyway para esta base PostgreSQL propia
-- (database-per-service). Las tablas de negocio (transfer / idempotency_key) se crean en V2.

-- Extensión para generación de UUID en Postgres (disponible para futuras tablas).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
