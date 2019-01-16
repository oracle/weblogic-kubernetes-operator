variable "cluster_kubernetes_version" { default = "v1.10.3" }
variable "cluster_name" { default = "tfTestCluster" }
variable "cluster_options_add_ons_is_kubernetes_dashboard_enabled" { default = true }
variable "cluster_options_add_ons_is_tiller_enabled" { default = true }
variable "cluster_options_kubernetes_network_config_pods_cidr" { default = "10.1.0.0/16" }
variable "cluster_options_kubernetes_network_config_services_cidr" { default = "10.2.0.0/16" }
variable "node_pool_initial_node_labels_key" { default = "key" }
variable "node_pool_initial_node_labels_value" { default = "value" }
variable "node_pool_kubernetes_version" { default = "v1.10.3" }
variable "node_pool_name" { default = "tfTestCluster_workers" }
variable "node_pool_node_image_name" { default = "Oracle-Linux-7.4" }
variable "node_pool_node_shape" { default = "VM.Standard1.1" }
variable "node_pool_quantity_per_subnet" { default = 2 }
variable "node_pool_ssh_public_key" { }

data "oci_identity_availability_domains" "test_availability_domains" {
  compartment_id = "${var.compartment_ocid}"
}

// Defined in oke.tf
/*resource "oci_core_virtual_network" "oke-vcn" {
  cidr_block = "${var.vcn_cidr}"
  compartment_id = "${var.compartment_ocid}"
  display_name = "${var.cluster_name}_vcn"
}*/

resource "oci_containerengine_cluster" "test_cluster" {
  #Required
  compartment_id = "${var.compartment_ocid}"
  kubernetes_version = "${var.cluster_kubernetes_version}"
  name = "${var.cluster_name}"
  vcn_id = "${oci_core_virtual_network.oke-vcn.id}"

  #Optional
  options {
    service_lb_subnet_ids = ["${oci_core_subnet.oke-subnet-loadbalancer-1.id}", "${oci_core_subnet.oke-subnet-loadbalancer-2.id}"]

    #Optional
    add_ons {
      #Optional
      is_kubernetes_dashboard_enabled = "${var.cluster_options_add_ons_is_kubernetes_dashboard_enabled}"
      is_tiller_enabled = "${var.cluster_options_add_ons_is_tiller_enabled}"
    }
    #kubernetes_network_config {
      #Optional
      #pods_cidr = "${var.cluster_options_kubernetes_network_config_pods_cidr}"
      #services_cidr = "${var.cluster_options_kubernetes_network_config_services_cidr}"
    #}
  }
}

resource "oci_containerengine_node_pool" "test_node_pool" {
	#Required
	cluster_id = "${oci_containerengine_cluster.test_cluster.id}"
	compartment_id = "${var.compartment_ocid}"
	kubernetes_version = "${var.node_pool_kubernetes_version}"
	name = "${var.node_pool_name}"
	node_image_name = "${var.node_pool_node_image_name}"
	node_shape = "${var.node_pool_node_shape}"
        subnet_ids = ["${oci_core_subnet.oke-subnet-worker-1.id}", "${oci_core_subnet.oke-subnet-worker-2.id}","${oci_core_subnet.oke-subnet-worker-3.id}"]

	#Optional
	#initial_node_labels {

		#Optional
	#	key = "${var.node_pool_initial_node_labels_key}"
	#	value = "${var.node_pool_initial_node_labels_value}"
	#}
	quantity_per_subnet = "${var.node_pool_quantity_per_subnet}"
	ssh_public_key = "${var.node_pool_ssh_public_key}"
}

output "cluster_id" {
  value = "${oci_containerengine_cluster.test_cluster.id}"
}
