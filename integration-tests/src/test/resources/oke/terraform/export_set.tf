// Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
// Licensed under the Mozilla Public License v2.0

resource "oci_file_storage_export_set" "oketest_export_set" {
  # Required
  mount_target_id = "${oci_file_storage_mount_target.oketest_mount_target.id}"
}
