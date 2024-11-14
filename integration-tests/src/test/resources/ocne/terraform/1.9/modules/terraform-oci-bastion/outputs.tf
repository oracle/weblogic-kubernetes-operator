# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

output "bastion_public_ip" {
  value = join(",", data.oci_core_vnic.bastion_vnic.*.public_ip_address)
}