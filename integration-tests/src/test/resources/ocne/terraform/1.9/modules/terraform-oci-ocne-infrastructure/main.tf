# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

module "images" {
  source         = "../oci-ocne-images"
  count          = length(var.image_ocid) != 0 ? 0 : 1
  compartment_id = var.compartment_id
  os_version     = var.os_version
}

module "kube-apiserver-loadbalancer" {
  source         = "../oci-ocne-loadbalancer"
  compartment_id = var.compartment_id
  prefix         = "${var.prefix}-cp"
  subnet_id      = var.subnet_id
  shape          = var.load_balancer_shape
  policy         = var.load_balancer_policy
  protocol       = "TCP"
  port           = var.kube_apiserver_port
  backends       = { for ip in local.control_plane_nodes : ip => var.kube_apiserver_port }
  backend_count  = var.control_plane_node_count
  instance_count = var.virtual_ip ? 0 : 1

  # Optional
  freeform_tags = var.freeform_tags
}

module "api-server-compute" {
  source     = "../oci-ocne-compute"
  depends_on = [module.images]

  availability_domain_id   = var.availability_domain_id
  compartment_id           = var.compartment_id
  prefix                   = "${var.prefix}-api-server"
  init_script              = local.apiserver_init
  instance_count           = 1
  image_ocid               = length(var.image_ocid) != 0 ? var.image_ocid : module.images[0].image_ocid
  subnet_id                = var.subnet_id
  instance_shape           = var.instance_shape
  ssh_public_key_path      = var.ssh_public_key_path
  ssh_private_key_path     = var.ssh_private_key_path
  enable_bastion           = var.enable_bastion
  bastion_public_ip        = var.bastion_public_ip
  bastion_user             = var.bastion_user
  bastion_private_key_path = var.bastion_private_key_path
  compute_user             = var.compute_user
  freeform_tags            = var.freeform_tags
  attach_secondary_vnic    = var.standalone_api_server ? false : var.virtual_ip
}

module "control-plane-compute" {
  source     = "../oci-ocne-compute"
  depends_on = [module.images]

  availability_domain_id   = var.availability_domain_id
  compartment_id           = var.compartment_id
  prefix                   = "${var.prefix}-control-plane"
  init_script              = local.agent_init
  instance_count           = var.standalone_api_server ? var.control_plane_node_count : var.control_plane_node_count - 1
  image_ocid               = length(var.image_ocid) != 0 ? var.image_ocid : module.images[0].image_ocid
  subnet_id                = var.subnet_id
  instance_shape           = var.instance_shape
  ssh_public_key_path      = var.ssh_public_key_path
  ssh_private_key_path     = var.ssh_private_key_path
  enable_bastion           = var.enable_bastion
  bastion_public_ip        = var.bastion_public_ip
  bastion_user             = var.bastion_user
  bastion_private_key_path = var.bastion_private_key_path
  compute_user             = var.compute_user
  freeform_tags            = var.freeform_tags
  attach_secondary_vnic    = var.standalone_api_server ? var.virtual_ip : false
}

module "worker-compute" {
  source     = "../oci-ocne-compute"
  depends_on = [module.images]

  availability_domain_id   = var.availability_domain_id
  compartment_id           = var.compartment_id
  prefix                   = "${var.prefix}-worker"
  init_script              = local.agent_init
  instance_count           = var.worker_node_count
  image_ocid               = length(var.image_ocid) != 0 ? var.image_ocid : module.images[0].image_ocid
  subnet_id                = var.subnet_id
  instance_shape           = var.instance_shape
  ssh_public_key_path      = var.ssh_public_key_path
  ssh_private_key_path     = var.ssh_private_key_path
  enable_bastion           = var.enable_bastion
  bastion_public_ip        = var.bastion_public_ip
  bastion_user             = var.bastion_user
  bastion_private_key_path = var.bastion_private_key_path
  compute_user             = var.compute_user
  freeform_tags            = var.freeform_tags
}

