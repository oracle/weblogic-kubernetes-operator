# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

output "vault_ocne_client_token" {
  description = "OCNE certificate signing token."
  value       = data.external.fetch_token.result["vault_ocne_client_token"]
}

output "uri" {
  depends_on  = [null_resource.vault_setup]
  description = "Load balancer URI."
  value       = "https://${module.vault-autoscaler.load_balancer_ip}:${var.load_balancer_port}"
}

output "instances" {
  description = "List of vault instances."
  value       = module.vault-autoscaler.instances
}

output "vault_storage_bucket" {
  description = "vault storage backend bucket"
  value       = oci_objectstorage_bucket.vault_storage_backend_bucket.name
}

output "vault_ha_storage_bucket" {
  description = "vault storage HA backend bucket"
  value       = oci_objectstorage_bucket.vault_storage_backend_ha_bucket.name
}
