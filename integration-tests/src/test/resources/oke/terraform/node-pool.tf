/*
# Copyright (c) 2022, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
resource "oci_containerengine_node_pool" "tfsample_node_pool" {
  #Required
  cluster_id         = oci_containerengine_cluster.tfsample_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.node_pool_kubernetes_version
  name               = var.node_pool_name
  node_shape         = var.node_pool_node_shape
  subnet_ids = [oci_core_subnet.oke-subnet-worker-1.id, oci_core_subnet.oke-subnet-worker-2.id]

  timeouts {
      create = "60m"
      delete = "2h"
  }

  node_eviction_node_pool_settings{
	is_force_delete_after_grace_duration = true
	eviction_grace_duration = "PT0M"
  }


  # Using image Oracle-Linux-7.x-<date>
  # Find image OCID for your region from https://docs.oracle.com/iaas/images/
  node_source_details {
       image_id = var.node_pool_node_image_name
       source_type = "image"
       boot_volume_size_in_gbs = "200"
  }
  node_shape_config {
        #Optional
        memory_in_gbs = 48.0
        ocpus = 4.0
  }
  # Optional
  initial_node_labels {
      key = "name"
      value = "var.cluster_name"
  }
  ssh_public_key      = var.node_pool_ssh_public_key
}

