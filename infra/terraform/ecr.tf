# =============================================================================
# ecr.tf — Repositorios ECR para las imágenes de los 6 microservicios.
# =============================================================================
# El nombre queda como "<ecr_repository_prefix>/<servicio>" para coincidir con
# image.repositoryPrefix del chart Helm (p. ej. antubank/account-service).

resource "aws_ecr_repository" "this" {
  for_each = toset(var.services)

  name                 = "${var.ecr_repository_prefix}/${each.value}"
  image_tag_mutability = var.ecr_image_tag_mutability
  force_delete         = true # permite teardown on-demand aunque haya imágenes

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.tags, { Service = each.value })
}

# Política de ciclo de vida: conserva las últimas 10 imágenes por repo (ahorro).
resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Conservar solo las ultimas 10 imagenes"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}
