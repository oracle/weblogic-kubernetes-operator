# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/

data "oci_identity_availability_domains" "ad_list" {
  compartment_id = var.tenancy_id
}

data "template_file" "ad_names" {
  count    = length(data.oci_identity_availability_domains.ad_list.availability_domains)
  template = lookup(data.oci_identity_availability_domains.ad_list.availability_domains[count.index], "name")
}

data "oci_identity_tenancy" "tenancy" {
  tenancy_id = var.tenancy_id
}

# get the tenancy's home region
data "oci_identity_regions" "home_region" {
  filter {
    name   = "key"
    values = [data.oci_identity_tenancy.tenancy.home_region_key]
  }
}

data "oci_core_vcn" "vcn" {
  vcn_id = var.vcn_id
}

data "template_file" "autonomous_template" {
  template = file("${path.module}/scripts/notification.template.sh")

  vars = {
    enable_notification = var.enable_notification
    topic_id             = var.enable_notification == true ? oci_ons_notification_topic.bastion_notification[0].topic_id : "null"
  }

  count = var.bastion_image_id == "Autonomous" ? 1 : 0
}

data "template_file" "autonomous_cloud_init_file" {
  template = file("${path.module}/cloudinit/autonomous.template.yaml")

  vars = {
    notification_sh_content = base64gzip(data.template_file.autonomous_template[0].rendered)
    timezone                = var.timezone
  }

  count = var.bastion_image_id == "Autonomous" ? 1 : 0
}

data "oci_core_images" "autonomous_images" {
  compartment_id   = var.compartment_id
  operating_system = "Oracle Autonomous Linux"
  shape            = lookup(var.bastion_shape, "shape", "VM.Standard.E2.2")
  sort_by          = "TIMECREATED"
}

# cloud init for bastion
data "template_cloudinit_config" "bastion" {
  gzip          = true
  base64_encode = true

  part {
    filename     = "bastion.yaml"
    content_type = "text/cloud-config"
    content      = data.template_file.autonomous_cloud_init_file[0].rendered
  }
}

# Gets a list of VNIC attachments on the bastion instance
data "oci_core_vnic_attachments" "bastion_vnics_attachments" {
  availability_domain = element(local.ad_names, (var.availability_domain - 1))
  compartment_id      = var.compartment_id
  depends_on          = [oci_core_instance.bastion]
  instance_id         = oci_core_instance.bastion.id
}

# Gets the OCID of the first (default) VNIC on the bastion instance
data "oci_core_vnic" "bastion_vnic" {
  depends_on = [oci_core_instance.bastion]
  vnic_id    = lookup(data.oci_core_vnic_attachments.bastion_vnics_attachments.vnic_attachments[0], "vnic_id")
}

data "oci_core_instance" "bastion" {
  depends_on  = [oci_core_instance.bastion]
  instance_id = oci_core_instance.bastion.id
}

data "oci_ons_notification_topic" "bastion_notification" {
  topic_id = oci_ons_notification_topic.bastion_notification[0].topic_id

  count = var.enable_notification ? 1 : 0
}
