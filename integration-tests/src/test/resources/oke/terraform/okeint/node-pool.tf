/*
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/

variable "private_subnet_ocid" {
}

variable "node_pool_node_config_details_node_pool_pod_network_option_details_cni_type" {
  default = "OCI_VCN_IP_NATIVE"
}

variable "node_pool_node_config_details_node_pool_pod_network_option_details_pod_nsg_ids" {
  default = []
}

variable "node_pool_node_config_details_node_pool_pod_network_option_details_pod_subnet_ids" {
  default = []
}

data "oci_identity_availability_domain" "ad1" {
     compartment_id = var.tenancy_ocid
     ad_number      = 1
}


resource "oci_containerengine_node_pool" "tfsample_node_pool" {
  #Required
  cluster_id         = oci_containerengine_cluster.tfsample_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.node_pool_kubernetes_version
  name               = var.node_pool_name
  node_shape         = var.node_pool_node_shape
  #subnet_ids = ["ocid1.subnet.oc1.phx.aaaaaaaah6tarxgsz2gcbaocbugpw5gjznbo7ylgtfb4yyonaezhbkm3a2xa"]

  timeouts {
      create = "60m"
      delete = "2h"
  }
  
  node_eviction_node_pool_settings{
	is_force_delete_after_grace_duration = true
	eviction_grace_duration = "PT0M"
  }
  node_config_details {
		#Required
		placement_configs {
		#Required
		  #availability_domain = "mFEn:PHX-AD-1"
		  availability_domain = data.oci_identity_availability_domain.ad1.name
		  subnet_id = var.private_subnet_ocid

		}
		size = 2
  }
  node_shape_config {
       #Optional
 	memory_in_gbs = 48.0
        ocpus = 4.0
  }

  # Using image Oracle-Linux-7.x-<date>
  # Find image OCID for your region from https://docs.oracle.com/iaas/images/
  node_source_details {
       image_id = var.node_pool_node_image_name
       source_type = "image"
       boot_volume_size_in_gbs = "200"
  }
  # Optional
  initial_node_labels {
      key = "name"
      value = "var.cluster_name"
  }
  ssh_public_key      = var.node_pool_ssh_public_key
}

