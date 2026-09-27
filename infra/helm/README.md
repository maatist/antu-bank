# Manifiestos Kubernetes / Helm — Antu Bank (NeoBank)

Chart de Helm para desplegar el **stack completo y fiel** de Antu Bank sobre Kubernetes,
pensado para **Amazon EKS**. Cubre la tarea **12b.1** y el **Requisito 12, criterio 5**
(_"incluir manifiestos K8s/Helm, ejecutables on-demand"_) honrando el **criterio 7**
(_"en local o AWS operar el stack completo: database-per-service estricto, todos los
microservicios"_).

> On-demand: este chart es **evidencia de skill cloud**. Se instala para demostrar el
> despliegue en AWS y se **desinstala** para no incurrir en costos permanentes (la demo
> pública 24/7 vive en el track de Render/Vercel — ver `infra/README-deploy.md`).

## Qué despliega

| Componente | Tipo | Puerto | Persistencia | Público |
|---|---|---|---|---|
| `api-gateway` | Deployment + Service (+ Ingress) | 8080 | — | Sí (Ingress) |
| `account-service` | Deployment + Service | 8082 | RDS `account` | No |
| `ledger-service` | Deployment + Service | 8083 | RDS `ledger` | No |
| `transfer-service` | Deployment + Service | 8084 | RDS `transfer` | No |
| `fraud-service` | Deployment + Service | 8085 | — (stateless) | No |
| `notification-service` | Deployment + Service | 8086 | — (stateless) | No |
| `keycloak` | Deployment + Service | 8080 | RDS `keycloak` | vía Ingress/host |

**Database-per-service estricto:** cada servicio con estado apunta a **su propia RDS**
(host y base distintos). No hay base compartida (eso es exclusivo del perfil `demo` público).

**Kafka:** los servicios producen/consumen contra **Amazon MSK** vía **SASL_SSL**.

## Estructura

```
infra/helm/
├── README.md                      # esta guía (en español)
└── antu-bank/                      # chart umbrella
    ├── Chart.yaml
    ├── .helmignore
    ├── values.yaml                 # defaults (stack completo y fiel)
    ├── values.example.yaml         # ejemplo para EKS (ECR + ALB + Secrets existentes)
    └── templates/
        ├── _helpers.tpl            # nombres, labels, referencia de imagen
        ├── configmap.yaml          # env NO sensible por servicio (perfil, issuer, MSK, DB URL/URI)
        ├── secret.yaml             # env SENSIBLE por servicio (opcional; preferir existingSecret)
        ├── deployment.yaml         # Deployment por servicio (probes /actuator/health, recursos)
        ├── service.yaml            # Service ClusterIP por servicio
        ├── servicemonitor.yaml     # ServiceMonitor por servicio (Prometheus Operator, opcional)
        ├── ingress.yaml            # Ingress del api-gateway (entrada pública)
        ├── keycloak.yaml           # Keycloak (Deployment + Service + Secret opcional)
        └── NOTES.txt               # resumen post-install
```

Es un **único chart umbrella** parametrizado por el map `services` de `values.yaml`: un solo
juego de plantillas genera los recursos de los 6 servicios. Habilitar/deshabilitar un servicio
es `services.<nombre>.enabled: true|false`. Esto evita duplicar plantillas por servicio y
mantiene la configuración en un solo lugar.

## Requisitos previos

- Un clúster **EKS** en marcha y `kubectl` apuntando a él (`aws eks update-kubeconfig ...`).
  La infraestructura (EKS, RDS por servicio, MSK) la provee **Terraform** (tarea 12b.2,
  `infra/terraform/`).
- **Helm 3** instalado.
- Imágenes de los 6 servicios publicadas en un registry accesible por el clúster (p. ej. **ECR**).
  Los `Dockerfile` por servicio son de la tarea 12a.1.
- Para el Ingress: **AWS Load Balancer Controller** (clase `alb`) o `ingress-nginx`.
- (Opcional) **kube-prometheus-stack** si quieres `ServiceMonitor` en vez de anotaciones.

## Manejo de secretos (nunca hardcodear)

Este chart **no contiene credenciales**. Las variables sensibles (passwords de RDS, JAAS de
MSK, admin de Keycloak) se toman de **Secrets de Kubernetes existentes**, referenciados con
`existingSecret`. Crea los Secrets fuera del chart, por ejemplo:

```bash
kubectl -n antu-bank create secret generic account-service-secrets \
  --from-literal=ACCOUNT_DB_PASSWORD='REEMPLAZAR' \
  --from-literal=KAFKA_SASL_JAAS_CONFIG='org.apache.kafka.common.security.scram.ScramLoginModule required username="REEMPLAZAR" password="REEMPLAZAR";'

kubectl -n antu-bank create secret generic ledger-service-secrets \
  --from-literal=LEDGER_DB_PASSWORD='REEMPLAZAR' \
  --from-literal=KAFKA_SASL_JAAS_CONFIG='...'

kubectl -n antu-bank create secret generic transfer-service-secrets \
  --from-literal=TRANSFER_DB_PASSWORD='REEMPLAZAR' \
  --from-literal=KAFKA_SASL_JAAS_CONFIG='...'

kubectl -n antu-bank create secret generic fraud-service-secrets \
  --from-literal=KAFKA_SASL_JAAS_CONFIG='...'

kubectl -n antu-bank create secret generic notification-service-secrets \
  --from-literal=KAFKA_SASL_JAAS_CONFIG='...'

kubectl -n antu-bank create secret generic keycloak-secrets \
  --from-literal=KC_BOOTSTRAP_ADMIN_USERNAME='admin' \
  --from-literal=KC_BOOTSTRAP_ADMIN_PASSWORD='REEMPLAZAR' \
  --from-literal=KC_DB_PASSWORD='REEMPLAZAR'
```

> En un entorno real, prefiere **External Secrets Operator** con **AWS Secrets Manager** o
> **SealedSecrets** en lugar de crear Secrets a mano. El chart solo necesita el nombre del
> Secret en `existingSecret`.

El realm de Keycloak se monta desde un **ConfigMap** (no se versiona dentro del chart):

```bash
kubectl -n antu-bank create configmap keycloak-realm \
  --from-file=antu-bank-realm.json=infra/keycloak/realm-export.json
```

## Instalar / actualizar

Desde la raíz del monorepo:

```bash
# 1) Copia el ejemplo y ajústalo (registry ECR, endpoints de RDS/MSK, hosts, existingSecret).
cp infra/helm/antu-bank/values.example.yaml infra/helm/antu-bank/values.prod.yaml
#    edita values.prod.yaml (NO lo subas al repo)

# 2) Crea los Secrets y el ConfigMap del realm (ver sección anterior).

# 3) Instala o actualiza el release (idempotente).
helm upgrade --install antu-bank infra/helm/antu-bank \
  --namespace antu-bank --create-namespace \
  -f infra/helm/antu-bank/values.prod.yaml
```

> El nombre de release recomendado es `antu-bank`: los defaults de `values.yaml` referencian
> los servicios internos por DNS `antu-bank-<servicio>` (p. ej. `antu-bank-ledger-service`).
> Si usas otro nombre de release, ajusta las URIs internas (`ACCOUNT_SERVICE_URI`, etc.) o
> fija `fullnameOverride: antu-bank`.

## Verificar

```bash
kubectl -n antu-bank get pods,svc,ingress
kubectl -n antu-bank rollout status deploy/antu-bank-api-gateway

# Probar el gateway sin Ingress (port-forward):
kubectl -n antu-bank port-forward svc/antu-bank-api-gateway 8080:8080
curl -s localhost:8080/actuator/health
```

## Desinstalar (teardown de la app)

```bash
helm uninstall antu-bank -n antu-bank
kubectl delete namespace antu-bank   # opcional: borra Secrets/ConfigMaps del namespace
```

> Esto elimina **solo la aplicación** del clúster. Para **derribar la infraestructura AWS**
> (EKS, RDS, MSK) y **evitar costos permanentes**, usa `terraform destroy` (tareas 12b.2 y
> 12b.4). El orden recomendado de teardown es: `helm uninstall` → borrar Ingress/ALB →
> `terraform destroy`. La **guía consolidada de spin-up/tear-down** de punta a punta (con
> checklist post-destroy e higiene de costos) está en
> [`infra/README-aws-ondemand.md`](../README-aws-ondemand.md) (tarea 12b.4).

## Mapeo a recursos AWS

| Recurso del chart | Recurso AWS (Terraform, tarea 12b.2) |
|---|---|
| Deployments/Services | Cargas en nodos de **EKS** |
| `ACCOUNT/LEDGER/TRANSFER_DB_URL` | Una instancia **RDS** por servicio (database-per-service) |
| `KC_DB_URL` (Keycloak) | Instancia **RDS** para Keycloak |
| `KAFKA_BOOTSTRAP_SERVERS` + SASL | Clúster **MSK** (SASL/SCRAM) |
| Ingress del `api-gateway` | **ALB** vía AWS Load Balancer Controller |
| `existingSecret` | (recomendado) **AWS Secrets Manager** vía External Secrets |

## Observabilidad (Requisito 11)

Cada Deployment expone `/actuator/prometheus`. Hay dos formas de recolectar métricas:

- **Anotaciones** `prometheus.io/scrape` en los pods (por defecto, `metrics.podAnnotations.enabled=true`)
  para un Prometheus "vanilla" configurado con service discovery de pods.
- **ServiceMonitor** del **Prometheus Operator** (`metrics.serviceMonitor.enabled=true`), si el
  clúster tiene instalado `kube-prometheus-stack`.

## Notas de diseño

- El chart usa el **perfil por defecto** de Spring (no el perfil `demo`), preservando el stack
  fiel: database-per-service estricto y todos los microservicios (Requisito 12, criterio 7).
- El SASL de MSK se configura por variables de entorno que Spring resuelve por _relaxed
  binding_ (`SPRING_KAFKA_SECURITY_PROTOCOL`, `SPRING_KAFKA_PROPERTIES_SASL_MECHANISM`), y el
  `sasl.jaas.config` (sensible) llega desde el Secret como `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG`.
  Se usan los nombres con prefijo `SPRING_KAFKA_*` (no `KAFKA_*`) porque en el perfil por
  defecto Spring los mapea a `spring.kafka.*` sin necesidad del perfil `demo`.
- Los contenedores corren como **no-root** (coherente con los `Dockerfile` que usan el usuario
  `spring`) con un `securityContext` restringido.
```
