# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

# Variables
variable "compartment_id" {
  type = string
}

variable "prefix" {
  type = string
}

variable "private_dns_label" {
  type    = string
  default = "private"
}

variable "ssh_public_key_path" {
  type = string
}

variable "vcn_dns_label" {
  type    = string
  default = "ocne"
}

variable "vnc_cidr_block" {
  type    = string
  default = "10.0.0.0/16"
}

variable "vnc_private_subnet_cidr_block" {
  type    = string
  default = "10.0.0.0/24"
}

variable "vcn_id" {
  type        = string
  description = "The OCID of the OCI Virtual Cloud Network in which to create any subnets that might be generated as part of this deployment"
}

variable "ig_route_id" {
  type = string
}

variable "nat_route_id" {
  type = string
}

variable "deploy_networking" {
  type        = bool
  description = "Decides if VCN is installed."
}

variable "enable_bastion" {
  type        = bool
  description = "Decides if bastion is installed.  Intended for internal use.  Set to false."
}

variable "enable_notification" {
  description = "Whether to enable ONS notification for the bastion host."
  default     = false
  type        = bool
}

variable "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  type        = map(any)
  default     = {}
}
