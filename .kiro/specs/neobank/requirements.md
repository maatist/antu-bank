# Requisitos — NeoBank (Fintech / Core Bancario Ligero)

> **Contexto:** Producto estrella de portafolio. Plataforma de banca digital con arquitectura de
> microservicios, orientada a **banca chilena**, desplegada con URL pública 24/7 para que
> reclutadores puedan testear todo sin fricción, conservando el código de infraestructura
> AWS/EKS como evidencia de skill cloud.
>
> **Idiomas:** La aplicación es **multilenguaje español/inglés (i18n)** con **español (es-CL) por
> defecto**. **Toda la documentación del proyecto se escribe en español.**

## Glosario

- **RUT:** Rol Único Tributario. Identificador chileno de personas/empresas con dígito verificador (módulo 11).
- **CLP:** Peso chileno. Moneda sin decimales (scale = 0).
- **UF:** Unidad de Fomento. Unidad indexada a la inflación, usada en Chile (scale = 2 conceptual).
- **TEF/CCA:** Transferencia Electrónica de Fondos / Cámara de Compensación Automatizada (interbancaria chilena).
- **Minor units:** Representación entera del monto en su menor unidad (para CLP = pesos, para USD = centavos).
- **Ledger de doble entrada:** Registro contable donde cada transacción genera asientos balanceados (Σ débitos = Σ créditos).
- **Idempotency-Key:** Clave que garantiza que una operación repetida no se aplique más de una vez.
- **Outbox pattern:** Patrón de consistencia que persiste eventos en una tabla dentro de la misma transacción de negocio antes de publicarlos.
- **Saga:** Transacción distribuida coordinada por pasos con compensación ante fallo.
- **BFF:** Backend For Frontend. Capa de agregación (aquí, GraphQL en el gateway).

---

## Requisito 1: Modelo de dominio monetario y de identidad chileno

**Historia de usuario:** Como ingeniero del core bancario, quiero tipos de dominio robustos para dinero e identidad chilena, para que ningún monto se corrompa por errores de precisión y toda cuenta se asocie a un RUT válido.

### Criterios de aceptación (EARS)

1. CUANDO se cree un valor monetario, EL SISTEMA DEBERÁ representarlo con `BigDecimal` en minor units y una moneda asociada, y NUNCA con tipos de punto flotante (`double`/`float`).
2. CUANDO la moneda sea CLP, EL SISTEMA DEBERÁ usar scale = 0 (sin decimales).
3. CUANDO la moneda sea USD, EL SISTEMA DEBERÁ usar scale = 2.
4. CUANDO se soporte UF, EL SISTEMA DEBERÁ tratarla como unidad indexada con su propio scale y no permitir operarla contra CLP/USD sin conversión explícita.
5. CUANDO se sumen o resten dos valores monetarios, EL SISTEMA DEBERÁ rechazar la operación SI las monedas difieren.
6. CUANDO una operación produzca redondeo, EL SISTEMA DEBERÁ aplicar redondeo bancario (HALF_EVEN) según el scale de la moneda.
7. DONDE una cuenta no admita saldo negativo, EL SISTEMA DEBERÁ rechazar operaciones que dejarían el saldo por debajo de cero.
8. CUANDO se construya un RUT, EL SISTEMA DEBERÁ validar su dígito verificador mediante módulo 11 y rechazar RUT inválidos.
9. CUANDO se formatee un RUT para presentación, EL SISTEMA DEBERÁ entregarlo en formato chileno (ej. `12.345.678-5`).
10. CUANDO se comparen dos valores monetarios o dos RUT, EL SISTEMA DEBERÁ usar igualdad por valor (value objects inmutables).

---

## Requisito 2: Gestión de cuentas (account-service)

**Historia de usuario:** Como cliente, quiero crear y consultar cuentas bancarias asociadas a mi RUT, para operar con el banco digital.

### Criterios de aceptación (EARS)

