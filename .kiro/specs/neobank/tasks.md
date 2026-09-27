# Plan de Implementación — NeoBank

> Cada tarea es un incremento demoable y testeable, construido de forma incremental. Marcar
> `[x]` al completar. Los `_Requisitos_` referencian `requirements.md`. Verificar build/tests
> tras cada tarea antes de avanzar. Contexto banca chilena e i18n es/en (es-CL default) aplican
> transversalmente; toda la documentación en español.

- [x] 1. Fundaciones del monorepo, dominio compartido e infra local
  - [x] 1.1 Configurar Gradle multi-módulo (Kotlin DSL): `settings.gradle.kts`, convención raíz (`build-logic` convention plugins), `gradle/libs.versions.toml` (version catalog), Java 21 (toolchain).
  - [x] 1.2 Crear módulo `common-domain` (dominio puro, sin Spring): enum `Currency` (CLP scale=0, USD scale=2, UF scale=2).
  - [x] 1.3 Implementar value object `Money` (`BigDecimal`, normalización por scale, `HALF_EVEN`, `ofMajor`/`ofMinor`, `plus`/`minus` con misma moneda, `isNegative`, igualdad por valor).
  - [x] 1.4 Implementar value object `Rut` (parseo, validación DV módulo 11, `format()` chileno `12.345.678-5`, igualdad por valor).
  - [x] 1.5 Modelos base compartidos (`AccountType`, `ChileanBank`) según diseño.
  - [x] 1.6 `infra/docker-compose.yml` con PostgreSQL (una DB/instancia por servicio), Kafka (Apache oficial, KRaft) y Keycloak (+ realm-export.json base). **Verificado:** los 5 contenedores arrancan healthy, Kafka responde y Keycloak importa el realm `antu-bank` (OIDC discovery HTTP 200).
  - [x] 1.7 Tests unitarios: `Money` (suma, resta, redondeo bancario, rechazo negativos donde aplique, igualdad, scale CLP vs USD, distinta moneda falla) y `Rut` (DV válido/inválido, formateo).
  - **Demo:** `docker compose -f infra/docker-compose.yml up -d` levanta infra local (verificado); `./gradlew test` en verde (verificado).
  - _Requisitos: 1_

- [x] 2. account-service — CRUD de cuentas y persistencia (contexto chileno)
  - [x] 2.1 Módulo Spring Boot 3 + JPA; base PostgreSQL propia; Flyway.
  - [x] 2.2 Entidad `Account` (RUT titular, nombre, tipo corriente/vista/ahorro, banco por código de plaza chilena, moneda). El saldo NO se guarda aquí (fuente de verdad: ledger-service).
  - [x] 2.3 REST: `POST /accounts`, `GET /accounts/{id}`, `GET /accounts?rut=`; Bean Validation con validador de RUT (`@ValidRut`).
  - [x] 2.4 Manejo de errores `ProblemDetail` (RFC 7807) con mensajes localizables es/en (`MessageSource`, `Accept-Language`).
  - [x] 2.5 OpenAPI/Swagger (springdoc).
  - [x] 2.6 Tests de integración con Testcontainers (PostgreSQL real) + slices web/repositorio. **6 tests en verde.**
  - **Demo:** crear/consultar cuenta con RUT vía REST + Swagger; tests verdes (verificado). Nota: se fijó Testcontainers 1.21.4 en el convention plugin por compatibilidad con Docker Engine 29+.
  - _Requisitos: 2_

- [x] 3. ledger-service — contabilidad de doble entrada
  - [x] 3.1 Módulo + PostgreSQL propia + Flyway.
  - [x] 3.2 Entidades `LedgerTransaction` y `LedgerEntry` (débito/crédito); invariante Σ = 0 en dominio y constraint.
  - [x] 3.3 Asientos inmutables (sin update/delete); saldo derivado de asientos.
  - [x] 3.4 REST para registrar transacción y consultar saldo.
  - [x] 3.5 Tests: rechazo de asientos desbalanceados, cálculo de saldo, inmutabilidad (Testcontainers).
  - **Demo:** registrar asientos, ver saldo consistente; test de rechazo de inválidos.
  - _Requisitos: 3_

- [x] 4. transfer-service — transferencias idempotentes (síncrono)
  - [x] 4.1 Módulo + PostgreSQL propia + Flyway; entidad `Transfer` + `IdempotencyKey` (única).
  - [x] 4.2 Endpoint `POST /transfers` con header `Idempotency-Key`; misma key = mismo resultado sin duplicar.
  - [x] 4.3 Validación de fondos; invocación directa a ledger (aún sin Kafka) para flujo end-to-end temprano; montos en CLP.
  - [x] 4.4 Tests de concurrencia (doble envío = un movimiento) + integración.
  - **Demo:** misma transferencia dos veces se aplica una sola.
  - _Requisitos: 4_

- [x] 5. Event-driven con Kafka + Outbox pattern
  - [x] 5.1 Tabla `outbox` en transfer-service; escribir evento en la misma transacción de negocio.
  - [x] 5.2 Relay que publica pendientes a Kafka y marca publicado; serialización de eventos versionada.
  - [x] 5.3 ledger-service consume evento de transferencia y genera el asiento.
  - [x] 5.4 Tests con Testcontainers Kafka (evento → asiento; outbox garantiza publicación).
  - **Demo:** una transferencia genera eventos consumidos por el ledger; saldos cuadran.
  - _Requisitos: 5_

