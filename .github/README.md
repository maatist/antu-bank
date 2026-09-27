# CI/CD — GitHub Actions (Antu Bank / NeoBank)

Automatización de **integración continua** y **publicación/despliegue on-demand** del
monorepo. Cubre la tarea **12b.3** del plan y el **Requisito 12** (despliegue doble track:
demo pública 24/7 + evidencia de skill cloud AWS **on-demand** para no incurrir en costos
permanentes).

Toda la documentación está en español, siguiendo la convención del proyecto.

## Workflows

| Workflow | Archivo | Cuándo corre | Qué hace |
|---|---|---|---|
| **CI** | `.github/workflows/ci.yml` | push y pull request (cualquier rama) | Compila y testea el backend (Gradle, Java 21, Testcontainers) y el frontend (Next.js: lint + test + build). No toca AWS. |
| **CD** | `.github/workflows/cd.yml` | push a `main`, tags `v*`, o manual | Construye y publica las 6 imágenes en **Amazon ECR**. Opcionalmente despliega a **EKS** con Helm **solo de forma manual**. |

### CI (`ci.yml`)

Dos jobs independientes que corren en paralelo:

- **backend**: `actions/setup-java` (Temurin 21) + cache de Gradle, ejecuta `./gradlew build`
  (compila + tests unitarios e integración). Los tests con **Testcontainers** funcionan
  porque el runner `ubuntu-latest` trae Docker preinstalado. Publica los reportes de test
  como artefacto.
- **frontend**: `actions/setup-node` (Node 22) con cache de npm, ejecuta `npm ci`, `npm run
  lint`, `npm test` (Vitest) y `npm run build` en `frontend/`.

### CD (`cd.yml`)

- **build-and-push**: matriz sobre los **6 servicios** (`api-gateway`, `account-service`,
  `ledger-service`, `transfer-service`, `fraud-service`, `notification-service`). Cada uno se
  construye con su `services/<servicio>/Dockerfile` usando **la raíz del monorepo como
  contexto** (lo exige el Dockerfile: necesita `settings.gradle.kts`, `build-logic`, el version
  catalog y `common-domain`). Las imágenes se etiquetan con el **SHA corto** de git (y `latest`
  en `main`, y la versión en tags `vX.Y.Z`) y se publican en **ECR**. Publicar en ECR **no**
  levanta el clúster EKS, así que es barato.
- **deploy**: ejecuta `helm upgrade --install` contra **EKS**. **Solo corre manualmente**
  (`workflow_dispatch` con la opción `deploy=true`) y está protegido por el **Environment
  `aws-eks`**, que puede exigir aprobación manual. Esto garantiza el carácter **on-demand**:
  el clúster (que sí cuesta) nunca recibe despliegues por accidente.

## Autenticación a AWS: OIDC, sin claves de larga duración

El CD **no usa** `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`. Se autentica con **OIDC**
(federación de identidad de GitHub Actions) asumiendo un **rol IAM** mediante
`aws-actions/configure-aws-credentials` (`role-to-assume`). Por eso el workflow declara
`permissions: id-token: write`.

Setup una sola vez en AWS (resumen):

1. Crear el **OIDC provider** de GitHub en IAM:
   `https://token.actions.githubusercontent.com` (audience `sts.amazonaws.com`).
2. Crear un **rol IAM** con una _trust policy_ que confíe en ese provider y restrinja el
   `sub` a este repositorio (p. ej. `repo:<org>/<repo>:ref:refs/heads/main` y el Environment
   de deploy). Adjuntar permisos mínimos para **ECR** (push/pull) y, para el deploy, para
   `eks:DescribeCluster` + acceso al clúster (aws-auth / EKS access entries).
3. Guardar el ARN del rol en el secret `AWS_ROLE_ARN`.

> El código Terraform del ECR/EKS vive en `infra/terraform` (tarea 12b.2). El chart Helm y su
> manejo de secretos están documentados en `infra/helm/README.md` (tarea 12b.1).

## Secrets y Variables requeridos (nada se hardcodea)

Configúralos en **Settings → Secrets and variables → Actions**. Los valores sensibles van como
**Secrets**; los no sensibles como **Variables**. Para el deploy, defínelos en el **Environment
`aws-eks`** (o a nivel de repositorio).

### Secrets

| Nombre | Usado en | Descripción |
|---|---|---|
| `AWS_ROLE_ARN` | CD | ARN del rol IAM a asumir vía OIDC. Ej.: `arn:aws:iam::123456789012:role/antu-bank-gha`. |
| `HELM_VALUES` | CD (deploy) | Contenido completo de un `values` de Helm para EKS (basado en `infra/helm/antu-bank/values.example.yaml`), con endpoints de RDS/MSK, hosts y `existingSecret`. **No** contiene passwords: esos viven en Secrets de Kubernetes referenciados con `existingSecret`. |

### Variables

| Nombre | Usado en | Descripción |
|---|---|---|
| `AWS_REGION` | CD | Región AWS. Ej.: `us-east-1` (coincide con el default de Terraform). |
| `ECR_REGISTRY` | CD (deploy) | Registry ECR: `<account-id>.dkr.ecr.<region>.amazonaws.com`. En `build-and-push` el registry se obtiene automáticamente del login de ECR. |
| `ECR_REPOSITORY_PREFIX` | CD | Prefijo de repos ECR. Por defecto `antubank` (coincide con `infra/terraform/ecr.tf` y `image.repositoryPrefix` del chart). |
| `EKS_CLUSTER_NAME` | CD (deploy) | Nombre del clúster EKS para `aws eks update-kubeconfig`. |

## Ejecutar el despliegue on-demand a EKS

1. Levantar la infraestructura con Terraform (`infra/terraform`, tarea 12b.2).
2. Crear los **Secrets de Kubernetes** y el ConfigMap del realm (ver `infra/helm/README.md`).
3. Cargar `HELM_VALUES` y las variables de deploy en el Environment `aws-eks`.
4. En GitHub: **Actions → CD → Run workflow**, marcar **deploy = true**.
5. Al terminar la demo, hacer teardown: `helm uninstall antu-bank -n antu-bank` y luego
   `terraform destroy` (tarea 12b.4) para **evitar costos permanentes**. El ciclo de vida
   completo (spin-up + tear-down de punta a punta, checklist post-destroy e higiene de costos)
   está en [`infra/README-aws-ondemand.md`](../infra/README-aws-ondemand.md).

## Notas

- El CI corre en cada push/PR y **nunca** toca AWS ni incurre en costos.
- La demo pública 24/7 (Render/Fly + Vercel) es un track independiente; ver
  `infra/README-deploy.md`.
- No se versiona ningún `values.prod.yaml` con datos reales ni credenciales.
