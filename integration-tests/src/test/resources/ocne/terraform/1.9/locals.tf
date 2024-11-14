# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {
  provision_modes_map = {
    provision_mode_ocne           = "OCNE"
    provision_mode_infrastucture  = "Infrastructure"
  }

  provision_modes_values_list = values(local.provision_modes_map)
}

locals {
  secret_name       = "${var.prefix}-${var.secret_name}"
  ocne_secret_name = "${var.prefix}-${var.ocne_secret_name}"
}