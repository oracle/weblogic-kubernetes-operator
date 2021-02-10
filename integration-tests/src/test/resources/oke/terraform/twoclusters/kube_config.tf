/*
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/

variable "cluster_kube_config_expiration" { default = 2592000 }
variable "cluster_kube_config_token_version" { default = "1.0.0" }

data "oci_containerengine_cluster_kube_config" "tfsample_cluster1_kube_config" {
  #Required
  cluster_id = "${oci_containerengine_cluster.tfsample_cluster1.id}"
}

data "oci_containerengine_cluster_kube_config" "tfsample_cluster2_kube_config" {
  #Required
  cluster_id = "${oci_containerengine_cluster.tfsample_cluster2.id}"
}


resource "local_file" "tfsample_cluster1_kube_config_file" {
  content     = "${data.oci_containerengine_cluster_kube_config.tfsample_cluster1_kube_config.content}"
  filename = "${path.module}/${var.cluster_name1}_kubeconfig"
}
resource "local_file" "tfsample_cluster2_kube_config_file" {
  content     = "${data.oci_containerengine_cluster_kube_config.tfsample_cluster2_kube_config.content}"
  filename = "${path.module}/${var.cluster_name2}_kubeconfig"
}
