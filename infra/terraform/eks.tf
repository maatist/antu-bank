# =============================================================================
# eks.tf — Cluster EKS + managed node group (spot pequeño para portafolio).
# =============================================================================

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.20"

  cluster_name    = local.name
  cluster_version = var.kubernetes_version

  # Endpoint público para poder usar kubectl/helm desde local (on-demand).
  cluster_endpoint_public_access       = var.cluster_endpoint_public_access
  cluster_endpoint_public_access_cidrs = var.cluster_public_access_cidrs

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Quien ejecuta `terraform apply` queda como admin del cluster.
  enable_cluster_creator_admin_permissions = true

  # Addons gestionados imprescindibles.
  cluster_addons = {
    coredns                = {}
    kube-proxy             = {}
    vpc-cni                = {}
    eks-pod-identity-agent = {}
  }

  eks_managed_node_groups = {
    default = {
      instance_types = var.node_instance_types
      capacity_type  = var.node_capacity_type

      min_size     = var.node_min_size
      max_size     = var.node_max_size
      desired_size = var.node_desired_size

      # Los nodos viven en subredes privadas; salen a Internet por NAT.
      subnet_ids = module.vpc.private_subnets
    }
  }

  tags = local.tags
}
