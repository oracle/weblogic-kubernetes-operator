/*
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
resource "oci_file_storage_export" "oketest_export1" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs1.id
  path           = "/oketest1"
}
resource "oci_file_storage_export" "oketest_export2" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs2.id
  path           = "/oketest2"
}
resource "oci_file_storage_export" "oketest_export3" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs3.id
  path           = "/oketest3"
}
resource "oci_file_storage_export" "oketest_export4" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs4.id
  path           = "/oketest4"
}
resource "oci_file_storage_export" "oketest_export5" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs5.id
  path           = "/oketest5"
}
resource "oci_file_storage_export" "oketest_export6" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs6.id
  path           = "/oketest6"
}
resource "oci_file_storage_export" "oketest_export7" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs7.id
  path           = "/oketest7"
}
resource "oci_file_storage_export" "oketest_export8" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs8.id
  path           = "/oketest8"
}
resource "oci_file_storage_export" "oketest_export9" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs9.id
  path           = "/oketest9"
}
resource "oci_file_storage_export" "oketest_export10" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs10.id
  path           = "/oketest10"
}
resource "oci_file_storage_export" "oketest_export11" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs11.id
  path           = "/oketest11"
}
resource "oci_file_storage_export" "oketest_export12" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs12.id
  path           = "/oketest12"
}
resource "oci_file_storage_export" "oketest_export13" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs13.id
  path           = "/oketest13"
}
resource "oci_file_storage_export" "oketest_export14" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs14.id
  path           = "/oketest14"
}
resource "oci_file_storage_export" "oketest_export15" {
  #Required
  export_set_id  = oci_file_storage_export_set.oketest_export_set.id
  file_system_id = oci_file_storage_file_system.oketest_fs15.id
  path           = "/oketest15"
}