1. CUANDO un cliente solicite crear una cuenta con RUT, tipo (corriente/vista/ahorro) y banco (código de plaza chilena), EL SISTEMA DEBERÁ persistir la cuenta y retornar su identificador.
2. SI el RUT es inválido, ENTONCES EL SISTEMA DEBERÁ rechazar la creación con un error de validación.
3. CUANDO se consulte una cuenta por identificador, EL SISTEMA DEBERÁ retornar sus datos y saldo.
4. CUANDO se listen cuentas de un RUT, EL SISTEMA DEBERÁ retornar todas las cuentas asociadas.
5. CUANDO ocurra un error, EL SISTEMA DEBERÁ responder con formato ProblemDetail (RFC 7807).
6. EL SISTEMA DEBERÁ exponer su contrato REST vía OpenAPI/Swagger.
7. EL SISTEMA DEBERÁ usar una base de datos PostgreSQL propia (database-per-service) con migraciones versionadas (Flyway).
8. CUANDO un mensaje de error se devuelva al usuario final, EL SISTEMA DEBERÁ soportar su localización en español (por defecto) e inglés según el idioma solicitado.

---

## Requisito 3: Ledger de doble entrada (ledger-service)

**Historia de usuario:** Como responsable de cumplimiento, quiero un ledger inmutable de doble entrada, para que todo movimiento sea auditable y los saldos sean siempre consistentes.

### Criterios de aceptación (EARS)

1. CUANDO se registre una transacción contable, EL SISTEMA DEBERÁ generar asientos de débito y crédito cuya suma sea exactamente cero.
2. SI una transacción no está balanceada (Σ ≠ 0), ENTONCES EL SISTEMA DEBERÁ rechazarla.
3. UNA VEZ registrado un asiento, EL SISTEMA DEBERÁ tratarlo como inmutable (sin updates ni deletes).
4. CUANDO se solicite el saldo de una cuenta, EL SISTEMA DEBERÁ derivarlo de la suma de sus asientos.
5. EL SISTEMA DEBERÁ usar una base de datos PostgreSQL propia con migraciones versionadas.

---

## Requisito 4: Transferencias idempotentes (transfer-service)

**Historia de usuario:** Como cliente, quiero transferir dinero de forma segura, para que un reintento por red inestable nunca duplique el movimiento.

### Criterios de aceptación (EARS)

1. CUANDO se reciba una transferencia con header `Idempotency-Key`, EL SISTEMA DEBERÁ persistir la clave junto al resultado.
2. CUANDO se reciba otra solicitud con la misma `Idempotency-Key`, EL SISTEMA DEBERÁ retornar el mismo resultado SIN aplicar un nuevo movimiento.
3. CUANDO dos solicitudes concurrentes usen la misma clave, EL SISTEMA DEBERÁ garantizar que solo se aplique un movimiento.
4. EL SISTEMA DEBERÁ manejar montos en CLP como moneda principal.
5. SI la cuenta origen no tiene fondos suficientes, ENTONCES EL SISTEMA DEBERÁ rechazar la transferencia.

---

## Requisito 5: Arquitectura orientada a eventos con Outbox (Kafka)

**Historia de usuario:** Como arquitecto, quiero consistencia entre la base de datos y Kafka, para que ningún evento se pierda ni se publique sin haberse confirmado en la base.

### Criterios de aceptación (EARS)

1. CUANDO transfer-service confirme una transferencia, EL SISTEMA DEBERÁ escribir el evento en una tabla outbox dentro de la misma transacción de base de datos.
2. CUANDO exista un evento pendiente en la outbox, EL SISTEMA DEBERÁ publicarlo a Kafka mediante un relay.
3. CUANDO ledger-service consuma un evento de transferencia, EL SISTEMA DEBERÁ generar el asiento correspondiente.
4. EL SISTEMA DEBERÁ versionar el esquema de los eventos.
5. UNA VEZ procesada una transferencia, LOS SALDOS del ledger DEBERÁN cuadrar con los movimientos.

---

