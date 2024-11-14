# Copyright (c) 2024 Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

terraform {
  required_providers {
    oci = {
      source                = "oracle/oci"
      version               = ">= 4.115.0"
    }
  }
  required_version =  ">= 1.0.0"
}
