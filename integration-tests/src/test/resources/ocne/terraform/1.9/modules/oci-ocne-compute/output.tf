# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

output "private_ip" {
  description = "Private IP address of instance."
  value       = oci_core_instance.instance.*.private_ip
}

output "secondary_private_ip" {
  depends_on = [null_resource.assign_vnics]
  value      = data.oci_core_vnic.second_vnic.*.private_ip_address
}

output "node_ocids" {
  description = "Comma separated list of Kubernetes nodes (both control plane and worker nodes) with their Oracle Cloud Identifiers (OCIDs)."
  value       = oci_core_instance.instance.*.id
}
