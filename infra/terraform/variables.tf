# =============================================================================
# variables.tf — parámetros del stack AWS on-demand (tarea 12b.2, Requisito 12).
# =============================================================================
# Defaults pensados para PORTAFOLIO: instancias pequeñas, node group spot, MSK
# mínimo. Todo parametrizado para poder ajustar sin tocar los .tf.
# Ver variables.example.tfvars para un punto de partida.

# ------------------------------ Generales ------------------------------------

variable "project" {
  description = "Prefijo/etiqueta base para nombrar recursos."
  type        = string
  default     = "antu-bank"
}

variable "environment" {
  description = "Nombre de entorno (afecta nombres y tags). Ej: prod, demo, sandbox."
  type        = string
  default     = "prod"
}

variable "region" {
  description = "Región AWS donde se crea toda la infraestructura."
  type        = string
  default     = "us-east-1"
}

variable "tags" {
  description = "Tags comunes aplicados a todos los recursos."
  type        = map(string)
  default = {
    Project   = "antu-bank"
    ManagedBy = "terraform"
    OnDemand  = "true"
  }
}

# ------------------------------ Red (VPC) ------------------------------------

variable "vpc_cidr" {
  description = "CIDR de la VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "az_count" {
  description = "Cantidad de zonas de disponibilidad a usar (>= 2 recomendado para EKS/RDS/MSK)."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count debe ser 2 o 3 (MSK y RDS multi-AZ requieren al menos 2)."
  }
}

variable "single_nat_gateway" {
  description = "Usar un solo NAT Gateway (más barato) en vez de uno por AZ."
  type        = bool
  default     = true
}

# ------------------------------ EKS ------------------------------------------

variable "kubernetes_version" {
  description = "Versión de Kubernetes del cluster EKS."
  type        = string
  default     = "1.30"
}

variable "node_instance_types" {
  description = "Tipos de instancia para el managed node group."
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_capacity_type" {
  description = "Tipo de capacidad del node group: SPOT (barato, portafolio) u ON_DEMAND."
  type        = string
  default     = "SPOT"

  validation {
    condition     = contains(["SPOT", "ON_DEMAND"], var.node_capacity_type)
    error_message = "node_capacity_type debe ser SPOT u ON_DEMAND."
  }
}

variable "node_min_size" {
  description = "Mínimo de nodos del node group."
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Máximo de nodos del node group."
  type        = number
  default     = 4
}

variable "node_desired_size" {
  description = "Cantidad deseada de nodos del node group."
  type        = number
  default     = 2
}

variable "cluster_endpoint_public_access" {
  description = "Exponer el endpoint del API server de EKS a Internet (kubectl local)."
  type        = bool
  default     = true
}

variable "cluster_public_access_cidrs" {
  description = "CIDRs permitidos para acceder al endpoint público del API server. Restringe en entornos reales."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# ------------------------------ RDS ------------------------------------------

variable "rds_engine_version" {
  description = "Versión mayor de PostgreSQL para las instancias RDS."
  type        = string
  default     = "16"
}

variable "rds_instance_class" {
  description = "Clase de instancia RDS (pequeña para portafolio)."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "Almacenamiento en GB por instancia RDS."
  type        = number
  default     = 20
}

variable "rds_multi_az" {
  description = "Habilitar Multi-AZ en RDS (mayor costo; false para portafolio)."
  type        = bool
  default     = false
}

variable "rds_deletion_protection" {
  description = "Protección contra borrado en RDS. false para poder hacer teardown limpio on-demand."
  type        = bool
  default     = false
}

# Database-per-service ESTRICTO: una instancia RDS distinta por servicio con estado.
# Clave = nombre lógico del servicio; valor = nombre de la base y usuario.
variable "databases" {
  description = "Servicios con estado que reciben su propia instancia RDS (database-per-service estricto)."
  type = map(object({
    db_name  = string
    username = string
  }))
  default = {
    account  = { db_name = "account", username = "account" }
    ledger   = { db_name = "ledger", username = "ledger" }
    transfer = { db_name = "transfer", username = "transfer" }
    keycloak = { db_name = "keycloak", username = "keycloak" }
  }
}

# ------------------------------ MSK (Kafka) ----------------------------------

variable "msk_kafka_version" {
  description = "Versión de Kafka del cluster MSK."
  type        = string
  default     = "3.6.0"
}

variable "msk_broker_count" {
  description = "Cantidad de brokers MSK (múltiplo del número de AZ; mínimo para portafolio)."
  type        = number
  default     = 2
}

variable "msk_instance_type" {
  description = "Tipo de instancia de los brokers MSK (pequeño para portafolio)."
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_ebs_volume_size" {
  description = "Tamaño en GB del volumen EBS por broker MSK."
  type        = number
  default     = 20
}

# ------------------------------ ECR ------------------------------------------

variable "ecr_repository_prefix" {
  description = "Prefijo de los repositorios ECR (coincide con image.repositoryPrefix del chart Helm)."
  type        = string
  default     = "antubank"
}

variable "services" {
  description = "Nombres de los 6 microservicios que reciben repositorio ECR."
  type        = list(string)
  default = [
    "api-gateway",
    "account-service",
    "ledger-service",
    "transfer-service",
    "fraud-service",
    "notification-service",
  ]
}

variable "ecr_image_tag_mutability" {
  description = "Mutabilidad de tags en ECR (IMMUTABLE recomendado; MUTABLE para iterar rápido)."
  type        = string
  default     = "MUTABLE"
}
