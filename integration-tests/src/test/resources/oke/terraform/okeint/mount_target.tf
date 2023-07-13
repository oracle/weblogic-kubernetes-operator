/*
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
resource "oci_file_storage_mount_target" "oketest_mount_target" {
  #Required
  availability_domain = data.oci_identity_availability_domain.ad1.name

  compartment_id = var.compartment_ocid
  subnet_id      = var.private_subnet_ocid

  #Optional
  display_name = "${var.cluster_name}-mt"
}

