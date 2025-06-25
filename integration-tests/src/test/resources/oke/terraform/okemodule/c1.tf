# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl


data "oci_containerengine_cluster_kube_config" "public" {
  count      = local.cluster_enabled && local.public_endpoint_available ? 1 : 0

  cluster_id = local.cluster_id
  endpoint   = "PUBLIC_ENDPOINT"
}


data "oci_containerengine_cluster_kube_config" "private" {
  count      = local.cluster_enabled && local.private_endpoint_available ? 1 : 0

  cluster_id = local.cluster_id
  endpoint   = "PRIVATE_ENDPOINT"
}

data "oci_containerengine_clusters" "existing_cluster" {
  count          = var.cluster_id != null ? 1 : 0
  compartment_id = var.compartment_id

  state = ["ACTIVE","UPDATING"]
  filter {
    name = "id"
    values = [var.cluster_id]
  }
}

# Obtain cluster Kubeconfig.
data "oci_containerengine_cluster_kube_config" "kube_config" {
  #cluster_id = oci_containerengine_cluster.k8s_cluster.id
  cluster_id = one(module.c1[*].cluster_id)
}


output "cluster_id" {
  description = "ID of the OKE cluster"
  value       = one(module.c1[*].cluster_id)
}


output "cluster_kubeconfig" {
  description = "OKE kubeconfig"
  value = var.output_detail ? (
    local.public_endpoint_available ? local.kubeconfig_public : local.kubeconfig_private
  ) : null
}

variable "cluster_kube_config_expiration" {
  default = 2592000
}

variable "cluster_kube_config_token_version" {
  default = "2.0.0"
}

output "cluster_ca_cert" {
  description = "OKE cluster CA certificate"
  value       = var.output_detail && length(local.cluster_ca_cert) > 0 ? local.cluster_ca_cert : null
}

output "apiserver_private_host" {
  description = "Private OKE cluster endpoint address"
  value       = local.apiserver_private_host
}


locals {
  cluster_enabled = var.create_cluster || coalesce(var.cluster_id, "none") != "none"
  cluster_id      = var.create_cluster ? one(module.c1[*].cluster_id) : var.cluster_id
  cluster_name    = var.cluster_name

  cluster-context = try(format("context-%s", substr(local.cluster_id, -11, -1)), "")

  existing_cluster_endpoints  = coalesce(one(flatten(data.oci_containerengine_clusters.existing_cluster[*].clusters[*].endpoints)), tomap({}))
  public_endpoint_available   = var.cluster_id != null ? length(lookup(local.existing_cluster_endpoints, "public_endpoint", "")) > 0 : var.control_plane_is_public && var.assign_public_ip_to_control_plane
  private_endpoint_available  = var.cluster_id != null ? length(lookup(local.existing_cluster_endpoints, "private_endpoint", "")) > 0 : true
  kubeconfig_public           = var.control_plane_is_public ? try(yamldecode(replace(lookup(one(data.oci_containerengine_cluster_kube_config.public), "content", ""), local.cluster-context, var.cluster_name)), tomap({})) : null
  kubeconfig_private          = try(yamldecode(replace(lookup(one(data.oci_containerengine_cluster_kube_config.private), "content", ""), local.cluster-context, var.cluster_name)), tomap({}))

  kubeconfig_clusters = try(lookup(local.kubeconfig_private, "clusters", []), [])
  apiserver_private_host = (var.create_cluster
    ? try(split(":", one(module.c1[*].endpoints.private_endpoint))[0], "")
  : split(":", replace(try(lookup(lookup(local.kubeconfig_clusters[0], "cluster", {}), "server", ""), "none"), "https://", ""))[0])

  kubeconfig_ca_cert = try(lookup(lookup(local.kubeconfig_clusters[0], "cluster", {}), "certificate-authority-data", ""), "none")
  cluster_ca_cert    = coalesce(var.cluster_ca_cert, local.kubeconfig_ca_cert)
}


module "c1" {

  source  = "oracle-terraform-modules/oke/oci"
  version = "5.2.2"

  count = lookup(lookup(var.clusters, "c1"), "enabled") ? 1 : 0

  home_region = lookup(local.regions, var.home_region)

  #region      = lookup(local.regions, lookup(lookup(var.clusters, "c1"), "region"))
  region      = lookup(local.regions, var.home_region)

  tenancy_id = var.tenancy_id

  # general oci parameters
  compartment_id = var.compartment_id

  # ssh keys
  ssh_private_key_path = var.ssh_private_key_path
  ssh_public_key_path  = var.ssh_public_key_path

# Network
  create_vcn     = false
  vcn_id         = var.vcn_id


  # networking
  create_drg       = var.oke_control_plane == "private" ? true : false
  drg_display_name = var.cluster_name


  vcn_cidrs     = [lookup(lookup(var.clusters, "c1"), "vcn")]
  vcn_name      = "VCN-wktiso1"

  #subnets
  subnets = {
    pub_lb  = { id = var.pub_lb_id }
    operator  = { id = var.pub_lb_id }
    bastion  = { id = var.pub_lb_id }
    workers  = { id = var.worker_subnet_id }
    cp = {id = var.control_plane_subnet_id }
  }

  # bastion host
  create_bastion              = true           # *true/false
  bastion_allowed_cidrs       = []             # e.g. ["0.0.0.0/0"] to allow traffic from all sources
  bastion_availability_domain = null           # Defaults to first available
  bastion_image_id            = null           # Ignored when
  bastion_image_os            = "Oracle Linux" # Ignored when bastion_image_type = "custom"
  bastion_image_os_version    = "8"            # Ignored when bastion_image_type = "custom"
  bastion_image_type          = "platform"     # platform/custom
  bastion_nsg_ids             = []             # Combined with created NSG when enabled in var.nsgs
  bastion_public_ip           = null           # Ignored when create_bastion = true
  #bastion_type                = "public"       # *public/private
  bastion_upgrade             = false          # true/*false
  bastion_user                = "opc"

  bastion_shape = {shape            = var.node_shape,ocpus            = 1,memory           = 4,boot_volume_size = 50}
  #create_bastion        = true
  allow_bastion_cluster_access  = true
  bastion_is_public = true
  #bastion_allowed_cidrs = ["0.0.0.0/0"]
  #bastion_upgrade       = false

  # operator host
  create_operator            = false
  operator_upgrade           = false
  operator_install_helm      = true
  #operator_install_kubectl_from_repo    = true
  operator_cloud_init            = []
  create_iam_resources       = false
  create_iam_operator_policy = "never"
  operator_install_k9s       = false

  # oke cluster options
  cluster_name                = var.cluster_name
  cluster_type                = var.cluster_type
  cni_type                    = var.preferred_cni
  control_plane_is_public     = true
  control_plane_allowed_cidrs = [local.anywhere]
  kubernetes_version          = var.kubernetes_version
  services_cidr               = lookup(lookup(var.clusters, "c1"), "services")


  # node pools
  allow_worker_ssh_access           = true
  kubeproxy_mode                    = "iptables"
  worker_pool_mode                  = "node-pool"
  worker_pools                      = var.nodepools
  worker_cloud_init                 = local.worker_cloud_init
  worker_image_type                 = "oke"

  # oke load balancers
  load_balancers          = "both"
  preferred_load_balancer = "public"



  user_id = var.user_id
  providers = {
    oci      = oci.c1
    oci.home = oci.home
  }
}


resource "local_file" "test_kube_config_file" {
  content  = data.oci_containerengine_cluster_kube_config.kube_config.content
  filename = "${path.module}/${var.cluster_name}_kubeconfig"
}
