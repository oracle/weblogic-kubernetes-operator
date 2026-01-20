/*
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
########################################
# DEBUG / CI VALIDATION OUTPUTS
# (safe to remove after validation)
########################################

output "DEBUG_var_cluster_name" {
  value = var.cluster_name
}

output "DEBUG_local_cluster_name" {
  value = local.cluster_name
}

output "DEBUG_module_cluster_id" {
  value = try(one(module.c1[*].cluster_id), null)
}

