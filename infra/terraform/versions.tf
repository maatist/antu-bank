# =============================================================================
# versions.tf — versiones de Terraform y providers (tarea 12b.2, Requisito 12).
# =============================================================================
# Requisitos de versión conservadores y pineados por rango menor para reproducibilidad.

terraform {
  required_version = ">= 1.6.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # ---------------------------------------------------------------------------
  # Backend de estado remoto (OPCIONAL, comentado para poder correr en local).
  # ---------------------------------------------------------------------------
  # Para trabajo en equipo / CI conviene un backend S3 + bloqueo con DynamoDB.
  # 1) Crea el bucket S3 y la tabla DynamoDB (fuera de este stack o con un stack
  #    aparte) y descomenta el bloque, ajustando nombres y región.
  # 2) Ejecuta: terraform init -reconfigure
  #
  # backend "s3" {
  #   bucket         = "antu-bank-tfstate"
  #   key            = "neobank/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "antu-bank-tflock"
  #   encrypt        = true
  # }
}
