# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// OCI Identity Variables
variable "user_id" {
  type        = string
  description = "The OCID of the user that will be used by Vault to access OCI resources"
}

variable "region" {
  type        = string
  description = "The ID of the OCI Region where Vault is deployed"
}

variable "tenancy_id" {
  type        = string
  description = "The OCID of the OCI Tenancy where Vault is deployed"
}

variable "fingerprint" {
  type        = string
  description = "The fingerprint associated with the private key used by the OCI user to authentication"
}

variable "api_private_key_path" {
  type        = string
  description = "The path to the private key used by the OCI user to authenticate"
}

variable "private_key_password" {
  type        = string
  description = "The password used to decrypt the OCI user's private key"
}

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

variable "subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this variable to set to the empty string, a network configuration will be generated automatically"
}

variable "compute_user" {
  type        = string
  description = "A user configured with sudo access and authenticated with ssh_public_key and ssh_private_key on each compute resource"
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
variable "pool_size" {
  type        = number
  description = "The initial number of Vault instances to spawn as part of this deployment"
  default     = "1"
}


variable "load_balancer_protocol" {
  type        = string
  description = "The protocol to select when creating the listener for the load balancer that talks to the Vault instances"
  default     = "TCP"
}

variable "load_balancer_ocid" {
  type        = string
  description = "The OCID of the load balancer to use.  If one is not specified, a load balancer will be created"
  default     = ""
}

variable "create_load_balancer" {
  type        = string
  description = "Set to true if a load balancer should be created by this module to service Vault instances.  Set to false to use an existing one"
  default     = true
}

variable "load_balancer_ip" {
  type        = string
  description = "If a custom load balancer is provided, set this to the canonical IP address of that load balancer"
  default     = ""
}

variable "load_balancer_port" {
  type        = string
  description = "The port that the generated load balancer listener is listening on"
  default     = "8200"
}

variable "vault_namespace" {
  type        = string
  description = "OCI Object storage bucket namespace string used to create the OCI Object Storage Bucket to support the [HashiCorp Vault OCI Object Storage Backend](https://www.vaultproject.io/docs/configuration/storage/oci-object-storage). To get the namespace string see [Understanding Object Storage Namespaces](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/understandingnamespaces.htm]"
}

variable "load_balancer_shape" {
  type        = map(string)
  description = "The OCI shape of the load balancer to create to service this pool"
}

variable "vault_version" {
  type        = string
  description = "The version of Vault that is installed"
  default     = "1.3.4"
}

variable "proxy" {
  type        = string
  description = "The https proxy that is used to download Vault.  Set to the empty string if no proxy is required"
}

variable "vault_ocid" {
  type        = string
  description = "The OCID of the OCI KMS Vault that stores the OCI KSM Vault Key that secures the auto-unseal data for Hashicorp Vault.  If unset, an OCI KMS Vault will be created"
}

variable "key_ocid" {
  type        = string
  description = "The OCID of an OCI KMS Vault Key that is within the OCI KMS Vault configured via `vault_ocid`.  If not is not provided, an OCI KMS Vault key will be created"
}

variable "secret_name" {
  type        = string
  description = "The name of the vault secret"
}

variable "ocne_secret_name" {
  type        = string
  description = "The name of the ocne vault secret"
}

variable "enable_bastion" {
  type        = bool
  description = "Decides if bastion is installed.  Intended for internal use.  Set to false."
}

variable "bastion_public_ip" {
  type        = string
  description = "Public IP address of an existing Bastion host. This is set when we are not creating a bastion but need to use an existing one."
}

variable "bastion_user" {
  type        = string
  description = "User name on the Bastion host"
  default     = "opc"
}

variable "bastion_private_key_path" {
  type        = string
  description = "The SSH private key path that goes with the SSH public key that is used when accessing the bastion host. Must be set to true if enable_bastion is set to true."
}

variable "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  type        = map(any)
  default     = {}
}
