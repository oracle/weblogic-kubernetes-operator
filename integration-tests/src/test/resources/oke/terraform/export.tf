resource "oci_file_storage_export" "oketest_export" {
  #Required
  export_set_id  = "${oci_file_storage_export_set.oketest_export_set.id}"
  file_system_id = "${oci_file_storage_file_system.oketest_fs.id}"
  path           = "/oketest"
}
