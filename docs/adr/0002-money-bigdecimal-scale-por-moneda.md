# ADR-0002: `Money` con `BigDecimal` y scale por moneda (nunca `double`)

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-002)

## Contexto

Antu Bank es un core bancario: manipular dinero incorrectamente no es un bug cosmético, es una
pérdida de correctitud contable. Los tipos de punto flotante binarios (`double`/`float`) no pueden
representar exactamente muchos valores decimales (por ejemplo `0.1`), y acumulan error en sumas y
redondeos repetidos. En un ledger de doble entrada, donde la invariante es que los asientos
cuadren en cero, cualquier deriva de precisión rompe la contabilidad.

Además, el dominio es **multi-moneda con distinta granularidad**: el peso chileno (**CLP**) no usa
decimales (scale = 0), mientras que el dólar (**USD**) y la Unidad de Fomento (**UF**) usan dos
decimales (scale = 2). El sistema debe tratar cada moneda con su propia precisión y prohibir operar
montos de monedas distintas sin conversión explícita.

## Decisión

Modelar el dinero como un **value object `Money` inmutable** en `common-domain`, con:

- `amount: BigDecimal` normalizado al **scale de la moneda** en construcción, con redondeo bancario
  `HALF_EVEN`.
- `currency: Currency`, un enum que fija el scale por moneda (`CLP=0`, `USD=2`, `UF=2`).
- Métodos de fábrica `ofMajor(...)` (unidades mayores) y `ofMinor(...)` (minor units).
- `plus`/`minus` que **exigen la misma moneda** y lanzan una excepción de dominio si difieren.
- Igualdad y `hashCode` **por valor**.
- Nunca exponer ni usar internamente `double`/`float` para montos.

La prohibición de saldos negativos se valida en el **borde de negocio**, no en el tipo, para
permitir asientos de débito legítimos dentro del ledger.

## Consecuencias

**Beneficios**

- Correctitud financiera garantizada: sin error de punto flotante, con redondeo bancario explícito
  y consistente por moneda.
- El scale por moneda modela fielmente el dominio chileno (CLP sin decimales) y evita mezclas
  accidentales de monedas.
- La inmutabilidad y la igualdad por valor hacen a `Money` seguro de compartir y fácil de testear.

**Trade-offs**

- Más código y más verbosidad que operar con un `double`: hay que construir, normalizar y validar
  moneda en cada operación.
- `BigDecimal` es más costoso computacionalmente que un primitivo, un costo despreciable frente a
  la exigencia de correctitud.

En fintech este trade-off no es negociable: la precisión monetaria prima sobre la conveniencia
sintáctica.
