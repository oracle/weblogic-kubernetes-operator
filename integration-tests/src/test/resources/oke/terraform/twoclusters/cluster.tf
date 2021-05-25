/*
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/
variable "cluster_kubernetes_version" {
  default = "v1.17.9"
}

variable "cluster_name1" {
  default = "tfTestCluster1"
}

variable "cluster_name2" {
  default = "tfTestCluster2"
}

variable "cluster_name" {
  default = "twoTestClusters"
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

variable "node_pool_name1" {
  default = "tfTestCluster_workers1"
}

variable "node_pool_name2" {
  default = "tfTestCluster_workers2"
}

variable "node_pool_node_image_name" {
  default = "Oracle-Linux-7.6"
}

variable "node_pool_node_shape" {
  default = "VM.Standard2.1"
}

variable "node_pool_quantity_per_subnet" {
  default = 2
}

variable "node_pool_ssh_public_key" {
}

data "oci_identity_availability_domains" "tfsample_availability_domains" {
  compartment_id = var.compartment_ocid
}

resource "oci_containerengine_cluster" "tfsample_cluster1" {
  #Required
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.cluster_kubernetes_version
  name               = var.cluster_name1
  vcn_id             = oci_core_virtual_network.oke-vcn.id

  #Optional
  options {
    service_lb_subnet_ids = [oci_core_subnet.oke-subnet-loadbalancer-1.id, oci_core_subnet.oke-subnet-loadbalancer-2.id]

    #Optional
    add_ons {
      #Optional
      is_kubernetes_dashboard_enabled = var.cluster_options_add_ons_is_kubernetes_dashboard_enabled
      is_tiller_enabled               = var.cluster_options_add_ons_is_tiller_enabled
    }
  }
}

resource "oci_containerengine_cluster" "tfsample_cluster2" {
  #Required
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.cluster_kubernetes_version
  name               = var.cluster_name2
  vcn_id             = oci_core_virtual_network.oke-vcn.id

  #Optional
  options {
    service_lb_subnet_ids = [oci_core_subnet.oke-subnet-loadbalancer-1.id, oci_core_subnet.oke-subnet-loadbalancer-2.id]

    #Optional
    add_ons {
      #Optional
      is_kubernetes_dashboard_enabled = var.cluster_options_add_ons_is_kubernetes_dashboard_enabled
      is_tiller_enabled               = var.cluster_options_add_ons_is_tiller_enabled
    }
  }
}

resource "oci_containerengine_node_pool" "tfsample_node_pool1" {
  #Required
  cluster_id         = oci_containerengine_cluster.tfsample_cluster1.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.node_pool_kubernetes_version
  name               = var.node_pool_name1
  node_image_name    = var.node_pool_node_image_name
  node_shape         = var.node_pool_node_shape

  #subnet_ids = ["${oci_core_subnet.oke-subnet-worker-1.id}", "${oci_core_subnet.oke-subnet-worker-2.id}","${oci_core_subnet.oke-subnet-worker-3.id}"]
  subnet_ids = [oci_core_subnet.oke-subnet-worker-1.id, oci_core_subnet.oke-subnet-worker-2.id]

  #Optional
  quantity_per_subnet = var.node_pool_quantity_per_subnet
  ssh_public_key      = var.node_pool_ssh_public_key
}

resource "oci_containerengine_node_pool" "tfsample_node_pool2" {
  #Required
  cluster_id         = oci_containerengine_cluster.tfsample_cluster2.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.node_pool_kubernetes_version
  name               = var.node_pool_name2
  node_image_name    = var.node_pool_node_image_name
  node_shape         = var.node_pool_node_shape

  #subnet_ids = ["${oci_core_subnet.oke-subnet-worker-1.id}", "${oci_core_subnet.oke-subnet-worker-2.id}","${oci_core_subnet.oke-subnet-worker-3.id}"]
  subnet_ids = [oci_core_subnet.oke-subnet-worker-1.id, oci_core_subnet.oke-subnet-worker-2.id]

  #Optional
  quantity_per_subnet = var.node_pool_quantity_per_subnet
  ssh_public_key      = var.node_pool_ssh_public_key
}

output "cluster1_id" {
  value = oci_containerengine_cluster.tfsample_cluster1.id
}

output "cluster2_id" {
  value = oci_containerengine_cluster.tfsample_cluster2.id
}