## Requisito 6: Saga de transferencia (orquestación + compensación)

**Historia de usuario:** Como arquitecto, quiero coordinar la transferencia como una saga, para que un fallo parcial no deje saldos inconsistentes.

### Criterios de aceptación (EARS)

1. CUANDO se inicie una transferencia, EL SISTEMA DEBERÁ ejecutar los pasos: reservar fondos → asentar → confirmar.
2. SI cualquier paso falla, ENTONCES EL SISTEMA DEBERÁ ejecutar las compensaciones de los pasos ya completados.
3. UNA VEZ compensada una saga fallida, LOS SALDOS DEBERÁN quedar íntegros (como si la transferencia no hubiese ocurrido).
4. EL SISTEMA DEBERÁ persistir el estado de la saga.

---

## Requisito 7: Detección de fraude y notificaciones

**Historia de usuario:** Como equipo de riesgo, quiero detectar transferencias sospechosas y notificar, para mitigar fraude en tiempo casi real.

### Criterios de aceptación (EARS)

1. CUANDO fraud-service consuma un evento de transferencia, EL SISTEMA DEBERÁ aplicar reglas de monto y velocidad con umbrales realistas en CLP.
2. SI una transferencia supera un umbral de riesgo, ENTONCES EL SISTEMA DEBERÁ marcarla como sospechosa y emitir un evento.
3. CUANDO notification-service consuma un evento notificable, EL SISTEMA DEBERÁ enviar una notificación de forma asíncrona (mock de log/email).
4. CUANDO se genere una notificación al usuario, EL SISTEMA DEBERÁ soportar contenido en español (por defecto) e inglés.

---

## Requisito 8: Autenticación y autorización (Keycloak / OAuth2 / OIDC)

**Historia de usuario:** Como usuario, quiero autenticarme de forma segura con roles, para acceder solo a lo que me corresponde.

### Criterios de aceptación (EARS)

1. EL SISTEMA DEBERÁ usar Keycloak como Identity Provider (OIDC), con un realm versionado (realm-export.json).
2. EL SISTEMA DEBERÁ definir un client público para el frontend con flujo Authorization Code + PKCE, y clients confidential para los servicios.
3. EL SISTEMA DEBERÁ definir los roles CUSTOMER y ADMIN.
4. CUANDO un servicio reciba un request, EL SISTEMA DEBERÁ validar el JWT contra el JWKS de Keycloak.
5. SI un request a un endpoint protegido no incluye token válido, ENTONCES EL SISTEMA DEBERÁ responder 401.
6. SI un usuario autenticado carece del rol requerido, ENTONCES EL SISTEMA DEBERÁ responder 403.
7. EL SISTEMA DEBERÁ registrar auditoría de accesos.

---

## Requisito 9: API Gateway + GraphQL BFF + resiliencia

**Historia de usuario:** Como frontend, quiero un único punto de entrada que agregue datos y sea resiliente, para simplificar el consumo y tolerar fallos.

### Criterios de aceptación (EARS)

1. EL SISTEMA DEBERÁ enrutar el tráfico externo a los servicios internos vía Spring Cloud Gateway.
2. EL SISTEMA DEBERÁ propagar el token de autenticación a los servicios downstream.
3. EL SISTEMA DEBERÁ aplicar rate limiting en el gateway.
4. CUANDO un servicio downstream falle o exceda su timeout, EL SISTEMA DEBERÁ aplicar circuit breaker (Resilience4j) con fallback.
5. EL SISTEMA DEBERÁ exponer una capa GraphQL (BFF) que agregue datos REST internos (ej. `me { accounts, balances, history }`).
6. EL SISTEMA DEBERÁ exponer un playground GraphQL.

---

## Requisito 10: Frontend Next.js (dashboard bancario, i18n es/en)

**Historia de usuario:** Como cliente, quiero un dashboard claro en mi idioma, para ver mis cuentas y transferir con confianza.

### Criterios de aceptación (EARS)

