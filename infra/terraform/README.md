# Terraform — Infra AWS on-demand (EKS + RDS + MSK) — Antu Bank

Código Terraform que provisiona, **on-demand**, la infraestructura AWS para desplegar el
**stack completo y fiel** de Antu Bank. Cubre la tarea **12b.2** y el **Requisito 12,
criterio 5** (_"incluir código Terraform para EKS + RDS + MSK... ejecutables on-demand"_),
honrando el **criterio 7** (_database-per-service estricto_) y el **criterio 6** (guía de
levantar/derribar para evitar costos permanentes).

> **On-demand / evidencia de skill cloud.** Esta infra se **levanta** para demostrar el
> despliegue en AWS y se **derriba** cuando no se usa: MSK, EKS, NAT y RDS **cuestan por
> hora**. La demo pública 24/7 vive en el track de Render/Vercel (ver `infra/README-deploy.md`).
> La **guía consolidada de levantar/derribar** del track AWS on-demand (spin-up + tear-down
> de punta a punta, más checklist de costo) está en
> [`infra/README-aws-ondemand.md`](../README-aws-ondemand.md) (tarea 12b.4).

## Qué provisiona

| Recurso | Detalle |
|---|---|
| **VPC** | Subredes públicas (ALB/NAT) y privadas (nodos EKS, RDS, MSK) en 2+ AZ, NAT, route tables. Módulo `terraform-aws-modules/vpc`. |
| **EKS** | Cluster + un managed node group (spot pequeño por defecto). Módulo `terraform-aws-modules/eks`. Addons: coredns, kube-proxy, vpc-cni, pod-identity. |
| **RDS** | **Una instancia PostgreSQL por servicio con estado**: `account`, `ledger`, `transfer` y `keycloak` (database-per-service estricto). En subredes privadas, cifradas. |
| **MSK** | Cluster Kafka con **SASL/SCRAM (SCRAM-SHA-512)** sobre TLS (puerto 9096). Credenciales en Secrets Manager cifradas con KMS propia. |
| **ECR** | Un repositorio por cada uno de los 6 servicios (`antubank/<servicio>`), con scan-on-push y lifecycle policy. |
| **Secrets Manager** | Passwords de RDS (por servicio) y credenciales SASL/SCRAM de MSK. Generados con `random_password` (sensibles). **Nunca hardcodeados.** |

**Database-per-service estricto:** `var.databases` define un mapa servicio → instancia RDS.
Cada entrada crea su propia instancia con host, base y usuario distintos. No hay base
compartida (eso es exclusivo del perfil `demo` público).

## Estructura

```
infra/terraform/
├── README.md                    # esta guía (en español)
├── versions.tf                  # required_version, providers, backend S3 (comentado)
├── variables.tf                 # variables con defaults sensatos (portafolio)
├── variables.example.tfvars     # plantilla de valores → copiar a terraform.tfvars
├── main.tf                      # provider, locals, VPC
├── eks.tf                       # cluster EKS + managed node group
├── security.tf                  # security groups de RDS y MSK (acotados a EKS)
├── rds.tf                       # RDS por servicio + secretos
├── msk.tf                       # MSK SASL/SCRAM + KMS + secreto asociado
├── ecr.tf                       # repositorios ECR de los 6 servicios
└── outputs.tf                   # salidas mapeadas a los values del chart Helm
```

## Requisitos previos

- **Terraform** >= 1.6 (`terraform version`).
- **AWS CLI** configurada con credenciales que puedan crear VPC/EKS/RDS/MSK/ECR/Secrets/KMS.
- Región por defecto `us-east-1` (parametrizable con `var.region`).
- (Opcional, recomendado en equipo/CI) backend S3 + DynamoDB para estado remoto: descomenta
  el bloque `backend "s3"` en `versions.tf` y ejecuta `terraform init -reconfigure`.

## Manejo de secretos (nunca hardcodear)

- Los passwords de las RDS y de SASL/SCRAM de MSK se **generan** con `random_password`
  (marcados `sensitive`) y se guardan en **AWS Secrets Manager**.
- El estado de Terraform contiene valores sensibles: **protégelo** (backend S3 cifrado +
  acceso restringido). No lo subas al repo (`.gitignore` ya excluye `*.tfstate*` y `*.tfvars`
  salvo `*.example.tfvars`).