- [x] 6. Saga de transferencia (orquestación + compensación)
  - [x] 6.1 Orquestador con estado persistido: reservar fondos → asentar → confirmar.
  - [x] 6.2 Compensaciones por paso ante fallo (rollback lógico).
  - [x] 6.3 Tests de camino feliz y de compensación (saldos íntegros tras fallo).
  - **Demo:** transferencia exitosa completa; fallida se compensa sin inconsistencias.
  - _Requisitos: 6_

- [x] 7. fraud-service + notification-service
  - [x] 7.1 fraud-service consume eventos de transferencia; reglas de monto y velocidad (umbrales realistas CLP); emite `TransferFlagged`.
  - [x] 7.2 notification-service consume eventos notificables; notificación async (mock log/email); contenido localizable es/en.
  - [x] 7.3 Tests de reglas de fraude y consumo de eventos.
  - **Demo:** transferencia sospechosa marcada + notificación disparada.
  - _Requisitos: 7_

- [x] 8. Integración de Keycloak (OAuth2/OIDC + JWT + roles)
  - [x] 8.1 `infra/keycloak/realm-export.json` versionado: realm `neobank`, client público frontend (Auth Code + PKCE), clients confidential por servicio, roles CUSTOMER/ADMIN.
  - [x] 8.2 Servicios como resource servers validando JWT vía JWKS.
  - [x] 8.3 Auditoría de accesos.
  - [x] 8.4 Documentación extra de Keycloak (usuario nuevo): conceptos y guía de setup en español.
  - [x] 8.5 Tests de autorización (401 sin token, 403 sin rol) y validación JWT.
  - **Demo:** login en Keycloak, obtener JWT, acceder según rol.
  - _Requisitos: 8_

- [x] 9. api-gateway (Spring Cloud Gateway) + GraphQL BFF + resiliencia
  - [x] 9.1 Enrutamiento a servicios + propagación de token.
  - [x] 9.2 Rate limiting + circuit breaker (Resilience4j) con fallback.
  - [x] 9.3 Capa GraphQL (Spring for GraphQL) que agrega REST internos (`me { accounts, balances, history }`) + playground/GraphiQL.
  - [x] 9.4 Tests de rutas, circuit breaker con fallback y resolución GraphQL.
  - **Demo:** tráfico por gateway; GraphQL agrega cuentas+saldos+historial en una query; rate limit y fallback OK.
  - _Requisitos: 9_

- [x] 10. Frontend Next.js (dashboard bancario, i18n es/en)
  - [x] 10.1 Login vía Keycloak (Auth Code + PKCE).
  - [x] 10.2 Vistas: cuentas/saldos, historial, iniciar transferencia con feedback de estado; consumo del BFF GraphQL.
  - [x] 10.3 i18n es/en (es-CL por defecto) con selector de idioma; formateo `es-CL` de montos/fechas.
  - [x] 10.4 Auto-login guest / botón demo + tour guiado para reclutadores.
  - [x] 10.5 Tests de componentes clave y flujo de transferencia (Testing Library).
  - **Demo:** login o guest, ver saldo, transferir, ver historial actualizado.
  - _Requisitos: 10_

- [x] 11. Observabilidad (métricas, trazas, logs)
  - [x] 11.1 Micrometer + Prometheus; dashboards Grafana (latencia, errores, throughput).
  - [x] 11.2 Trazas OpenTelemetry → Jaeger que crucen gateway → transfer → ledger.
  - [x] 11.3 Logs estructurados JSON + Loki con correlación por trace-id.
  - **Demo:** dashboard de latencia/errores + traza que cruza gateway→transfer→ledger.
  - _Requisitos: 11_

- [x] 12. Despliegue
  - [x] 12a. Contenerización y despliegue público always-on (portafolio)
    - [x] 12a.1 Dockerfile por servicio (multi-stage, Java 21).
    - [x] 12a.2 Perfil `demo` reducido: consolidación de contenedores, 1 PostgreSQL con schemas, Kafka gestionado free (Upstash/Redpanda).
    - [x] 12a.3 Deploy backend en Render/Fly.io + frontend en Vercel (HTTPS).
    - [x] 12a.4 Seed automático (cuentas + usuarios CUSTOMER/ADMIN demo con datos chilenos: RUT válidos, nombres y bancos reales).
    - [x] 12a.5 Swagger y playground GraphQL públicos.
    - **Demo:** URL pública HTTPS; auto-login guest, testear transferencias, ver Swagger/GraphQL.
    - _Requisitos: 12_
  - [x] 12b. Infra AWS + Kubernetes + CI/CD (on-demand, evidencia de skill)
    - [x] 12b.1 Manifiestos K8s/Helm por servicio.
    - [x] 12b.2 Terraform para EKS + RDS + MSK.
    - [x] 12b.3 Pipeline GitHub Actions (build, test, imagen a registry, deploy).
    - [x] 12b.4 Guía de levantar/derribar para evitar costos permanentes.
    - **Demo:** `terraform apply` levanta EKS + app; pipeline despliega en push; guía de teardown.
    - _Requisitos: 12_

- [x] 13. Documentación pro + demo + hardening final
  - [x] 13.1 README (español) con arquitectura, diagramas y enlaces a la demo.
  - [x] 13.2 ADRs (español) consolidados.
  - [x] 13.3 OpenAPI/Swagger navegable + colección de pruebas (Postman/Bruno).
  - [x] 13.4 Landing con tour guiado y credenciales demo visibles.
  - [x] 13.5 Revisión de seguridad (headers, CORS, manejo de secretos) + pruebas end-to-end.
  - **Demo:** README navegable + demo desplegada + docs de API interactivas + tour para reclutadores.
  - _Requisitos: 13_
