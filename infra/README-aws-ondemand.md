# Guía de levantar/derribar la infra AWS on-demand (tarea 12b.4)

Guía **consolidada** del ciclo de vida completo del track **AWS on-demand** de Antu Bank:
cómo **levantarlo** (spin-up) para una demo y cómo **derribarlo por completo** (tear-down)
para **evitar costos permanentes**. Cubre la tarea **12b.4** y el **Requisito 12, criterio 6**
(_"incluir una guía de levantar/derribar la infra AWS para evitar costos permanentes"_).

Esta guía **no duplica** el detalle de cada herramienta: lo **orquesta** y enlaza a las guías
específicas ya existentes:

- **Terraform (infra):** [`infra/terraform/README.md`](terraform/README.md) — VPC, EKS, RDS x4
  (database-per-service), MSK SASL/SCRAM, ECR, Secrets Manager/KMS.
- **Helm (app):** [`infra/helm/README.md`](helm/README.md) — chart umbrella del stack completo.
- **CI/CD:** [`.github/README.md`](../.github/README.md) — build/push a ECR (OIDC) + deploy
  manual on-demand a EKS.

> **No ejecutes comandos AWS a ciegas.** Esta guía asume `aws`, `kubectl`, `helm` y `terraform`
> instalados y credenciales AWS configuradas. Todo comando de creación/borrado **cuesta o
> detiene costo real**: léelo antes de correrlo.

---

## 1. Dos tracks: cuál cuesta y por qué importa el teardown

El proyecto tiene **dos tracks de despliegue independientes** (Requisito 12):

| Track | Para qué | Dónde vive | Costo | ¿Se derriba? |
|---|---|---|---|---|
| **Demo pública 24/7** | URL pública siempre accesible para reclutadores | Render/Fly.io + Vercel — ver [`infra/README-deploy.md`](README-deploy.md) y [`infra/README-demo.md`](README-demo.md) | Gratis/barato, pensado para quedarse arriba | **No** (es always-on) |
| **AWS on-demand** | Evidencia de skill cloud (EKS + RDS + MSK + CI/CD) | Terraform + Helm + GitHub Actions (esta guía) | **Cuesta por hora** mientras esté arriba | **Sí, siempre** al terminar la demo |

El track AWS reproduce el **stack completo y fiel** (database-per-service estricto, los 6
microservicios, Kafka gestionado) pero **se paga por hora**. Por eso se levanta solo para
demostrar y **se derriba en cuanto termina**.

### Recursos que cobran por hora (por qué el teardown es obligatorio)

| Recurso AWS | Cómo se crea | Nota de costo |
|---|---|---|
| **EKS** (control plane) | `terraform apply` (`eks.tf`) | Tarifa horaria fija por clúster, esté ocioso o no. |
| **Node group** (EC2, spot) | `terraform apply` (`eks.tf`) | Instancias EC2 de los nodos. |
| **NAT Gateway(s)** | `terraform apply` (VPC en `main.tf`) | Cobran por hora **y** por GB procesado. Fuente típica de costo "olvidado". |
| **MSK** (brokers) | `terraform apply` (`msk.tf`) | Brokers `kafka.t3.small` por hora + almacenamiento. |
| **RDS x4** (`account`, `ledger`, `transfer`, `keycloak`) | `terraform apply` (`rds.tf`) | Cuatro instancias `db.t4g.micro` por hora + EBS. |
| **EBS** (volúmenes) | Nodos EKS y RDS | Persisten si algo queda huérfano tras el destroy. |
| **ALB** (Application Load Balancer) | **NO** lo crea Terraform: lo crea el **AWS Load Balancer Controller** al aplicar el **Ingress** del chart Helm | Por hora + LCU. **Debe borrarse antes** del `terraform destroy` (ver §3). |

> El ALB es el caso especial: no lo maneja Terraform, sino Kubernetes (al crear el Ingress).
> Si se destruye la infra sin borrar antes el Ingress, el ALB y sus ENIs quedan **huérfanos**
> y **bloquean** el borrado de la VPC. Por eso el orden de teardown importa.

---

## 2. SPIN-UP — levantar el stack de punta a punta

Orden: **infra (Terraform) → imágenes (ECR) → secretos K8s + realm → app (Helm) → URL →
smoke test**. Cada paso enlaza a su guía detallada.

### Paso 1 — Provisionar la infra con Terraform

Sigue [`infra/terraform/README.md`](terraform/README.md) (sección _"Levantar la infra"_). Resumen:

```bash
cd infra/terraform
cp variables.example.tfvars terraform.tfvars   # ajusta región, tamaños, CIDRs (NO se versiona)
terraform init
terraform plan
terraform apply                                 # ~20-30 min por EKS/MSK
```

Configura `kubectl` contra el clúster recién creado:

