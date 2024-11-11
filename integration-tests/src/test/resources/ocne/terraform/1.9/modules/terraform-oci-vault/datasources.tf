# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

data "template_file" "vault_config_template" {
  template = file("${path.module}/templates/vault.template")

  vars = {
    obj_namespace           = var.vault_namespace
    obj_bucket              = oci_objectstorage_bucket.vault_storage_backend_bucket.name
    obj_lock_bucket         = oci_objectstorage_bucket.vault_storage_backend_ha_bucket.name
    kms_key_id              = module.vault-kms.key_id
    kms_crypto_endpoint     = module.vault-kms.crypto_endpoint
    kms_management_endpoint = module.vault-kms.management_endpoint
  }
}

data "template_file" "vault_setup" {
  template = file("${path.module}/templates/vault_setup")

  vars = {
    VIF               = "/tmp/vault.init"
    VIF_base64        = "/tmp/vault_init_base64.json"
    VAULT_ADDR        = "https://127.0.0.1:8200"
    VAULT_SKIP_VERIFY = "true"
    user_id           = var.user_id
    region            = var.region
    compartment_id    = var.compartment_id
    vault_ocid        = var.vault_ocid
    key_ocid          = var.key_ocid
    secret_name       = var.secret_name
    ocne_secret_name  = var.ocne_secret_name
    secretfile        = ""
    RT                = ""
    CT                = "/tmp/client.token"
    CT_base64         = "/tmp/client_token_base64.json"
    api_key_file      = "/etc/vault.d/.oci/oci_api_key.pem"
    compute_user      = var.compute_user
  }
}

data "template_file" "cloudinit_template" {
  template = file("${path.module}/templates/cloudinit.template")

  vars = {
    vault_version           = var.vault_version
    config                  = base64gzip(data.template_file.vault_config_template.rendered)
    proxy                   = var.proxy
    tenancy_id              = var.tenancy_id
    user_id                 = var.user_id
    region                  = var.region
    fingerprint             = var.fingerprint
    password                = var.private_key_password
    key_file                = base64gzip(file(var.api_private_key_path))
    vault_setup             = base64gzip(data.template_file.vault_setup.rendered)
    lb_ip                   = module.vault-autoscaler.load_balancer_ip
    ocne-cert-engine-policy = base64gzip(file("${path.module}/templates/ocne-cert-engine-policy.hcl"))
  }
}

data "template_cloudinit_config" "vault" {
  gzip          = true
  base64_encode = true

  part {
    filename     = "vault.yaml"
    content_type = "text/cloud-config"
    content      = data.template_file.cloudinit_template.rendered
  }
}

data "external" "fetch_token" {
  depends_on = [null_resource.vault_setup]
  program    = ["sh", "${path.module}/templates/fetch_token"]
  query = {
    compute_user             = var.compute_user
    vault_host               = module.vault-autoscaler.instances[0]
    compartment_id           = var.compartment_id
    ocne_secret_name         = var.ocne_secret_name
    ssh_private_key_path     = var.ssh_private_key_path
    region                   = var.region
    bastion_user             = var.bastion_user
    bastion_public_ip        = var.bastion_public_ip
    bastion_private_key_path = var.bastion_private_key_path
    enable_bastion          = var.enable_bastion
  }
}
