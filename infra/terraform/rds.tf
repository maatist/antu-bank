# =============================================================================
# rds.tf — Una instancia RDS PostgreSQL por servicio con estado.
# =============================================================================
# DATABASE-PER-SERVICE ESTRICTO (Requisito 12, criterio 7): cada servicio con
# estado (account, ledger, transfer y keycloak) recibe SU PROPIA instancia RDS,
# con host, base y usuario distintos. No hay base compartida.
#
# Los passwords se generan con random_password (sensible) y se guardan en AWS
# Secrets Manager. NUNCA se hardcodean ni se emiten en outputs en claro.

# Subnet group compartido (subredes privadas): las instancias siguen siendo
# independientes; solo comparten la ubicación de red privada.
resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-rds"
  subnet_ids = module.vpc.private_subnets
  tags       = merge(local.tags, { Name = "${local.name}-rds" })
}

# Password por instancia (uno por servicio), marcado sensible.
resource "random_password" "rds" {
  for_each = var.databases

  length  = 24
  special = false # evita caracteres problemáticos en URLs JDBC
}

# Una instancia RDS por servicio con estado.
resource "aws_db_instance" "this" {
  for_each = var.databases

  identifier     = "${local.name}-${each.key}"
  engine         = "postgres"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  allocated_storage = var.rds_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = each.value.db_name
  username = each.value.username
  password = random_password.rds[each.key].result
  port     = 5432

  multi_az               = var.rds_multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  deletion_protection       = var.rds_deletion_protection
  skip_final_snapshot       = true
  final_snapshot_identifier = null
  apply_immediately         = true

  # Backups mínimos (portafolio); ajustar en entornos reales.
  backup_retention_period = 1

  tags = merge(local.tags, {
    Name    = "${local.name}-${each.key}"
    Service = each.key
  })
}

# Credenciales de cada RDS en Secrets Manager (no en claro en el estado/outputs).
resource "aws_secretsmanager_secret" "rds" {
  for_each = var.databases

  name        = "${local.name}/rds/${each.key}"
  description = "Credenciales RDS del servicio ${each.key} (database-per-service)"
  tags        = local.tags
}

resource "aws_secretsmanager_secret_version" "rds" {
  for_each = var.databases

  secret_id = aws_secretsmanager_secret.rds[each.key].id
  secret_string = jsonencode({
    username = each.value.username
    password = random_password.rds[each.key].result
    host     = aws_db_instance.this[each.key].address
    port     = aws_db_instance.this[each.key].port
    dbname   = each.value.db_name
    # URL JDBC lista para el chart (ACCOUNT_DB_URL, LEDGER_DB_URL, ...).
    jdbc_url = "jdbc:postgresql://${aws_db_instance.this[each.key].address}:${aws_db_instance.this[each.key].port}/${each.value.db_name}"
  })
}