1. CUANDO el usuario ingrese, EL SISTEMA DEBERÁ autenticarlo vía Keycloak (Authorization Code + PKCE).
2. EL SISTEMA DEBERÁ mostrar cuentas, saldos e historial consumiendo el BFF GraphQL.
3. CUANDO el usuario inicie una transferencia, EL SISTEMA DEBERÁ mostrar feedback de estado del proceso.
4. EL SISTEMA DEBERÁ ofrecer un auto-login guest / botón demo que ingrese sin registro.
5. EL SISTEMA DEBERÁ incluir un tour guiado para reclutadores.
6. EL SISTEMA DEBERÁ soportar i18n español/inglés, con **español (es-CL) por defecto**, y permitir cambiar de idioma.
7. CUANDO se muestren montos y fechas, EL SISTEMA DEBERÁ formatearlos según el locale activo (es-CL: miles con punto, ej. `$1.000.000`).

---

## Requisito 11: Observabilidad

**Historia de usuario:** Como operador, quiero métricas, trazas y logs, para diagnosticar el sistema en producción.

### Criterios de aceptación (EARS)

1. EL SISTEMA DEBERÁ exponer métricas con Micrometer scrapeables por Prometheus.
2. EL SISTEMA DEBERÁ ofrecer dashboards en Grafana (latencia, errores, throughput).
3. EL SISTEMA DEBERÁ emitir trazas distribuidas (OpenTelemetry → Jaeger) que crucen gateway → transfer → ledger.
4. EL SISTEMA DEBERÁ emitir logs estructurados (JSON) agregables (Loki).

---

## Requisito 12: Despliegue doble track

**Historia de usuario:** Como dueño del portafolio, quiero una demo pública siempre accesible y a la vez evidencia de skill cloud, para impresionar a reclutadores sin incurrir en costos permanentes.

### Criterios de aceptación (EARS)

1. EL SISTEMA DEBERÁ desplegar el frontend en Vercel y el backend en un PaaS de contenedores gratuito/barato (Render/Fly.io), accesible 24/7 vía HTTPS.
2. DONDE se ejecute el perfil `demo` público, EL SISTEMA DEBERÁ operar en configuración reducida (menos contenedores, un PostgreSQL con schemas, Kafka gestionado gratuito) sin perder funcionalidad testeable.
3. EL SISTEMA DEBERÁ incluir seed automático con cuentas y usuarios CUSTOMER/ADMIN demo usando datos chilenos realistas (RUT válidos, nombres y bancos chilenos).
4. EL SISTEMA DEBERÁ exponer Swagger y el playground GraphQL de forma pública.
5. EL SISTEMA DEBERÁ incluir código Terraform para EKS + RDS + MSK y manifiestos K8s/Helm, ejecutables on-demand.
6. EL SISTEMA DEBERÁ incluir una guía de levantar/derribar la infra AWS para evitar costos permanentes.
7. DONDE se ejecute en local o AWS, EL SISTEMA DEBERÁ operar el stack completo y fiel (database-per-service, todos los microservicios).

---

## Requisito 13: Documentación, seguridad y demo

**Historia de usuario:** Como reclutador, quiero entender y probar el proyecto en minutos, para evaluar la capacidad senior del autor.

### Criterios de aceptación (EARS)

1. EL SISTEMA DEBERÁ incluir un README (en español) con arquitectura, diagramas y enlaces a la demo.
2. EL SISTEMA DEBERÁ documentar ADRs (decisiones de diseño) en español.
3. EL SISTEMA DEBERÁ publicar documentación de API interactiva (OpenAPI/Swagger) y una colección de pruebas (Postman/Bruno).
4. EL SISTEMA DEBERÁ incluir una landing con tour guiado y credenciales demo visibles.
5. EL SISTEMA DEBERÁ pasar una revisión de seguridad (headers, CORS, manejo de secretos) y pruebas end-to-end.
6. TODA la documentación del proyecto DEBERÁ estar redactada en español.
