# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

# Gets the OCID of the OS image to use
data "oci_core_images" "OLImageOCID" {
  compartment_id           = var.compartment_id
  #operating_system         = var.os
  #operating_system_version = var.os_version
  #sort_by                  = "TIMECREATED"
  #sort_order               = "DESC"

  display_name             = "Oracle-Autonomous-Linux-8.10-2024.07.31-0"
  #display_name             = "Oracle-Linux-8.10-2024.09.30-0"
  # filter to avoid Oracle Linux images for GPU
  #filter {
  #  name   = "display_name"
  #  values = ["^${replace(var.os, " ", "-")}-${var.os_version}\\.?[0-9]?-[0-9][0-9][0-9][0-9].[0-9][0-9].[0-9][0-9]-[0-9]$"]
  #  regex  = true
  #}
}
