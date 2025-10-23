# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {
  lb_ocid = var.create_load_balancer ? oci_load_balancer_load_balancer.lb[0].id : var.load_balancer_ocid
}
