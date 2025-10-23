# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

resource "null_resource" "delete_vault_storage_backend_bucket" {
  triggers = {
    vault_primary_node   = element(module.vault-autoscaler.instances, 0)
    bastion_public_ip    = var.bastion_public_ip
    bastion_user         = var.bastion_user
    bastion_private_key  = var.enable_bastion || var.bastion_public_ip != "" ? file(var.bastion_private_key_path) : ""
    compute_user         = var.compute_user
    ssh_private_key_path = var.ssh_private_key_path
    prefix               = var.prefix
  }
  provisioner "remote-exec" {
    when = destroy
    connection {
      agent               = false
      timeout             = "10m"
      host                = self.triggers.vault_primary_node
      user                = self.triggers.compute_user
      private_key         = file(self.triggers.ssh_private_key_path)
      bastion_host        = self.triggers.bastion_public_ip
      bastion_user        = self.triggers.bastion_user
      bastion_private_key = self.triggers.bastion_private_key
    }
    inline = [
      "set -x",
      "export OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True && oci os object bulk-delete -bn ${self.triggers.prefix}-bucket --force",
      "export OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True && oci os object bulk-delete -bn ${self.triggers.prefix}-ha-bucket --force"
    ]
  }
}

resource "oci_objectstorage_bucket" "vault_storage_backend_bucket" {
  compartment_id = var.compartment_id
  name           = "${var.prefix}-bucket"
  namespace      = var.vault_namespace

  # Optional
  freeform_tags  = var.freeform_tags
}

resource "oci_objectstorage_bucket" "vault_storage_backend_ha_bucket" {
  compartment_id = var.compartment_id
  name           = "${var.prefix}-ha-bucket"
  namespace      = var.vault_namespace

  # Optional
  freeform_tags  = var.freeform_tags
}

module "images" {
  source = "../oci-ocne-images"

  compartment_id = var.compartment_id
  os_version     = "8"
}

module "vault-autoscaler" {
  source = "../oci-ocne-autoscaling"

  availability_domain_id = var.availability_domain_id
  compartment_id         = var.compartment_id
  prefix                 = var.prefix
  instance_shape         = var.instance_shape
  image_ocid             = module.images.image_ocid
  subnet_id              = var.subnet_id
  ssh_public_key_path    = var.ssh_public_key_path
  pool_size              = var.pool_size
  load_balancer_protocol = var.load_balancer_protocol
  load_balancer_port     = var.load_balancer_port
  load_balancer_ocid     = var.load_balancer_ocid
  load_balancer_ip       = var.load_balancer_ip
  load_balancer_shape    = var.load_balancer_shape
  create_load_balancer   = var.create_load_balancer

  instance_user_data = data.template_cloudinit_config.vault.rendered
  freeform_tags      = var.freeform_tags
}

module "vault-kms" {
  source = "../oci-ocne-kms"

  compartment_id         = var.compartment_id
  prefix                 = var.prefix
  vault_ocid             = var.vault_ocid
  key_ocid               = var.key_ocid
  freeform_tags          = var.freeform_tags
}

resource "null_resource" "vault_setup" {
  depends_on = [module.vault-autoscaler]

  provisioner "remote-exec" {
    connection {
      agent               = false
      timeout             = "10m"
      host                = element(module.vault-autoscaler.instances, 0)
      user                = var.compute_user
      private_key         = file(var.ssh_private_key_path)
      bastion_host        = var.bastion_public_ip
      bastion_user        = var.bastion_user
      bastion_private_key = var.enable_bastion || var.bastion_public_ip != "" ? file(var.bastion_private_key_path) : ""
    }
    inline = [
      "set -x",
      "sudo /usr/bin/cloud-init status --wait 1> /dev/null",
      "timeout 300 bash -c 'while [[ \"$(curl -skL -o /dev/null -w ''%%{http_code}'' https://${module.vault-autoscaler.load_balancer_ip}:${var.load_balancer_port})\" != \"200\" ]]; do sleep 1; done' || false"
    ]
  }
}
