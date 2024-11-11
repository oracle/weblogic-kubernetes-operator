# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

resource "oci_kms_vault" "vault" {
  count          = length(var.vault_ocid) > 0 ? 0 : 1
  compartment_id = var.compartment_id
  display_name   = "${var.prefix}-vault"
  vault_type     = var.type

  # Optional
  freeform_tags = var.freeform_tags
}

resource "oci_kms_key" "vault_key" {
  count          = length(var.key_ocid) > 0 ? 0 : 1
  compartment_id = var.compartment_id
  display_name   = "${var.prefix}-vault-key"
  key_shape {
    algorithm = var.key_algorithm
    length    = var.key_length
  }
  management_endpoint = local.management_endpoint

  # Optional
  freeform_tags = var.freeform_tags
}

