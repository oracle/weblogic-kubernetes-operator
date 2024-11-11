# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// Common OCI resource variables
variable "availability_domain_id" {
  type        = string
  description = "The ID of the availability domain inside the `region` in which to create this deployment"
}

variable "compartment_id" {
  type        = string
  description = "The OCID of the compartment in which to create this deployment"
}

variable "prefix" {
  type        = string
  description = "A unique prefix to attach to the name of all cloud resources that are created as a part of this deployment"
}

variable "instance_shape" {
  type        = map(any)
  description = "The OCI instance shape to use for all compute resources that are created as part of this deployment"
}

variable "image_ocid" {
  type        = string
  description = "The OCID of the OS image to use when creating all compute resources that are part of this deployment"
  default     = ""
}

variable "os_version" {
  type        = string
  description = "The version of Oracle Linux to use as the base image for all compute resources that are part of this deployemnt"
}

variable "kernel_version" {
  type        = string
  description = "Kernel version to be installed"
}

variable "subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this variable to set to the empty string, a network configuration will be generated automatically"
}

// Compute instance specific variables
variable "ssh_public_key_path" {
  type        = string
  description = "The SSH public key path to use when configuring access to any compute resources created as part of this deployment"
}

variable "ssh_private_key_path" {
  type        = string
  description = "THe SSH private key path that goes with the SSH public key that is used when accessing compute resources that are created as part of this deployment"
}

// Instance pool specific variables
variable "load_balancer_shape" {
  type        = map(string)
  description = "The OCI load balancer shape to use when creating load balancers for this deployment"
}

variable "load_balancer_policy" {
  type        = string
  description = "The traffic policy to apply to any load balancers that are created as part of this deployment"
  default     = "LEAST_CONNECTIONS"
}

variable "ocne_version" {
  type        = string
  description = "The version and release of OCNE to deploy"
}

variable "yum_repo_url" {
  type        = string
  description = "The URI of the yum repository that hosts all OCNE packages"
}

variable "control_plane_node_count" {
  type        = number
  description = "The number of Kubernetes control plane nodes to deploy"
}

variable "worker_node_count" {
  type        = number
  description = "The number of Kubernetes worker nodes to deploy"
}

variable "standalone_api_server" {
  type        = bool
  description = "If true, a dedicated compute instance is allocated for the OCNE API Server.  Otherwise, it will be deployed onto one of the Kubernetes control plane nodes"
  default     = true
}

variable "use_vault" {
  type        = bool
  description = "Decides if Vault is used to requisition certificates for OCNE daemons.  If true, then certificates are allocated using a Vault instance.  Otherwise, this module will generate certificates and distribute them to each node"
  default     = false
}

variable "environment_name" {
  type        = string
  description = "The name of the OCNE Environment that is created by this module to deploy module instances into"
  default     = "myenvironment"
}

variable "kubernetes_name" {
  type        = string
  description = "The name of the instance of the OCNE Kubernetes module that is installed as part of this deployment"
  default     = "mycluster"
}

variable "kube_apiserver_port" {
  type        = string
  description = "The port to use for the Kubernetes API server that is created as part of this deployment"
  default     = "6443"
}

variable "container_registry" {
  type        = string
  description = "The container image registry that contains all container images that are consumed by this deployment"
  default     = "container-registry.oracle.com/olcne"
}

variable "compute_user" {
  type        = string
  description = "A user configured with sudo access and authenticated with ssh_public_key_path and ssh_private_key_path on each compute resource"
}

// OCNE variables
variable "enable_bastion" {
  type        = bool
  description = "Decides if bastion is installed.  Intended for internal use.  Set to false."
}

variable "bastion_public_ip" {
  type        = string
  description = "Public IP address of the Bastion host"
}

variable "bastion_user" {
  type        = string
  description = "User name on the Bastion host"
  default     = "opc"
}

variable "bastion_private_key_path" {
  type        = string
  description = "The location of the SSH private key for the Bastion host"
}

variable "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  type        = map(any)
  default     = {}
}

variable "virtual_ip" {
  type        = bool
  description = "Setup Kubernetes API server endpoint on a virtual IP address representing all the Kubernetes control plane nodes"
  default     = false
}
