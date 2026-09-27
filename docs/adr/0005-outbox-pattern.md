# ADR-0005: Outbox pattern para consistencia Kafka + base de datos

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-005)

## Contexto

Cuando `transfer-service` procesa una transferencia, necesita hacer dos cosas: **persistir** el
cambio de negocio en su base PostgreSQL y **publicar** un evento en Kafka para que otros servicios
(`ledger-service`, `fraud-service`, `notification-service`) reaccionen. Este es el clásico problema
del **dual-write**: escribir en dos sistemas distintos sin una transacción que los abarque a ambos.

Si se escribe primero en la base y luego falla la publicación en Kafka, el evento se pierde y el
ledger nunca asienta la transferencia. Si se publica primero y falla el commit en la base, se
emite un evento por algo que nunca ocurrió. No hay orden de las dos operaciones que sea seguro por
sí solo, porque no comparten transacción.

## Decisión

Aplicar el **Outbox pattern**:

- Dentro de la **misma transacción de negocio**, `transfer-service` persiste el cambio y **escribe
  el evento en una tabla `outbox`**. Como es la misma transacción de PostgreSQL, ambos se
  confirman o ninguno lo hace.
- Un **relay** independiente lee las filas pendientes de `outbox`, las **publica en Kafka** y
  luego las marca como publicadas.
- Los eventos usan **serialización versionada** para poder evolucionar el esquema del evento sin
  romper a los consumidores.

Del lado consumidor, `ledger-service` consume el evento de transferencia y genera el asiento
correspondiente.

## Consecuencias

**Beneficios**

- Resuelve el dual-write: el evento se confirma atómicamente junto con el cambio de negocio, así
  que nunca hay un evento sin su hecho ni un hecho sin su evento.
- Entrega **al-menos-una-vez** hacia Kafka; combinada con consumidores idempotentes, garantiza que
  la transferencia se asiente exactamente una vez en efecto.
- Desacopla la publicación del camino de request: si Kafka está caído momentáneamente, el relay
  reintenta sin perder eventos.

**Trade-offs**

- Un **componente adicional** (el relay) que hay que operar y monitorear.
- **Latencia eventual**: entre el commit y la publicación del relay hay un pequeño desfase; los
  consumidores ven el evento un instante después, no de forma síncrona.

La latencia eventual es aceptable para este dominio y es el precio estándar de la consistencia
distribuida sin dual-write.
