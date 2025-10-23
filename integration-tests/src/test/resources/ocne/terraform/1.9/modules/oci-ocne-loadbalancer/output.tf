# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

output "ip_address" {
  description = "IP address of load balancer."
  value       = var.instance_count > 0 ? oci_load_balancer_load_balancer.kube_apiserver_lb[0].ip_address_details[0].ip_address: ""
}

output "port" {
  description = "The port that the listener should serve traffic on"
  value       = var.port
}

output "endpoint" {
  depends_on  = [oci_load_balancer_listener.listener, oci_load_balancer_backend.backends]
  description = "Load balancer URI."
  value       = var.instance_count > 0 ? "${oci_load_balancer_load_balancer.kube_apiserver_lb[0].ip_address_details[0].ip_address}:${var.port}" : ""
}

output "load_balancer_ocid" {
  description = "OCID of the load balancer."
  value       = var.instance_count > 0 ? oci_load_balancer_load_balancer.kube_apiserver_lb[0].id : ""
}
