// Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
// Licensed under the Mozilla Public License v2.0

resource "oci_file_storage_file_system" "oketest_fs" {
  #Required
  #availability_domain = "${lookup(data.oci_identity_availability_domains.ADs.availability_domains[1],"name")}"
  availability_domain = "${var.availability_domain_name}"
  compartment_id      = "${var.compartment_ocid}"
}
