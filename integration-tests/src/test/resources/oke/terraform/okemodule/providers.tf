# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

provider "oci" {
  fingerprint         = var.api_fingerprint
  private_key_path    = var.api_private_key_path
  region              = lookup(local.regions,var.home_region)
  tenancy_ocid        = var.tenancy_id
  user_ocid           = var.user_id
  alias               = "home"
  ignore_defined_tags = ["Oracle-Tags.CreatedBy", "Oracle-Tags.CreatedOn"]
}

provider "oci" {
  fingerprint         = var.api_fingerprint
  private_key_path    = var.api_private_key_path
  region              = lookup(local.regions,lookup(lookup(var.clusters,"c1"),"region"))
  tenancy_ocid        = var.tenancy_id
  user_ocid           = var.user_id
  alias               = "c1"
  ignore_defined_tags = ["Oracle-Tags.CreatedBy", "Oracle-Tags.CreatedOn"]
}