resource "null_resource" "copy_apiserver_ssh_key" {
  triggers = {
    control-plane-nodes = join(",", module.control-plane-compute.private_ip)
    worker-nodes        = join(",", module.worker-compute.private_ip)
  }
  count      = length(local.agents)
  depends_on = [module.api-server-compute, module.control-plane-compute, module.worker-compute]

  provisioner "local-exec" {
    command = <<EOF
                        if [[ ${var.enable_bastion} == true || "${var.bastion_public_ip}" != "" ]] ; then
                                apiserver_ssh_key=$(ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no -o ProxyCommand="ssh -i ${var.bastion_private_key_path} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -W %h:%p ${var.bastion_user}@${var.bastion_public_ip}" ${var.compute_user}@${local.apiserver_ip} cat /home/${var.compute_user}/.ssh/id_rsa.pub) && \
                                ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no -o ProxyCommand="ssh -i ${var.bastion_private_key_path} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -W %h:%p ${var.bastion_user}@${var.bastion_public_ip}" ${var.compute_user}@${local.agents[count.index]} 'echo '$apiserver_ssh_key' | tee -a /home/${var.compute_user}/.ssh/authorized_keys'
                        fi

                        if [[ ${var.enable_bastion} == false && "${var.bastion_public_ip}" == "" ]] ; then
                                apiserver_ssh_key=$(ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no ${var.compute_user}@${local.apiserver_ip} cat /home/${var.compute_user}/.ssh/id_rsa.pub) && \
                                ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no ${var.compute_user}@${local.agents[count.index]} 'echo '$apiserver_ssh_key' | tee -a /home/${var.compute_user}/.ssh/authorized_keys'
                        fi
                        EOF
  }
}

resource "null_resource" "update_kernel" {
  # ol8_UEKR6 is current default; review periodically.
  # A reboot is required when kernel is changed.
  # kata complains about multiple versions of kernel-uek-containers during kubelet instalation so orignal UEK kernel must be removed.
  count      = var.kernel_version != "ol8_UEKR6" ? local.total_nodes : 0
  depends_on = [module.api-server-compute, module.control-plane-compute, module.worker-compute, null_resource.copy_apiserver_ssh_key]

  connection {
    agent               = false
    timeout             = "10m"
    host                = local.all_nodes[count.index]
    user                = var.compute_user
    private_key         = file(var.ssh_private_key_path)
    bastion_host        = var.bastion_public_ip
    bastion_user        = var.bastion_user
    bastion_private_key = var.enable_bastion || var.bastion_public_ip != "" ? file(var.bastion_private_key_path) : ""
  }

  provisioner "remote-exec" {
    inline = [
      "rpm -qa | grep uek > RPM"
    ]
  }

  provisioner "local-exec" {
    command = <<EOF
                        if [[ ${var.enable_bastion} == true || "${var.bastion_public_ip}" != "" ]] ; then
                                ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no -o ProxyCommand="ssh -i ${var.bastion_private_key_path} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -W %h:%p ${var.bastion_user}@${var.bastion_public_ip}" ${var.compute_user}@${local.all_nodes[count.index]} \
				'sudo dnf install -y kernel-uek && \
				(sleep 2 && \
				sudo reboot)&' && \
				until ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no ${var.compute_user}@${local.all_nodes[count.index]} true 2> /dev/null ; do :; done

				ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no -o ProxyCommand="ssh -i ${var.bastion_private_key_path} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -W %h:%p ${var.bastion_user}@${var.bastion_public_ip}" ${var.compute_user}@${local.all_nodes[count.index]} \
				'sudo dnf remove -y $RPM && \
				rm -f RPM'
                        fi

                        if [[ ${var.enable_bastion} == false && "${var.bastion_public_ip}" == "" ]] ; then
                                ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no ${var.compute_user}@${local.all_nodes[count.index]} \
				'rpm -qa | grep uek > RPM && \
				sudo dnf install -y kernel-uek && \
			       	(sleep 2 && \
				sudo reboot)&' && \
				until ssh -i ${var.ssh_private_key_path} -o StrictHostKeyChecking=no ${var.compute_user}@${local.all_nodes[count.index]} true 2> /dev/null ; do :; done
                        fi
                        EOF
  }

  provisioner "remote-exec" {
    inline = [
      "sudo dnf remove -y $(cat RPM)",
      "rm -f RPM"
    ]
  }
}