```bash
# El comando exacto lo entrega el propio output:
terraform output -raw cluster_region_kubeconfig_cmd
aws eks update-kubeconfig --region <region> --name "$(terraform output -raw cluster_name)"
```

Recoge los datos que alimentan el `values` del chart (mapeo completo output→values en el
README de Terraform):

```bash
terraform output ecr_registry
terraform output rds_jdbc_urls
terraform output -raw msk_bootstrap_brokers_sasl_scram
terraform output rds_secret_arns
terraform output -raw msk_scram_secret_arn
```

### Paso 2 — Publicar las 6 imágenes en ECR

Dos opciones (detalle en [`.github/README.md`](../.github/README.md)):

- **Recomendado (CI/CD):** push a `main` dispara el job `build-and-push` de
  [`.github/workflows/cd.yml`](../.github/workflows/cd.yml), que construye y publica las 6
  imágenes en ECR vía OIDC. Publicar imágenes **no** levanta el clúster (es barato).
- **Manual (local):** login a ECR y build/push por servicio, usando la **raíz del monorepo**
  como contexto de build:

  ```bash
  aws ecr get-login-password --region <region> \
    | docker login --username AWS --password-stdin "$(terraform output -raw ecr_registry)"

  REGISTRY="$(terraform output -raw ecr_registry)"
  for s in api-gateway account-service ledger-service transfer-service fraud-service notification-service; do
    docker build -f "services/$s/Dockerfile" -t "$REGISTRY/antubank/$s:latest" .
    docker push "$REGISTRY/antubank/$s:latest"
  done
  ```

### Paso 3 — Crear los Secrets de Kubernetes y el ConfigMap del realm

El chart **no contiene credenciales**: las toma de Secrets existentes (`existingSecret`) y del
ConfigMap del realm. Sigue la sección _"Manejo de secretos"_ de
[`infra/helm/README.md`](helm/README.md). Los passwords se leen desde Secrets Manager (no de
outputs de Terraform), p. ej.:

```bash
aws secretsmanager get-secret-value \
  --secret-id "$(terraform output -raw msk_scram_secret_arn)" \
  --query SecretString --output text
```

Crea el namespace, los Secrets por servicio y el ConfigMap del realm:

```bash
kubectl create namespace antu-bank
# ... crear los *-service-secrets y keycloak-secrets (ver infra/helm/README.md) ...
kubectl -n antu-bank create configmap keycloak-realm \
  --from-file=antu-bank-realm.json=infra/keycloak/realm-export.json
```

### Paso 4 — Instalar la app con Helm

Sigue la sección _"Instalar / actualizar"_ de [`infra/helm/README.md`](helm/README.md):

```bash
cp infra/helm/antu-bank/values.example.yaml infra/helm/antu-bank/values.prod.yaml
#   edita values.prod.yaml con los outputs del Paso 1 (registry, RDS/MSK, hosts, existingSecret)
helm upgrade --install antu-bank infra/helm/antu-bank \
  --namespace antu-bank --create-namespace \
  -f infra/helm/antu-bank/values.prod.yaml
```

> Alternativa CI/CD: **Actions → CD → Run workflow** con **deploy = true** (job `deploy`
> protegido por el Environment `aws-eks`). Ver [`.github/README.md`](../.github/README.md).

### Paso 5 — Obtener la URL pública y smoke test

El Ingress del `api-gateway` provisiona un ALB. Obtén el hostname y prueba salud:

```bash
kubectl -n antu-bank get ingress
kubectl -n antu-bank rollout status deploy/antu-bank-api-gateway

ALB_HOST="$(kubectl -n antu-bank get ingress -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}')"
curl -s "http://$ALB_HOST/actuator/health"
```

Smoke test funcional (Swagger, GraphiQL, OIDC discovery del realm `antu-bank`), análogo al
checklist de la demo en [`infra/README-deploy.md`](README-deploy.md).

---

## 3. TEAR-DOWN — derribar todo y detener el cobro

**Orden crítico: app primero, infra después.** El ALB lo creó Kubernetes, no Terraform, así
que hay que quitarlo **antes** del `terraform destroy` para no dejar ENIs que bloqueen el
borrado de la VPC.

### Paso 1 — Desinstalar la app y borrar el Ingress/namespace (elimina el ALB)

```bash
helm uninstall antu-bank -n antu-bank
kubectl delete namespace antu-bank        # borra Ingress → el LB Controller elimina el ALB
```

Confirma que el ALB desapareció **antes** de seguir (puede tardar 1-2 min en reconciliar):

```bash
aws elbv2 describe-load-balancers \
  --query "LoadBalancers[?VpcId=='$(terraform -chdir=infra/terraform output -raw vpc_id)'].LoadBalancerArn" \
  --output text
# Debe salir vacío. Si no, espera y reintenta antes del destroy.
```

### Paso 2 — Destruir la infra AWS con Terraform

