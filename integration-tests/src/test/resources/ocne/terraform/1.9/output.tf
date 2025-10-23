# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

output "apiserver_ip" {
  description = "IP address of operator node."
  value       = module.infrastructure.apiserver_ip
}

output "control_plane_nodes" {
  description = "IP addresses of control plane nodes."
  value       = module.infrastructure.control_plane_nodes
}

output "worker_nodes" {
  description = "IP addresses of worker nodes."
  value       = module.infrastructure.worker_nodes
}

output "vault_instances" {
  description = "IP addresses of vault instances."
  value       = var.use_vault ? module.vault[0].instances : []
}

output "vault_uri" {
  description = "OCI vault URL."
  value       = var.use_vault ? module.vault[0].uri : ""
}

output "ocne_vault_client_token" {
  description = "OCNE certificate signing token."
  value       = var.use_vault ? module.vault[0].vault_ocne_client_token : ""
  # sensitive = true
}

output "vault_storage_bucket" {
  description = "vault storage backend bucket"
  value       = var.use_vault ? module.vault[0].vault_storage_bucket : ""
}

output "vault_ha_storage_bucket" {
  description = "vault storage HA backend bucket"
  value       = var.use_vault ? module.vault[0].vault_ha_storage_bucket : ""
}

output "user_id" {
  description = "The OCID of the user that will be used by Vault to access OCI resources.  This value cannot be empty if Vault is being deployed."
  value       = var.user_id
}

output "region" {
  description = "The ID of the OCI Region where Vault is deployed.  This value cannot be empty if Vault is being deployed."
  value       = var.region
}

output "tenancy_id" {
  description = "The OCID of the OCI Tenancy where Vault is deployed.  This value cannot be empty if Vault is being deployed."
  value       = var.tenancy_id
}

output "fingerprint" {
  description = "The fingerprint associated with the private key used by the OCI user to authentication.  This value cannot be empty if Vault is being deployed."
  value       = var.fingerprint
}

output "api_private_key_path" {
  description = "The path to the private key used by the OCI user to authenticate.  This value cannot be empty if Vault is being deployed."
  value       = var.api_private_key_path
}

// Common OCI resource outputs
output "availability_domain_id" {
  description = "The ID of the availability domain inside the `region` in which to create this deployment"
  value       = var.availability_domain_id
}

output "compartment_id" {
  description = "The OCID of the compartment in which to create this deployment"
  value       = var.compartment_id
}

output "prefix" {
  description = "A unique prefix to attach to the name of all cloud resources that are created as a part of this deployment"
  value       = var.prefix
}

output "instance_shape" {
  description = "The OCI instance shape to use for all compute resources that are created as part of this deployment"
  value       = var.instance_shape
}

output "image_ocid" {
  description = "The OCID of the OS image to use when creating all compute resources that are part of this deployment"
  value       = module.infrastructure.image_ocid
}

output "os_version" {
  description = "The version of Oracle Linux to use as the base image for all compute resources that are part of this deployemnt"
  value       = var.os_version
}

output "subnet_id" {
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this output to set to the empty string, a network configuration will be generated automatically"
  value       = var.deploy_networking ? module.oci-ocne-network[0].private_subnet_id.*.id[0] : var.subnet_id
}

// Compute instance specific outputs
output "ssh_private_key_path" {
  description = "THe SSH private key path that goes with the SSH public key that is used when accessing compute resources that are created as part of this deployment"
  value       = var.ssh_private_key_path
}

// Instance pool specific outputs
output "vault_pool_size" {
  description = "The initial number of Vault instances to spawn as part of this deployment"
  value       = var.vault_pool_size
}

output "load_balancer_ip" {
  description = "The IP of theOCI load balancer indiciating where Kubernetes clients should connect to when communicating with the Kubernetes cluster"
  value       = module.infrastructure.load_balancer_ip
}

output "load_balancer_shape" {
  description = "The OCI load balancer shape to use when creating load balancers for this deployment"
  value       = var.load_balancer_shape
}

output "load_balancer_policy" {
  description = "The traffic policy to apply to any load balancers that are created as part of this deployment"
  value       = var.load_balancer_policy
}

