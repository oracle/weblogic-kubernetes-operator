# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

resource "null_resource" "variable_validation" {
  lifecycle {
    postcondition {
      condition     = (contains(local.provision_modes_values_list, var.provision_mode))
      error_message = "Invalid provision mode: ${var.provision_mode}. Valid provision modes are: ${join(", ", local.provision_modes_values_list)}"
    }
    postcondition {
      condition     = (!var.deploy_networking || var.enable_bastion)
      error_message = "enable_bastion must be true if deploy_networking is true"
    }
    postcondition {
      condition     = (!var.enable_bastion || (var.enable_bastion && var.bastion_private_key_path != ""))
      error_message = "bastion_private_key_path must be set if enable_bastion is true"
    }
  }
}

data "external" "ocne_config" {
  program = ["sh", "${path.module}/modules/ocne-provision/files/config-read.sh"]
  query = {
    path = var.config_file_path
    ov   = var.ocne_version
    en   = var.environment_name
    kn   = var.kubernetes_name
  }
}

resource "null_resource" "ocne_config_validation" {
  depends_on = [data.external.ocne_config]
  lifecycle {
    postcondition {
      condition     = (length(data.external.ocne_config.result.error) == 0)
      error_message = data.external.ocne_config.result.error
    }
  }
}

module "oci-ocne-network" {
  source = "./modules/oci-ocne-network"
  count  = (var.deploy_networking && var.enable_bastion) || var.enable_bastion ? 1 : 0

  compartment_id      = var.compartment_id
  ssh_public_key_path = var.ssh_public_key_path
  prefix              = var.prefix
  deploy_networking   = var.deploy_networking
  enable_bastion      = var.enable_bastion
  vcn_id              = var.vcn_id
  ig_route_id         = var.ig_route_id
  nat_route_id        = var.nat_route_id
  freeform_tags       = var.freeform_tags
}

module "bastion" {
  source = "./modules/terraform-oci-bastion"
  count  = var.enable_bastion ? 1 : 0

  bastion_shape       = var.bastion_shape
  tenancy_id          = var.tenancy_id
  compartment_id      = var.compartment_id
  ig_route_id         = var.deploy_networking ? module.oci-ocne-network[0].ig_route_id : var.ig_route_id
  vcn_id              = var.deploy_networking ? module.oci-ocne-network[0].vcn_id : var.vcn_id
  prefix              = var.prefix
  ssh_public_key_path = var.ssh_public_key_path
  enable_notification = var.enable_notification
  freeform_tags       = var.freeform_tags
}

module "infrastructure" {
  depends_on = [null_resource.variable_validation]
  source     = "./modules/terraform-oci-ocne-infrastructure"

  availability_domain_id   = var.availability_domain_id
  compartment_id           = var.compartment_id
  prefix                   = var.prefix
  subnet_id                = var.deploy_networking ? module.oci-ocne-network[0].private_subnet_id.*.id[0] : var.subnet_id
  instance_shape           = var.instance_shape
  image_ocid               = var.image_ocid
  os_version               = var.os_version
  kernel_version           = var.kernel_version
  load_balancer_shape      = var.load_balancer_shape
  ssh_public_key_path      = var.ssh_public_key_path
  ssh_private_key_path     = var.ssh_private_key_path
  control_plane_node_count = var.control_plane_node_count
  worker_node_count        = var.worker_node_count
  standalone_api_server    = var.standalone_api_server
  yum_repo_url             = var.yum_repo_url
  ocne_version             = data.external.ocne_config.result.version
  enable_bastion           = var.enable_bastion
  bastion_public_ip        = var.enable_bastion ? module.bastion[0].bastion_public_ip : var.bastion_public_ip
  bastion_user             = var.bastion_user
  bastion_private_key_path = var.enable_bastion || var.bastion_public_ip != "" ? var.bastion_private_key_path : ""
  compute_user             = var.compute_user
  freeform_tags            = var.freeform_tags
  virtual_ip               = var.virtual_ip
}

