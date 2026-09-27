# ADR-0006: Saga orquestada para transferencias

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-006)

## Contexto

Una transferencia en Antu Bank cruza varios servicios y varios pasos: **reservar fondos**,
**asentar** el movimiento en el ledger de doble entrada y **confirmar** la operación. Como cada
servicio es dueño de su propia base de datos (ver [ADR-0004](0004-database-per-service.md)), no
existe una transacción ACID que abarque todo el flujo. Se necesita una **transacción distribuida**
con compensación: si un paso falla, hay que deshacer lógicamente los pasos anteriores para que los
saldos queden íntegros.

Hay dos estilos de saga:

- **Coreografía:** cada servicio reacciona a eventos y decide su siguiente paso; no hay un
  coordinador central. Es más desacoplado pero el flujo global queda implícito y disperso, difícil
  de seguir y de razonar sobre las compensaciones.
- **Orquestación:** un componente central dirige la secuencia de pasos y sus compensaciones.

## Decisión

Implementar la transferencia como una **saga orquestada** con **estado persistido**: un
orquestador dirige la secuencia `ReservarFondos → Asentar → Confirmar` y define una
**compensación por paso** ante fallo (rollback lógico), de modo que:

- Si falla al reservar, no hay nada que compensar.
- Si falla al asentar, se compensa la reserva.
- Si falla al confirmar, se compensa el asiento y luego la reserva.

El estado de la saga se persiste para poder retomarla y auditarla.

## Consecuencias

**Beneficios**

- El flujo completo y sus compensaciones están **explícitos y centralizados**, fáciles de razonar,
  testear (camino feliz y de compensación) y auditar.
- Ante un fallo en cualquier paso, los saldos quedan íntegros por diseño.
- El estado persistido permite recuperación y trazabilidad de cada transferencia.

**Trade-offs**

- Introduce un **orquestador con estado** que hay que mantener, versionar y operar, frente a la
  coreografía que no tiene coordinador central.
- Concentra la lógica de flujo en un punto; es un acoplamiento deliberado a cambio de claridad.

Para un dominio financiero donde la corrección de las compensaciones es crítica, la explicitud de
la orquestación pesa más que el mayor desacoplamiento de la coreografía.
