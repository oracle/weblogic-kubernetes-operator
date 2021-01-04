/*
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
resource "oci_file_storage_mount_target" "oketest_mount_target" {
  #Required
  availability_domain = "${lookup(data.oci_identity_availability_domains.ADs.availability_domains[1],"name")}"
  
  compartment_id      = "${var.compartment_ocid}"
  subnet_id           = "${oci_core_subnet.oke-subnet-worker-2.id}"

  #Optional
  display_name = "${var.cluster_name}-mt"
}