module "vault" {
  source = "./modules/terraform-oci-vault"
  count  = var.use_vault ? 1 : 0

  user_id                  = var.user_id
  region                   = var.region
  tenancy_id               = var.tenancy_id
  fingerprint              = var.fingerprint
  api_private_key_path     = var.api_private_key_path
  private_key_password     = var.private_key_password
  availability_domain_id   = var.availability_domain_id
  compartment_id           = var.compartment_id
  prefix                   = "${var.prefix}-vault"
  subnet_id                = var.deploy_networking ? module.oci-ocne-network[0].private_subnet_id.*.id[0] : var.subnet_id
  instance_shape           = var.instance_shape
  ssh_public_key_path      = var.ssh_public_key_path
  ssh_private_key_path     = var.ssh_private_key_path
  load_balancer_port       = "8200"
  proxy                    = var.proxy
  vault_ocid               = var.vault_ocid
  key_ocid                 = var.key_ocid
  vault_namespace          = var.vault_namespace
  load_balancer_ocid       = module.infrastructure.load_balancer_ocid
  create_load_balancer     = false
  load_balancer_ip         = module.infrastructure.load_balancer_ip
  pool_size                = var.vault_pool_size
  secret_name              = local.secret_name
  ocne_secret_name         = local.ocne_secret_name
  load_balancer_shape      = var.load_balancer_shape
  enable_bastion           = var.enable_bastion
  bastion_public_ip        = var.enable_bastion ? module.bastion[0].bastion_public_ip : var.bastion_public_ip
  bastion_user             = var.bastion_user
  bastion_private_key_path = var.enable_bastion || var.bastion_public_ip != "" ? var.bastion_private_key_path : ""
  compute_user             = var.compute_user
  freeform_tags            = var.freeform_tags
}

module "ocne-provision" {
  depends_on = [module.infrastructure]
  source     = "./modules/ocne-provision"
  count      = var.provision_mode == lookup(local.provision_modes_map, "provision_mode_ocne", "") ? 1 : 0

  ssh_public_key_path               = var.ssh_public_key_path
  ssh_private_key_path              = var.ssh_private_key_path
  os_version                        = var.os_version
  proxy                             = var.proxy
  no_proxy                          = var.no_proxy
  use_vault                         = var.use_vault
  control_plane_node_count          = var.control_plane_node_count
  worker_node_count                 = var.worker_node_count
  control_plane_nodes               = module.infrastructure.control_plane_nodes
  worker_nodes                      = module.infrastructure.worker_nodes
  apiserver_ip                      = module.infrastructure.apiserver_ip
  vault_uri                         = var.use_vault ? module.vault[0].uri : ""
  ocne_version                      = data.external.ocne_config.result.version
  environment_name                  = data.external.ocne_config.result.environment_name
  kubernetes_name                   = data.external.ocne_config.result.kubernetes_name
  kube_apiserver_port               = var.kube_apiserver_port
  kube_apiserver_ip                 = var.virtual_ip ? module.infrastructure.kube_apiserver_virtual_ip : module.infrastructure.load_balancer_ip
  virtual_ip                        = var.virtual_ip
  container_registry                = var.container_registry
  certificate_signing_token         = var.use_vault ? module.vault[0].vault_ocne_client_token : ""
  enable_bastion                    = var.enable_bastion
  bastion_public_ip                 = var.enable_bastion ? module.bastion[0].bastion_public_ip : var.bastion_public_ip
  bastion_user                      = var.bastion_user
  bastion_private_key_path          = var.enable_bastion || var.bastion_public_ip != "" ? var.bastion_private_key_path : ""
  node_ocids                        = module.infrastructure.node_ocids
  compute_user                      = var.compute_user
  debug                             = var.debug
  restrict_service_externalip_cidrs = var.restrict_service_externalip_cidrs
  config_file_path                  = var.config_file_path
  oci_api_key_path                  = data.external.ocne_config.result.oci_api_key_path
  kubevirt_config                   = data.external.ocne_config.result.kubevirt_config
}
