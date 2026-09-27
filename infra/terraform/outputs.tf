# =============================================================================
# outputs.tf — salidas mapeadas a los values del chart Helm (infra/helm/antu-bank).
# =============================================================================
# Estas salidas alimentan values.prod.yaml (ver infra/helm/README.md):
#   - image.registry            <- ecr_registry
#   - ACCOUNT/LEDGER/TRANSFER_DB_URL, KC_DB_URL <- rds_jdbc_urls
#   - KAFKA_BOOTSTRAP_SERVERS    <- msk_bootstrap_brokers_sasl_scram
# Los passwords NO se emiten en claro: viven en Secrets Manager (ver *_secret_arns).

# ------------------------------ Generales ------------------------------------

output "region" {
  description = "Región AWS del stack."
  value       = var.region
}

output "account_id" {
  description = "ID de la cuenta AWS."
  value       = data.aws_caller_identity.current.account_id
}

output "vpc_id" {
  description = "ID de la VPC."
  value       = module.vpc.vpc_id
}

# ------------------------------ EKS ------------------------------------------

output "cluster_name" {
  description = "Nombre del cluster EKS (para aws eks update-kubeconfig)."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "Endpoint del API server de EKS."
  value       = module.eks.cluster_endpoint
}

output "cluster_region_kubeconfig_cmd" {
  description = "Comando para configurar kubectl contra el cluster."
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

# ------------------------------ ECR ------------------------------------------

output "ecr_registry" {
  description = "Host del registry ECR (mapea a image.registry en el chart)."
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
}

output "ecr_repository_urls" {
  description = "URL de cada repositorio ECR por servicio."
  value       = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

# ------------------------------ RDS ------------------------------------------

output "rds_endpoints" {
  description = "Endpoint host:puerto de cada RDS por servicio (database-per-service)."
  value       = { for k, db in aws_db_instance.this : k => db.endpoint }
}

output "rds_jdbc_urls" {
  description = "URL JDBC lista para el chart, por servicio (ACCOUNT_DB_URL, LEDGER_DB_URL, TRANSFER_DB_URL, KC_DB_URL)."
  value = {
    for k, db in aws_db_instance.this :
    k => "jdbc:postgresql://${db.address}:${db.port}/${var.databases[k].db_name}"
  }
}

output "rds_secret_arns" {
  description = "ARN del secreto en Secrets Manager con credenciales de cada RDS (username/password/jdbc_url)."
  value       = { for k, s in aws_secretsmanager_secret.rds : k => s.arn }
}

# ------------------------------ MSK ------------------------------------------

output "msk_bootstrap_brokers_sasl_scram" {
  description = "Bootstrap brokers SASL/SCRAM de MSK (mapea a KAFKA_BOOTSTRAP_SERVERS del chart)."
  value       = aws_msk_cluster.this.bootstrap_brokers_sasl_scram
}

output "msk_cluster_arn" {
  description = "ARN del cluster MSK."
  value       = aws_msk_cluster.this.arn
}

output "msk_scram_secret_arn" {
  description = "ARN del secreto SASL/SCRAM (username/password) para construir el sasl.jaas.config."
  value       = aws_secretsmanager_secret.msk_scram.arn
}

output "msk_scram_username" {
  description = "Usuario SASL/SCRAM de MSK (el password vive en Secrets Manager, no se emite)."
  value       = local.msk_scram_username
}
