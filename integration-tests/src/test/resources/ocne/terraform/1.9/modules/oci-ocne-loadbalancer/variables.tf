# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// Common OCI resource variables
variable "compartment_id" {
  type        = string
  description = "The OCID of the compartment in which to create this deployment"
}

variable "prefix" {
  type        = string
  description = "A unique prefix to attach to the name of all cloud resources that are created as a part of this deployment"
}

variable "subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this variable to set to the empty string, a network configuration will be generated automatically"
}

// Load balancer specific variables
variable "shape" {
  type        = map(string)
  description = "The OCI shape of the load balancer to create"
}

variable "policy" {
  type        = string
  description = "The traffic policy to apply to the load balancer backend set"
  default     = "LEAST_CONNECTIONS"
}

variable "protocol" {
  type        = string
  description = "The protocol that the listener create by this module listens on"
  default     = "HTTP"
}

variable "port" {
  type        = number
  description = "The port that the listener created by this module should serve traffic on"
}

variable "backends" {
  type        = map(any)
  description = "A mapping of IP address or hostname to ports.  These will become the backend set for the load balancer"
}

// While in most cases the length of the backend
// map can be calculated appropriately, there are
// cases where the backends are dynamically generated
// by some calling module where the plan stage would
// be unable determine the backends variable until
// part of the apply has finished.  Hence this
// seemingly pointless argument.
variable "backend_count" {
  type        = number
  description = "The number of keys in the `backends` variable"
}

variable "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  type        = map(any)
  default     = {}
}

variable "instance_count" {
  type        = number
  description = "The number of LB instances to create with the given configuration"
}