output "vault_namespace" {
  description = "The OCI object storage namespace under which object storage buckets for Vault are created as part of this deployment"
  value       = var.vault_namespace
}

output "vault_version" {
  description = "The version of Vault to deploy"
  value       = var.vault_version
}

output "proxy" {
  description = "A proxy server to use for https and http communication when downloading or otherwise fetching data from external networks"
  value       = var.proxy
}

output "ocne_version" {
  description = "The version and release of OCNE to deploy"
  value       = data.external.ocne_config.result.version
}

output "yum_repo_url" {
  description = "The URI of the yum repository that hosts all OCNE packages"
  value       = var.yum_repo_url
}

output "control_plane_node_count" {
  description = "The number of Kubernetes control plane nodes to deploy"
  value       = var.control_plane_node_count
}

output "worker_node_count" {
  description = "The number of Kubernetes worker nodes to deploy"
  value       = var.worker_node_count
}

output "standalone_api_server" {
  description = "If true, a dedicated compute instance is allocated for the OCNE API Server.  Otherwise, it will be deployed onto one of the Kubernetes control plane nodes"
  value       = var.standalone_api_server
}

output "environment_name" {
  description = "The name of the OCNE Environment that is created by this module to deploy module instances into"
  value       = var.environment_name
}

output "kubernetes_name" {
  description = "The name of the instance of the OCNE Kubernetes module that is installed as part of this deployment"
  value       = var.kubernetes_name
}

output "kube_apiserver_port" {
  description = "The port to use for the Kubernetes API server that is created as part of this deployment"
  value       = var.kube_apiserver_port
}

output "container_registry" {
  description = "The container image registry that contains all container images that are consumed by this deployment"
  value       = var.container_registry
}

output "extra_cas" {
  description = "Any extra trusted certificates for compute resources"
  value       = var.extra_cas
}

// Networking outputs
output "vcn_id" {
  description = "The OCID of the OCI Virtual Cloud Network in which to create any subnets that might be generated as part of this deployment"
  value       = var.deploy_networking ? module.oci-ocne-network[0].vcn_id : var.vcn_id
}

// Vault outputs
output "use_vault" {
  description = "Decides if Vault is used to requisition certificates for OCNE daemons.  If true, then certificates are allocated using a Vault instance.  Otherwise, this module will generate certificates and distribute them to each node"
  value       = var.use_vault
}

output "vault_ocid" {
  description = "The OCID of the OCI KMS Vault to use with the Hashicorp Vault automatic unsealing feature"
  value       = var.vault_ocid
}

output "key_ocid" {
  description = "The OCID of the OCI KMS Vault Key to use with the Hashicorp Vault automatic unsealing feature"
  value       = var.key_ocid
}

output "secret_name" {
  description = "The name of the vault secret"
  value       = local.secret_name
}

output "ocne_secret_name" {
  description = "The name of the ocne vault secret"
  value       = local.ocne_secret_name
}

output "bastion_private_key_path" {
  value = var.bastion_private_key_path
}

output "bastion_public_ip" {
  value = var.enable_bastion ? module.bastion[0].bastion_public_ip : var.bastion_public_ip
}

output "bastion_user" {
  value = var.bastion_user
}

output "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  value       = var.freeform_tags
}

output "provision_mode" {
  description = "Specifies the provision mode."
  value       = var.provision_mode
}

output "provision_modes_map" {
  description = "Map that contains allowed provision modes."
  value       = local.provision_modes_map
}

output "kube_apiserver_virtual_ip" {
  description = "The virtual IP address to be Kubernetes API server endpoint"
  value       = var.virtual_ip ? module.infrastructure.kube_apiserver_virtual_ip : ""
}

output "kube_apiserver_endpoint_ip" {
  description = "The  Kubernetes API server endpoint"
  value       = var.virtual_ip ? module.infrastructure.kube_apiserver_virtual_ip : module.infrastructure.load_balancer_ip
}

output "config_file_path" {
  description = "The path to the OCNE configuration file"
  value       = var.config_file_path
}
