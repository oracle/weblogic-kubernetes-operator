# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// Common OCI resource variables
variable "availability_domain_id" {
  type        = string
  description = "The ID of the availability domain inside the `region` in which to create this deployment"
}

variable "compartment_id" {
  type        = string
  description = "The OCID of the compartment in which to create this deployment"
}

variable "prefix" {
  type        = string
  description = "A unique prefix to attach to the name of all cloud resources that are created as a part of this deployment"
}

variable "instance_shape" {
  type        = map(any)
  description = "The OCI instance shape to use for all compute resources that are created as part of this deployment"
}

variable "image_ocid" {
  type        = string
  description = "The OCID of the OS image to use when creating all compute resources that are part of this deployment"
}

variable "subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this variable to set to the empty string, a network configuration will be generated automatically"
}

// Compute instance specific variables
variable "ssh_public_key_path" {
  type        = string
  description = "The SSH public key path to use when configuring access to any compute resources created as part of this deployment"
}
variable "instance_user_data" {
  type        = string
  description = "Raw cloud-init data that is to be used to instantiate new compute resources"
}

// Instance pool specific variables
variable "pool_size" {
  type        = number
  description = "The initial number of compute instances to spawn as part of this deployment"
}

// Load balancer specific variables
variable "load_balancer_shape" {
  type        = map(string)
  description = "The OCI shape of the load balancer to create to service this pool"
}

variable "load_balancer_policy" {
  type        = string
  description = "The traffic policy to apply to the load balancer backend that represents this pool"
  default     = "LEAST_CONNECTIONS"
}

variable "load_balancer_protocol" {
  type        = string
  description = "The protocol that the load balancer listener listens for"
  default     = "HTTP"
}

variable "load_balancer_port" {
  type        = string
  description = "The port your application runs on.  This port is used to create the load balancer listeners and to create the backends"
}

variable "load_balancer_ocid" {
  type        = string
  description = "Optional OCID specifying an existing load balancer.  If provided, no load balancer will be created and all other resources will be attached to this load balancer"
  default     = ""
}

variable "create_load_balancer" {
  type        = string
  description = "If true, automatically generate a load balancer.  If false, do not generate one and an OCID must be provided instead"
  default     = true
}

variable "load_balancer_ip" {
  type        = string
  description = "If a custom load balancer is provided, this must be set to the canonical IP address that the load balancer serves traffic from"
  default     = ""
}

variable "freeform_tags" {
  description   = "Freeform tags with useful miscellaneous information."
  type          = map(any)
  default       = {}
}
