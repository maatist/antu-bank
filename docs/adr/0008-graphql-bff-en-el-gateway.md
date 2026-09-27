# ADR-0008: GraphQL como BFF en el gateway

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-008)

## Contexto

El dashboard del frontend necesita, para una sola pantalla, datos que viven en varios servicios:
las **cuentas** del usuario (`account-service`), sus **saldos** (`ledger-service`) y su
**historial** de transferencias (`transfer-service`). Si el frontend consumiera directamente los
REST internos, tendría que hacer múltiples llamadas, orquestar su composición y quedar acoplado a
la forma de cada API — un anti-patrón conocido como "chatty client" y una fuente de
over/under-fetching.

Un **Backend For Frontend (BFF)** resuelve esto ofreciendo al frontend un contrato pensado para sus
necesidades, que agrega por detrás los servicios internos. GraphQL es una buena opción para un BFF
de agregación porque el cliente pide exactamente los campos que necesita en una sola query.

## Decisión

Exponer una **capa GraphQL como BFF en el api-gateway** (Spring for GraphQL) que agrega los REST
internos. El frontend consulta una sola query del estilo:

```graphql
me { accounts, balances, history }
```

y el gateway se encarga de invocar los servicios internos, propagando el token, y componer la
respuesta. Los **servicios internos siguen siendo REST simples**: GraphQL es solo la fachada de
agregación en el borde, no el estilo de comunicación interna. Se expone además un playground
(GraphiQL) para inspección interactiva.

## Consecuencias

**Beneficios**

- Un **contrato único y eficiente** para el frontend: una query trae cuentas, saldos e historial
  sin múltiples round-trips ni over-fetching.
- Los servicios internos permanecen simples (REST), sin cargar con la complejidad de un esquema
  GraphQL cada uno.
- El playground GraphiQL facilita explorar la API, útil para la demo de portafolio.

**Trade-offs**

- Añade una **capa y una tecnología más** en el gateway (esquema, resolvers, agregación) que hay
  que mantener.
- La lógica de composición se concentra en el BFF; hay que cuidar que no se convierta en un punto
  de acoplamiento excesivo con los servicios.

El beneficio de un contrato de frontend limpio y eficiente justifica la capa adicional.
