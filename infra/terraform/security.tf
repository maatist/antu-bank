# =============================================================================
# security.tf — grupos de seguridad para RDS y MSK, con acceso acotado a EKS.
# =============================================================================
# Principio: RDS y MSK viven en subredes privadas y solo aceptan tráfico desde
# los nodos EKS (por security group), nunca desde Internet.

# SG de RDS: solo PostgreSQL (5432) desde el SG de los nodos EKS.
resource "aws_security_group" "rds" {
  name        = "${local.name}-rds"
  description = "Acceso PostgreSQL a las RDS solo desde nodos EKS"
  vpc_id      = module.vpc.vpc_id

  tags = merge(local.tags, { Name = "${local.name}-rds" })
}

resource "aws_security_group_rule" "rds_ingress_from_eks" {
  type                     = "ingress"
  description              = "PostgreSQL desde nodos EKS"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = module.eks.node_security_group_id
}

resource "aws_security_group_rule" "rds_egress_all" {
  type              = "egress"
  description       = "Salida (respuestas)"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.rds.id
}

# SG de MSK: puertos SASL_SSL (9096) y control desde el SG de los nodos EKS.
resource "aws_security_group" "msk" {
  name        = "${local.name}-msk"
  description = "Acceso Kafka (SASL_SSL) a MSK solo desde nodos EKS"
  vpc_id      = module.vpc.vpc_id

  tags = merge(local.tags, { Name = "${local.name}-msk" })
}

resource "aws_security_group_rule" "msk_ingress_sasl_from_eks" {
  type                     = "ingress"
  description              = "Kafka SASL_SSL desde nodos EKS"
  from_port                = 9096
  to_port                  = 9096
  protocol                 = "tcp"
  security_group_id        = aws_security_group.msk.id
  source_security_group_id = module.eks.node_security_group_id
}

resource "aws_security_group_rule" "msk_ingress_zk_from_eks" {
  type                     = "ingress"
  description              = "Zookeeper/control plane desde nodos EKS"
  from_port                = 2181
  to_port                  = 2181
  protocol                 = "tcp"
  security_group_id        = aws_security_group.msk.id
  source_security_group_id = module.eks.node_security_group_id
}

resource "aws_security_group_rule" "msk_egress_all" {
  type              = "egress"
  description       = "Salida (respuestas)"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.msk.id
}
