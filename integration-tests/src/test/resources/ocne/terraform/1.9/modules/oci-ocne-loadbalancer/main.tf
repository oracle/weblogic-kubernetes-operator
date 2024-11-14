# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

resource "oci_load_balancer_load_balancer" "kube_apiserver_lb" {
  compartment_id = var.compartment_id
  display_name   = "${var.prefix}-lb"
  shape          = lookup(var.shape, "shape")
  subnet_ids     = [var.subnet_id]
  is_private     = "true"
  count          = var.instance_count

  # Optional
  dynamic "shape_details" {
    for_each = length(regexall("flexible", lookup(var.shape, "shape", "flexible"))) > 0 ? [1] : []
    content {
      minimum_bandwidth_in_mbps = lookup(var.shape, "flex_min")
      maximum_bandwidth_in_mbps = lookup(var.shape, "flex_max")
    }
  }
  freeform_tags = var.freeform_tags
}

resource "oci_load_balancer_backend_set" "lb_backend" {
  health_checker {
    protocol = var.protocol
    port     = var.port
    url_path = "/sys/health"
  }
  count            = var.instance_count
  load_balancer_id = oci_load_balancer_load_balancer.kube_apiserver_lb[0].id
  name             = "${var.prefix}-backend"
  policy           = var.policy
}

// Please see the comment on the backend_count variable to
// understand why this is done with count rather than for_each
// and why there's a stupid lookup into backends variables.
resource "oci_load_balancer_backend" "backends" {
  depends_on       = [oci_load_balancer_backend_set.lb_backend]
  count            = (var.instance_count > 0) ? var.backend_count : 0
  backendset_name  = "${var.prefix}-backend"
  ip_address       = keys(var.backends)[count.index]
  load_balancer_id = oci_load_balancer_load_balancer.kube_apiserver_lb[0].id
  port             = var.backends[keys(var.backends)[count.index]]
}

resource "oci_load_balancer_listener" "listener" {
  depends_on               = [oci_load_balancer_backend_set.lb_backend]
  default_backend_set_name = "${var.prefix}-backend"
  load_balancer_id         = oci_load_balancer_load_balancer.kube_apiserver_lb[0].id
  name                     = "${var.prefix}-listener"
  port                     = var.port
  protocol                 = var.protocol
  count                    = var.instance_count
}
