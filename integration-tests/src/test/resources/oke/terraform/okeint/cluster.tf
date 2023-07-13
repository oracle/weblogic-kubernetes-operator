/*
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/

// Compartment in which to create the cluster resources.
variable "compartment_name" {
}

variable "compartment_ocid" {
}

variable "sub_compartment_ocid" {
}

variable "cluster_kubernetes_version" {
  default = "v1.17.9"
}

variable "cluster_name" {
  default = "tfTestCluster"
}

variable "cluster_options_add_ons_is_kubernetes_dashboard_enabled" {
  default = true
}

variable "cluster_options_add_ons_is_tiller_enabled" {
  default = true
}

variable "cluster_options_kubernetes_network_config_pods_cidr" {
  default = "10.1.0.0/16"
}

variable "cluster_options_kubernetes_network_config_services_cidr" {
  default = "10.2.0.0/16"
}

variable "node_pool_initial_node_labels_key" {
  default = "key"
}

variable "node_pool_initial_node_labels_value" {
  default = "value"
}

variable "node_pool_kubernetes_version" {
  default = "v1.17.9"
}

variable "node_pool_name" {
  default = "tfTestCluster_workers"
}

variable "node_pool_node_image_name" {
  default = "Oracle-Linux-7.6"
}

variable "node_pool_node_shape" {
  default = "VM.Standard2.1"
}

variable "node_pool_quantity_per_subnet" {
  default = 1
}

variable "node_pool_ssh_public_key" {
}

variable "vcn_ocid" {
}

variable "pub_subnet_ocid" {
}

variable "cluster_cluster_pod_network_options_cni_type" {
  default = "OCI_VCN_IP_NATIVE"
}

data "oci_identity_availability_domains" "tfsample_availability_domains" {
  compartment_id = var.sub_compartment_ocid
}

resource "oci_containerengine_cluster" "tfsample_cluster" {
  #Required
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.cluster_kubernetes_version
  name               = var.cluster_name
  vcn_id             = var.vcn_ocid

  endpoint_config {

        #Optional
        is_public_ip_enabled = "true"
        subnet_id = var.pub_subnet_ocid
    }
  #Optional
  options {
    service_lb_subnet_ids = [var.pub_subnet_ocid]

    #Optional
    add_ons {
      #Optional
      is_kubernetes_dashboard_enabled = var.cluster_options_add_ons_is_kubernetes_dashboard_enabled
      is_tiller_enabled               = var.cluster_options_add_ons_is_tiller_enabled
    }
  }
}


output "cluster_id" {
  value = oci_containerengine_cluster.tfsample_cluster.id
}

