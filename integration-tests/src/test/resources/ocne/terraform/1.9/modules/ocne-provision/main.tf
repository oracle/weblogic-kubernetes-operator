# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {
  agents = concat(var.control_plane_nodes, var.worker_nodes)
}

resource "null_resource" "ocne_provision_oci_key" {
  connection {
    agent               = false
    timeout             = "5m"
    host                = var.apiserver_ip
    user                = var.compute_user
    private_key         = file(var.ssh_private_key_path)
    bastion_host        = var.bastion_public_ip
    bastion_user        = var.bastion_user
    bastion_private_key = var.enable_bastion ? file(var.bastion_private_key_path) : ""
  }
  count = var.oci_api_key_path == "" ? 0 : 1
  provisioner "file" {
    source     = var.oci_api_key_path
    destination = "/home/${var.compute_user}/oci_api_key.pem"
  }
}

resource "null_resource" "ocne_provision_kubevirt_cfg" {
  connection {
    agent               = false
    timeout             = "5m"
    host                = var.apiserver_ip
    user                = var.compute_user
    private_key         = file(var.ssh_private_key_path)
    bastion_host        = var.bastion_public_ip
    bastion_user        = var.bastion_user
    bastion_private_key = var.enable_bastion ? file(var.bastion_private_key_path) : ""
  }
  count = var.kubevirt_config == "" ? 0 : 1
  provisioner "file" {
    source     = var.kubevirt_config
    destination = "/home/${var.compute_user}/kubevirt_config.yaml"
  }
}

resource "null_resource" "ocne_provision" {
  depends_on = [null_resource.ocne_provision_oci_key, null_resource.ocne_provision_kubevirt_cfg]
  connection {
    agent               = false
    timeout             = "60m"
    host                = var.apiserver_ip
    user                = var.compute_user
    private_key         = file(var.ssh_private_key_path)
    bastion_host        = var.bastion_public_ip
    bastion_user        = var.bastion_user
    bastion_private_key = var.enable_bastion ? file(var.bastion_private_key_path) : ""
  }

  triggers = {
    control-plane-nodes = join(",", var.control_plane_nodes)
    worker-nodes        = join(",", var.worker_nodes)
  }

  provisioner "local-exec" {
    command = <<EOF
    if [[ "${var.config_file_path}" != "" ]] ; then
       cp "${var.config_file_path}" ${path.module}/config-file.yaml
    else
       cp ${path.module}/config-file.yaml.example ${path.module}/config-file.yaml
    fi
    sh ${path.module}/files/config-edit.sh ${path.module}/config-file.yaml \
       ${var.environment_name} ${var.kubernetes_name} \
       ${var.apiserver_ip} \
       ${var.kube_apiserver_ip} ${var.kube_apiserver_port} ${var.virtual_ip} \
       ${join(",", formatlist("%s:8090", var.control_plane_nodes) )} \
       ${join(",", formatlist("%s:8090", var.worker_nodes))} \
       "${var.proxy}" "${var.no_proxy}" "${var.container_registry}" "${var.compute_user}" "${var.ocne_version}"
    EOF
  }

  provisioner "file" {
    source     = "${path.module}/config-file.yaml"
    destination = "/home/${var.compute_user}/config-file.yaml"
  }

  provisioner "file" {
    content     = data.template_file.provision.rendered
    destination = "/home/${var.compute_user}/provision.sh"
  }

  provisioner "remote-exec" {
    inline = [
      "set -x",
      "bash /home/${var.compute_user}/provision.sh",
    ]
  }
}

resource "null_resource" "fetch_kubeconfig" {
  depends_on = [null_resource.ocne_provision]

  provisioner "local-exec" {
    command = <<EOT
			if [[ ${var.enable_bastion} == true || "${var.bastion_public_ip}" != "" ]] ; then scp -r -i '${var.ssh_private_key_path}' -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -o ProxyCommand='ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i '${var.bastion_private_key_path}' -W %h:%p ${var.bastion_user}@${var.bastion_public_ip}' ${var.compute_user}@${var.apiserver_ip}:/home/${var.compute_user}/kubeconfig.${var.environment_name}.${var.kubernetes_name} ${abspath("./kubeconfig")} ; fi
			if [[ ${var.enable_bastion} == false && "${var.bastion_public_ip}" == "" ]] ; then scp -r -i '${var.ssh_private_key_path}' -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ${var.compute_user}@${var.apiserver_ip}:/home/${var.compute_user}/kubeconfig.${var.environment_name}.${var.kubernetes_name} ${abspath("./kubeconfig")} ; fi
			EOT
  }
}

resource "null_resource" "cleanup" {
  depends_on = [null_resource.ocne_provision]
  provisioner "local-exec" {
    when    = destroy
    command = "rm ${path.module}/config-file.yaml"
  }
}
