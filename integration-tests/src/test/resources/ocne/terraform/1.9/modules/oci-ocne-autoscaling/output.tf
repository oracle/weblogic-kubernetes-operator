# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

output "instances" {
  description = "Private IP addresses of instances."
  value       = data.oci_core_instance.instances.*.private_ip
}

output "load_balancer_ip" {
  description = "The canonical IP address that the load balancer serves traffic from."
  value       = var.create_load_balancer ? oci_load_balancer_load_balancer.lb[0].ip_address_details[0].ip_address : var.load_balancer_ip
}
