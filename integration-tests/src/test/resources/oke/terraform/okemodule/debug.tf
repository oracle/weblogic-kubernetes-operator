/*
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
########################################
# DEBUG / CI VALIDATION OUTPUTS
# (safe to remove after validation)
########################################

output "DEBUG_var_cluster_name" {
  description = "Cluster name as passed via tfvars"
  value       = var.cluster_name
}

output "DEBUG_local_cluster_name" {
  description = "Cluster name as resolved inside locals"
  value       = local.cluster_name
}

output "DEBUG_module_cluster_name" {
  description = "Cluster name as seen by the OKE module"
  value       = one(module.c1[*].cluster_name)
}

