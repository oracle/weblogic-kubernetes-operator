# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {
  vault_ocid          = length(var.vault_ocid) > 0 ? data.oci_kms_vault.vault[0].id : oci_kms_vault.vault[0].id
  key_ocid            = length(var.key_ocid) > 0 ? var.key_ocid : oci_kms_key.vault_key[0].id
  crypto_endpoint     = length(var.vault_ocid) > 0 ? data.oci_kms_vault.vault[0].crypto_endpoint : oci_kms_vault.vault[0].crypto_endpoint
  management_endpoint = length(var.vault_ocid) > 0 ? data.oci_kms_vault.vault[0].management_endpoint : oci_kms_vault.vault[0].management_endpoint
}
