# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {
  control_plane_nodes = contains(keys(module.control-plane-compute), "private_ip") ? (var.standalone_api_server ? flatten(module.control-plane-compute.private_ip) : flatten(concat(module.api-server-compute.private_ip, module.control-plane-compute.private_ip))) : []
  apiserver_ip        = contains(keys(module.api-server-compute), "private_ip") ? element(module.api-server-compute.private_ip, 0) : ""
  agents              = flatten(concat(module.control-plane-compute.private_ip, module.worker-compute.private_ip))
  all_nodes           = distinct(concat([local.apiserver_ip], local.control_plane_nodes, local.agents))
  total_nodes         = var.control_plane_node_count + var.worker_node_count + (var.standalone_api_server ? 1 : 0)
  ocne_short_version  = join("", [element(split("", var.ocne_version), 0), element(split("", var.ocne_version), 2)])
  node_ocids          = flatten(concat(module.control-plane-compute.node_ocids, module.worker-compute.node_ocids))

  apiserver_init = templatefile("${path.module}/files/apiserver_init.sh", {
    os_version         = var.os_version
    yum_repo_url       = var.yum_repo_url
    ocne_short_version = local.ocne_short_version
    ocne_version       = var.ocne_version
    compute_user       = var.compute_user
    kernel_version     = var.kernel_version
    }
  )

  agent_init = templatefile("${path.module}/files/agent_init.sh", {
    os_version   = var.os_version
    yum_repo_url = var.yum_repo_url
    }
  )
}
