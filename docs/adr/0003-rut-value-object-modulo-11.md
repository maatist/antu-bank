# ADR-0003: `Rut` como value object con validación módulo 11

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-003)

## Contexto

En Chile, personas y empresas se identifican por su **RUT** (Rol Único Tributario): un cuerpo
numérico más un **dígito verificador** calculado con el algoritmo de **módulo 11**. Antu Bank
modela un banco chileno, así que la identidad de clientes y la titularidad de cuentas se apoyan en
el RUT.

Si el RUT se tratara como un simple `String`, el sistema aceptaría identificadores mal formados o
con dígito verificador incorrecto, contaminando la base de datos y desplazando la validación a cada
punto de uso (con el riesgo de olvidarla en alguno). La presentación también importa: los RUT en
Chile se muestran en un formato canónico con separadores de miles y guion (por ejemplo
`12.345.678-5`).

## Decisión

Modelar el RUT como un **value object `Rut` inmutable** en `common-domain`:

- Construcción a partir de un string que se **normaliza** (quita puntos y guion) y se separa en
  cuerpo y dígito verificador.
- **Validación obligatoria del DV mediante módulo 11** en construcción; un RUT inválido se rechaza
  con una excepción de dominio (`InvalidRutException`) y nunca llega a persistirse.
- Método `format()` que entrega la representación chilena canónica (`12.345.678-5`).
- Igualdad **por valor**.

La validación se integra además con Bean Validation (`@ValidRut`) en el borde REST del
`account-service`.

## Consecuencias

**Beneficios**

- Fidelidad al dominio bancario chileno: solo RUT válidos entran al sistema.
- La validación vive en un único lugar (el constructor del value object), no dispersa por la
  aplicación: es imposible construir un `Rut` inválido.
- El formateo canónico centralizado da una presentación consistente en toda la UI y las respuestas
  de la API.

**Trade-offs**

- Introduce un tipo de dominio donde muchos sistemas usarían un `String`, con el costo de mapear
  desde/hacia string en los bordes (persistencia, JSON, formularios).

El costo es mínimo frente al beneficio de garantizar identidad válida en el corazón del sistema.
