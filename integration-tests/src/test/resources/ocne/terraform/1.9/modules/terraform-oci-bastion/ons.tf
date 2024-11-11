# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/

resource "oci_ons_notification_topic" "bastion_notification" {
  compartment_id = var.compartment_id
  name           = var.prefix == "none" ? var.notification_topic : "${var.prefix}-${var.notification_topic}"

  count = var.enable_notification ? 1 : 0

  freeform_tags = var.freeform_tags
}

resource "oci_ons_subscription" "bastion_notification" {
  compartment_id = var.compartment_id
  endpoint       = var.notification_endpoint
  protocol       = var.notification_protocol
  topic_id       = oci_ons_notification_topic.bastion_notification[0].topic_id

  count = var.enable_notification ? 1 : 0

  freeform_tags  = var.freeform_tags
}

resource "oci_identity_dynamic_group" "bastion_notification" {
  compartment_id = var.tenancy_id
  depends_on     = [oci_core_instance.bastion]
  description    = "dynamic group to allow bastion to send notifications to ONS"
  matching_rule  = "ALL {instance.id = '${join(",", data.oci_core_instance.bastion.*.id)}'}"
  name           = var.prefix == "none" ? "bastion-notification" : "${var.prefix}-bastion-notification"

  count = var.enable_notification ? 1 : 0

  freeform_tags  = var.freeform_tags
}

resource "oci_identity_policy" "bastion_notification" {
  compartment_id = var.compartment_id
  depends_on     = [oci_core_instance.bastion]
  description    = "policy to allow bastion host to publish messages to ONS"
  name           = var.prefix == "none" ? "bastion-notification" : "${var.prefix}-bastion-notification"
  statements     = ["Allow dynamic-group ${oci_identity_dynamic_group.bastion_notification[0].name} to use ons-topic in compartment id ${var.compartment_id} where request.permission='ONS_TOPIC_PUBLISH'"]

  count = var.enable_notification ? 1 : 0

  freeform_tags  = var.freeform_tags
}
