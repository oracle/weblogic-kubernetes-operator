# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

#Terraform and provider version to use
terraform {
  required_providers {
    oci = {
      source = "oracle/oci"
    }
  }
  required_version = ">= 1.0.0"
}
