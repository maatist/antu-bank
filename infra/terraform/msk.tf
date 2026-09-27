# =============================================================================
# msk.tf — Cluster MSK (Kafka) con autenticación SASL/SCRAM (SCRAM-SHA-512).
# =============================================================================
# Requisito 12, criterio 5. Los servicios producen/consumen contra MSK vía
# SASL_SSL (puerto 9096), coherente con el chart Helm.
#
# MSK SCRAM exige un secreto en AWS Secrets Manager ASOCIADO al cluster. Ese
# secreto DEBE:
#   - cifrarse con una KMS key administrada por el cliente (no la default
#     aws/secretsmanager), y
#   - tener un nombre que empiece con "AmazonMSK_".
# El password se genera con random_password (sensible); nunca se hardcodea.

# ---- KMS key para cifrar el secreto SCRAM (requisito de MSK) ----------------
resource "aws_kms_key" "msk_scram" {
  description         = "KMS para secreto SCRAM de MSK (${local.name})"
  enable_key_rotation = true
  tags                = local.tags
}

resource "aws_kms_alias" "msk_scram" {
  name          = "alias/${local.name}-msk-scram"
  target_key_id = aws_kms_key.msk_scram.key_id
}

# ---- Credenciales SASL/SCRAM ------------------------------------------------
resource "random_password" "msk_scram" {
  length  = 24
  special = false
}

locals {
  msk_scram_username = "antubank"
}

# El nombre DEBE empezar con "AmazonMSK_".
resource "aws_secretsmanager_secret" "msk_scram" {
  name        = "AmazonMSK_${local.name}_scram"
  description = "Credenciales SASL/SCRAM para MSK (${local.name})"
  kms_key_id  = aws_kms_key.msk_scram.key_id
  tags        = local.tags
}

resource "aws_secretsmanager_secret_version" "msk_scram" {
  secret_id = aws_secretsmanager_secret.msk_scram.id
  secret_string = jsonencode({
    username = local.msk_scram_username
    password = random_password.msk_scram.result
  })
}

# Política que permite a MSK leer el secreto SCRAM.
resource "aws_secretsmanager_secret_policy" "msk_scram" {
  secret_arn = aws_secretsmanager_secret.msk_scram.arn
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AWSKafkaResourcePolicy"
        Effect    = "Allow"
        Principal = { Service = "kafka.amazonaws.com" }
        Action    = "secretsmanager:getSecretValue"
        Resource  = aws_secretsmanager_secret.msk_scram.arn
      }
    ]
  })
}

# ---- Logging del cluster ----------------------------------------------------
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${local.name}"
  retention_in_days = 7
  tags              = local.tags
}

# ---- Cluster MSK ------------------------------------------------------------
resource "aws_msk_cluster" "this" {
  cluster_name           = local.name
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = var.msk_broker_count

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.msk_ebs_volume_size
      }
    }
  }

  # Solo SASL/SCRAM (SCRAM-SHA-512). TLS in-transit obligatorio.
  client_authentication {
    sasl {
      scram = true
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  tags = local.tags
}

# Asocia el secreto SCRAM al cluster (habilita el login SASL/SCRAM).
resource "aws_msk_scram_secret_association" "this" {
  cluster_arn     = aws_msk_cluster.this.arn
  secret_arn_list = [aws_secretsmanager_secret.msk_scram.arn]

  depends_on = [aws_secretsmanager_secret_version.msk_scram]
}