- Los `outputs` **no** emiten passwords en claro: exponen ARNs de los secretos para que el
  chart los consuma (idealmente vía External Secrets Operator).

## Levantar la infra (spin-up)

Desde `infra/terraform/`:

```bash
# 1) Copia la plantilla de variables y ajústala (región, tamaños, CIDRs permitidos).
cp variables.example.tfvars terraform.tfvars
#    edita terraform.tfvars (NO se versiona)

# 2) Inicializa (descarga providers y módulos).
terraform init

# 3) Revisa el plan.
terraform plan

# 4) Aplica (crea VPC, EKS, RDS x4, MSK, ECR). Tarda ~20-30 min por EKS/MSK.
terraform apply
```

Al terminar, conecta `kubectl` y sigue con Helm (tarea 12b.1):

```bash
# Configura kubectl contra el cluster (usa el output del comando).
terraform output -raw cluster_region_kubeconfig_cmd
aws eks update-kubeconfig --region <region> --name <cluster_name>

# Consulta los datos para el values del chart:
terraform output ecr_registry
terraform output rds_jdbc_urls
terraform output -raw msk_bootstrap_brokers_sasl_scram
```

### Mapeo output → values del chart Helm

| Output de Terraform | Valor en `infra/helm/antu-bank/values.prod.yaml` |
|---|---|
| `ecr_registry` | `image.registry` |
| `rds_jdbc_urls["account"]` | `services.account-service.env.ACCOUNT_DB_URL` |
| `rds_jdbc_urls["ledger"]` | `services.ledger-service.env.LEDGER_DB_URL` |
| `rds_jdbc_urls["transfer"]` | `services.transfer-service.env.TRANSFER_DB_URL` |
| `rds_jdbc_urls["keycloak"]` | `keycloak.env.KC_DB_URL` |
| `msk_bootstrap_brokers_sasl_scram` | `global.env.KAFKA_BOOTSTRAP_SERVERS` |
| `rds_secret_arns`, `msk_scram_secret_arn` | fuente para los `existingSecret` (vía External Secrets / AWS Secrets Manager) |

Los passwords se leen desde Secrets Manager (no desde outputs), p. ej.:

```bash
aws secretsmanager get-secret-value --secret-id "$(terraform output -raw msk_scram_secret_arn)" \
  --query SecretString --output text
```

## Derribar la infra (TEARDOWN — evita costos permanentes)

Orden recomendado (primero la app, luego la infra):

```bash
# 1) Desinstala la app y borra el Ingress para que se elimine el ALB creado por el controller.
helm uninstall antu-bank -n antu-bank
kubectl delete namespace antu-bank    # borra Ingress/Secrets/ConfigMaps del namespace

# 2) Destruye toda la infra AWS (EKS, RDS x4, MSK, NAT, VPC, ECR).
terraform destroy
```

> **Importante:**
> - Borra el **Ingress** antes de `terraform destroy`; si queda un ALB "huérfano" creado por
>   el AWS Load Balancer Controller, Terraform no podrá eliminar la VPC (dependencias de ENIs).
> - `rds_deletion_protection = false` y `skip_final_snapshot = true` permiten un teardown
>   limpio on-demand. En un entorno real, activa la protección y snapshots finales.
> - `force_delete = true` en ECR permite borrar repos aunque tengan imágenes.
> - Tras `terraform destroy`, verifica en la consola que no queden **NAT Gateways**, **EBS**,
>   **snapshots** ni **secretos** con costo. Los secretos de Secrets Manager pueden quedar en
>   estado "scheduled for deletion" unos días.

## Notas de diseño

- **Portafolio-first:** node group **spot**, instancias RDS `db.t4g.micro`, MSK
  `kafka.t3.small`, un solo NAT. Todo parametrizable en `variables.tf`.
- **Seguridad:** RDS y MSK viven en **subredes privadas** y solo aceptan tráfico desde el
  security group de los nodos EKS. Sin acceso público. Secretos vía Secrets Manager + KMS.
- **MSK SCRAM:** MSK exige que el secreto SCRAM se cifre con una **KMS key propia** (no la
  default) y que su nombre empiece con `AmazonMSK_`; ambos requisitos están contemplados.
- **Estado fiel (criterio 7):** una RDS por servicio (database-per-service estricto), sin
  base compartida, coherente con el perfil `default` del chart Helm.
```
