# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

terraform {
  required_providers {
    oci = {
      source                = "oracle/oci"
      version               = "6.15.0"
      #version               = ">= 4.115.0"
    }
  }
  required_version =  ">= 1.0.0"
}
