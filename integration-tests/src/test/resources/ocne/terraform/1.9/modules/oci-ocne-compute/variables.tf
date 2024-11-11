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
}

variable "subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this variable to set to the empty string, a network configuration will be generated automatically"
}

variable "secondary_subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet.  If non-empty, each instance created by this module will have a secondary VNIC assigned to this subnet and a private IP address within it will be allocated."
  default     = ""
}

variable "attach_secondary_vnic" {
  type        = bool
  description = "If set to true, allocate a secondary VNIC and attach it to the subnet identified bvy `secondary_subnet_id`"
  default     = false
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

variable "init_script" {
  type        = string
  description = "Init script post instance creation"
}

variable "instance_count" {
  type        = number
  description = "The number of compute instances to create with the given configuration"
}

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

variable "compute_user" {
  type        = string
  description = "A user configured with sudo access and authenticated with ssh_public_key_path and ssh_private_key_path on each compute resource"
}

variable "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  type        = map(any)
  default     = {}
}
