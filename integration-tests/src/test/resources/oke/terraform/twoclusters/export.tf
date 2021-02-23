/*
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
resource "oci_file_storage_export" "oketest_export" {
  #Required
  export_set_id  = "${oci_file_storage_export_set.oketest_export_set.id}"
  file_system_id = "${oci_file_storage_file_system.oketest_fs.id}"
  path           = "/oketest"
}
