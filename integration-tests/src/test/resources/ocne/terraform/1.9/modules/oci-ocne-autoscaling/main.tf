# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

resource "oci_load_balancer_load_balancer" "lb" {
  count          = var.create_load_balancer ? 1 : 0
  compartment_id = var.compartment_id
  display_name   = "${var.prefix}-load-balancer"
  shape          = lookup(var.load_balancer_shape, "shape")
  subnet_ids     = [var.subnet_id]
  is_private     = "true"

  # Optional
  shape_details {
    minimum_bandwidth_in_mbps = lookup(var.load_balancer_shape, "flex_min")
    maximum_bandwidth_in_mbps = lookup(var.load_balancer_shape, "flex_max")
  }
  freeform_tags = var.freeform_tags
}

resource "oci_load_balancer_backend_set" "lb_backend" {
  health_checker {
    protocol = var.load_balancer_protocol
    port     = var.load_balancer_port
    url_path = "/sys/health"
  }

  load_balancer_id = local.lb_ocid
  name             = "${var.prefix}-lb-backend"
  policy           = var.load_balancer_policy
}

resource "oci_core_instance_configuration" "instance_config" {
  compartment_id = var.compartment_id
  display_name   = "${var.prefix}-instance-configuration"
  freeform_tags  = var.freeform_tags

  instance_details {
    instance_type = "compute"

    launch_details {
      availability_domain = var.availability_domain_id
      compartment_id      = var.compartment_id
      display_name        = "${var.prefix}-compute"
      shape               = lookup(var.instance_shape, "shape", "VM.Standard2.2")

      dynamic "shape_config" {
        for_each = length(regexall("Flex", lookup(var.instance_shape, "shape", "VM.Standard.E3.Flex"))) > 0 ? [1] : []
        content {
          ocpus         = max(1, lookup(var.instance_shape, "ocpus", 1))
          memory_in_gbs = (lookup(var.instance_shape, "memory", 4) / lookup(var.instance_shape, "ocpus", 1)) > 64 ? (lookup(var.instance_shape, "ocpus", 1) * 16) : lookup(var.instance_shape, "memory", 4)
        }
      }

      source_details {
        source_type             = "image"
        image_id                = var.image_ocid
        boot_volume_size_in_gbs = lookup(var.instance_shape, "boot_volume_size", null)
      }

      metadata = {
        ssh_authorized_keys = file(var.ssh_public_key_path)
        user_data           = var.instance_user_data
      }

      create_vnic_details {
        assign_public_ip = "false"
        subnet_id        = var.subnet_id
      }
    }
  }
}

resource "oci_core_instance_pool" "instance_pool" {
  depends_on                = [oci_load_balancer_backend_set.lb_backend]
  instance_configuration_id = oci_core_instance_configuration.instance_config.id
  compartment_id            = var.compartment_id
  display_name              = "${var.prefix}-instance-pool"
  size                      = var.pool_size
  freeform_tags             = var.freeform_tags

  placement_configurations {
    availability_domain = var.availability_domain_id
    primary_subnet_id   = var.subnet_id
  }

  load_balancers {
    backend_set_name = "${var.prefix}-lb-backend"
    load_balancer_id = local.lb_ocid
    port             = var.load_balancer_port
    vnic_selection   = "PrimaryVnic"
  }
}

resource "oci_load_balancer_listener" "listener" {
  depends_on               = [oci_core_instance_pool.instance_pool, oci_load_balancer_backend_set.lb_backend]
  default_backend_set_name = oci_core_instance_pool.instance_pool.load_balancers[0].backend_set_name
  load_balancer_id         = local.lb_ocid
  name                     = "${var.prefix}-listener"
  port                     = var.load_balancer_port
  protocol                 = var.load_balancer_protocol
}
