-- =============================================================================
-- Init del PostgreSQL consolidado del perfil "demo" (tarea 12a.2, Requisito 12, criterio 2).
-- =============================================================================
-- En el despliegue público reducido hay UN solo PostgreSQL para todos los servicios. El
-- aislamiento se logra con un SCHEMA por servicio dentro de la misma base (neobank). Cada
-- servicio se conecta con currentSchema=<su schema> y Flyway registra su historial en él, de
-- modo que las migraciones de un servicio no colisionan con las de otro.
--
-- Este script lo ejecuta el contenedor postgres al inicializar la base (docker-entrypoint-initdb.d)
-- SOLO la primera vez, cuando el volumen de datos está vacío. Es idempotente por si se ejecuta de
-- nuevo. Flyway también crea el schema si falta (create-schemas: true); dejarlo explícito aquí
-- documenta la topología y evita depender del orden de arranque.
--
-- Local y AWS mantienen database-per-service estricto (Requisito 12, criterio 7); este archivo
-- aplica ÚNICAMENTE al stack reducido de demo.

-- Extensión usada por las migraciones (generación de UUID). Disponible a nivel de base.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Un schema por servicio con estado. Los servicios sin estado (fraud, notification, gateway) no
-- usan base de datos.
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS transfer;
