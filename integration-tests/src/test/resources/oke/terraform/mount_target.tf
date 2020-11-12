// Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
// Licensed under the Mozilla Public License v2.0
variable "availability_domain_name" {}
variable "subnet_worker" {}
resource "oci_file_storage_mount_target" "oketest_mount_target" {
  #Required
  availability_domain = "${var.availability_domain_name}"
  compartment_id      = "${var.compartment_ocid}"
  #subnet_id           = "${oci_core_subnet.oke-subnet-worker-2.id}"
  subnet_id           = "${lookup(data.oci_core_subnets.oke_subnets.subnets[var.subnet_worker],"id")}"

  #Optional
  display_name = "${var.cluster_name}-mt"
}
output "subnet_id" {
  value = "${oci_core_subnet.oke-subnet-worker-2.id}"
}
