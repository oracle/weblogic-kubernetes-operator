# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {

  all_cluster_ids = merge(
    { c1 = one(element([module.c1[*].cluster_id], 0)) }
  )

  kubeconfig_templates = {
    for cluster_name, cluster_id in local.all_cluster_ids :
    cluster_name => templatefile("${path.module}/scripts/generate_kubeconfig.template.sh",
      {
        cluster_id = cluster_id
        endpoint   = var.oke_control_plane == "public" ? "PUBLIC_ENDPOINT" : "PRIVATE_ENDPOINT"
        region     = lookup(local.regions, lookup(lookup(var.clusters, cluster_name), "region"))
      }
    )
  }

  set_credentials_templates = {
    for cluster_name, cluster_id in local.all_cluster_ids :
    cluster_name => templatefile("${path.module}/scripts/kubeconfig_set_credentials.template.sh",
      {
        cluster_id    = cluster_id
        cluster_id_11 = substr(cluster_id, (length(cluster_id) - 11), length(cluster_id))
        region        = lookup(local.regions, lookup(lookup(var.clusters, cluster_name), "region"))
      }
    )
  }

  set_alias_templates = {
    for cluster_name, cluster_id in local.all_cluster_ids :
    cluster_name => templatefile("${path.module}/scripts/set_alias.template.sh",
      {
        cluster       = cluster_name
        cluster_id_11 = substr(cluster_id, (length(cluster_id) - 11), length(cluster_id))
      }
    )
  }

  token_helper_template = templatefile("${path.module}/scripts/token_helper.template.sh", {})

}
