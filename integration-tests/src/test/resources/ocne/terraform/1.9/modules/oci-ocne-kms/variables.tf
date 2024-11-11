# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// Common OCI resource variables
variable "compartment_id" {
  type        = string
  description = "The OCID of the compartment in which to create this deployment"
}
variable "vault_ocid" {
  type        = string
  description = "The OCID of the OCI KMS Vault to use.  If not provided, one will be created"
}

variable "prefix" {
  type        = string
  description = "A unique prefix to attach to the name of all cloud resources that are created as a part of this deployment"
}

variable "type" {
  type        = string
  description = "The type of OCI KMS Vault to instantiate"
  default     = "DEFAULT"
}

variable "key_length" {
  type        = number
  description = "The length of the OCI KMS Vault Key to generate, in bits"
  default     = "16"
}

variable "key_algorithm" {
  type        = string
  description = "The cryptographic algorithm used to generate the OCI KMS Vault Key"
  default     = "AES"
}

variable "key_ocid" {
  type        = string
  description = "The OCID of the OCI KMS Vault Key to use.  If not provided, one will be created"
  default     = ""
}

variable "freeform_tags" {
  description   = "Freeform tags with useful miscellaneous information."
  type          = map(any)
  default       = {}
}
