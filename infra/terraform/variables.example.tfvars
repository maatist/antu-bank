# =============================================================================
# variables.example.tfvars — punto de partida para el stack AWS on-demand.
# =============================================================================
# COPIA este archivo a uno propio (NO versionado) y ajústalo:
#
#   cp variables.example.tfvars terraform.tfvars   # terraform.tfvars se ignora en git
#   terraform plan   # usa terraform.tfvars automáticamente
#
# NO contiene secretos: los passwords de RDS y MSK los genera Terraform con
# random_password y se guardan en AWS Secrets Manager.

# --- Generales ---
project     = "antu-bank"
environment = "prod"
region      = "us-east-1"

# --- Red ---
vpc_cidr           = "10.42.0.0/16"
az_count           = 2
single_nat_gateway = true # un solo NAT (más barato) para portafolio

# --- EKS ---
kubernetes_version  = "1.30"
node_instance_types = ["t3.medium"]
node_capacity_type  = "SPOT" # spot: barato para demo on-demand
node_min_size       = 2
node_max_size       = 4
node_desired_size   = 2
# Restringe el acceso al API server a tu IP en entornos reales, p. ej.:
# cluster_public_access_cidrs = ["203.0.113.10/32"]

# --- RDS (una instancia por servicio: account, ledger, transfer, keycloak) ---
rds_engine_version      = "16"
rds_instance_class      = "db.t4g.micro"
rds_allocated_storage   = 20
rds_multi_az            = false
rds_deletion_protection = false # false para teardown limpio on-demand

# --- MSK (Kafka) ---
msk_kafka_version   = "3.6.0"
msk_broker_count    = 2 # múltiplo del número de AZ
msk_instance_type   = "kafka.t3.small"
msk_ebs_volume_size = 20

# --- ECR ---
ecr_repository_prefix    = "antubank"
ecr_image_tag_mutability = "MUTABLE"
