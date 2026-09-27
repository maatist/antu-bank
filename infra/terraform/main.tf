# =============================================================================
# main.tf — provider, locals y networking (VPC) del stack AWS on-demand.
# =============================================================================
# Tarea 12b.2, Requisito 12 (criterios 5, 6 y 7). Provisiona, on-demand:
#   - VPC con subredes públicas/privadas en varias AZ, NAT, route tables.
#   - (eks.tf)   Cluster EKS + managed node group.
#   - (rds.tf)   Una instancia RDS PostgreSQL por servicio con estado.
#   - (msk.tf)   Cluster MSK (Kafka) con SASL/SCRAM.
#   - (ecr.tf)   Repositorios ECR para los 6 servicios.
#   - (outputs.tf) Salidas mapeadas a los values del chart Helm.

provider "aws" {
  region = var.region

  default_tags {
    tags = merge(var.tags, {
      Environment = var.environment
    })
  }
}

locals {
  name = "${var.project}-${var.environment}"

  # AZ efectivas según az_count.
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  tags = merge(var.tags, {
    Environment = var.environment
  })
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

# -----------------------------------------------------------------------------
# VPC: subredes públicas (ALB/NAT) y privadas (EKS nodes, RDS, MSK).
# Se etiquetan las subredes para el AWS Load Balancer Controller / EKS.
# -----------------------------------------------------------------------------
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.8"

  name = "${local.name}-vpc"
  cidr = var.vpc_cidr

  azs = local.azs
  # /20 por subred a partir del CIDR de la VPC.
  private_subnets = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 4, i)]
  public_subnets  = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 4, i + 8)]

  enable_nat_gateway     = true
  single_nat_gateway     = var.single_nat_gateway
  one_nat_gateway_per_az = !var.single_nat_gateway

  enable_dns_hostnames = true
  enable_dns_support   = true

  # Tags requeridas por el AWS Load Balancer Controller para descubrir subredes.
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }

  tags = local.tags
}