```bash
cd infra/terraform
terraform destroy        # elimina EKS, node group, RDS x4, MSK, NAT, VPC, ECR (~20-30 min)
```

El teardown limpio ya está preparado en el código Terraform (ver `infra/terraform/README.md`):

- `rds_deletion_protection = false` y `skip_final_snapshot = true` → las RDS se borran sin
  bloqueo ni snapshot final.
- `force_delete = true` en ECR → los repos se borran aunque tengan imágenes.

### Paso 3 — Checklist post-destroy: cazar costo huérfano

`terraform destroy` borra lo que gestiona, pero recursos creados **fuera** de Terraform (ALB,
sus ENIs) o dejados en limbo pueden seguir cobrando. Revisa uno por uno:

- [ ] **Load Balancers / Target Groups** (ALB creado por el Ingress) — deben estar eliminados
      (Paso 1). LCU cobra por hora.
- [ ] **ENIs** huérfanas del ALB — impiden borrar la VPC si quedan; suelen irse al borrar el ALB.
- [ ] **NAT Gateways** — cobran por hora aunque estén ociosos.
- [ ] **Elastic IPs** — una EIP **sin asociar** cobra por hora.
- [ ] **Volúmenes EBS** disponibles (`available`) — de nodos EKS o RDS que no se liberaron.
- [ ] **Snapshots de RDS/EBS** — no deberían existir (`skip_final_snapshot=true`), pero verifícalo.
- [ ] **Imágenes ECR** — el repo se borra con `force_delete`; confirma que no quedó ninguno.
- [ ] **Secretos en Secrets Manager** — quedan en estado _"scheduled for deletion"_ unos días
      (ventana de recuperación); es esperado y no cobra tras el borrado.
- [ ] **CloudWatch Log Groups** (`/aws/eks/...`, RDS, MSK) — el almacenamiento de logs cobra;
      bórralos si no los necesitas.
- [ ] **KMS key** de MSK — queda en _pending deletion_ (ventana obligatoria); no cobra tras eso.

---

## 4. Higiene de costos — verificar que no queda nada billable

Comandos de solo lectura (`aws cli`) para auditar recursos con costo. Sustituye `<region>`.

```bash
# NAT Gateways activos:
aws ec2 describe-nat-gateways --region <region> \
  --filter Name=state,Values=available \
  --query "NatGateways[].NatGatewayId" --output text

# Elastic IPs SIN asociar (cobran):
aws ec2 describe-addresses --region <region> \
  --query "Addresses[?AssociationId==null].PublicIp" --output text

# Volúmenes EBS disponibles (huérfanos):
aws ec2 describe-volumes --region <region> \
  --filters Name=status,Values=available \
  --query "Volumes[].VolumeId" --output text

# Load balancers restantes:
aws elbv2 describe-load-balancers --region <region> \
  --query "LoadBalancers[].LoadBalancerName" --output text

# Instancias RDS restantes:
aws rds describe-db-instances --region <region> \
  --query "DBInstances[].DBInstanceIdentifier" --output text

# Clústeres MSK restantes:
aws kafka list-clusters --region <region> \
  --query "ClusterInfoList[].ClusterName" --output text

# Clústeres EKS restantes:
aws eks list-clusters --region <region> --query "clusters" --output text

# Snapshots RDS manuales:
aws rds describe-db-snapshots --region <region> --snapshot-type manual \
  --query "DBSnapshots[].DBSnapshotIdentifier" --output text
```

Si todos los comandos anteriores salen **vacíos**, no queda nada cobrando por hora.

### Cost Explorer y presupuestos

- Activa **AWS Cost Explorer** para ver el gasto por servicio (EKS, EC2-Other/NAT, RDS, MSK)
  y confirmar que cae a ~0 tras el teardown (el dato tarda hasta 24 h en consolidarse).
- Crea un **AWS Budget** con alerta (p. ej. aviso al superar unos pocos USD/día) para que un
  recurso olvidado no pase desapercibido.

### Gotchas y tiempos

- **El ALB debe morir antes del destroy:** el AWS Load Balancer Controller borra el ALB al
  eliminar el Ingress/namespace. Sin eso, las ENIs impiden borrar la VPC y `terraform destroy`
  falla.
- **RDS ya está listo para teardown limpio:** `deletion_protection=false` + `skip_final_snapshot=true`.
  En un entorno **real** actívalos al revés (protección + snapshot final).
- **EKS y MSK son lentos:** el `apply` y el `destroy` tardan **~20-30 min** cada uno; es normal.
- **Secrets Manager y KMS** no se borran de inmediato: quedan en ventana de recuperación
  (días). Es esperado y no genera cobro tras el borrado.
- **CI barato:** el workflow de CI ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml))
  **nunca** toca AWS; solo el job `deploy` de `cd.yml` (manual) levanta costo.
