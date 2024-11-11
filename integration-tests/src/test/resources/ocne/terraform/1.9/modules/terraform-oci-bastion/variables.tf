# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

# provider identity parameters
variable "tenancy_id" {
  description = "tenancy id where to create the sources"
  type        = string
  default     = ""
}

# general oci parameters

variable "compartment_id" {
  description = "compartment id where to create all resources"
  type        = string
}

variable "prefix" {
  description = "a string that will be prepended to all resources"
  type        = string
  default     = "none"
}

# network parameters

variable "availability_domain" {
  description = "the AD to place the bastion host"
  default     = 1
  type        = number
}

variable "bastion_access" {
  description = "CIDR block in the form of a string to which ssh access to the bastion must be restricted to. *_ANYWHERE_* is equivalent to 0.0.0.0/0 and allows ssh access from anywhere."
  default     = "ANYWHERE"
  type        = string
}

variable "ig_route_id" {
  description = "the route id to the internet gateway"
  type        = string
}

variable "netnum" {
  description = "0-based index of the bastion subnet when the VCN's CIDR is masked with the corresponding newbit value."
  default     = 32
  type        = number
}

variable "newbits" {
  description = "The difference between the VCN's netmask and the desired bastion subnet mask"
  default     = 13
  type        = number
}

variable "vcn_id" {
  description = "The id of the VCN to use when creating the bastion resources."
  type        = string
}

# bastion host parameters

variable "bastion_image_id" {
  description = "Provide a custom image id for the bastion host or leave as Autonomous."
  default     = "Autonomous"
  type        = string
}

variable "bastion_shape" {
  description = "The shape of bastion instance."
  type = map(any)
}

variable "bastion_upgrade" {
  description = "Whether to upgrade the bastion host packages after provisioning. It's useful to set this to false during development/testing so the bastion is provisioned faster."
  default     = false
  type        = bool
}

variable "ssh_public_key" {
  description = "the content of the ssh public key used to access the bastion. set this or the ssh_public_key_path"
  default     = ""
  type        = string
}

variable "ssh_public_key_path" {
  description = "path to the ssh public key used to access the bastion. set this or the ssh_public_key"
  default     = ""
  type        = string
}

variable "timezone" {
  description = "The preferred timezone for the bastion host."
  default     = "Australia/Sydney"
  type        = string
}

# bastion notification

variable "enable_notification" {
  description = "Whether to enable ONS notification for the bastion host."
  type        = bool
}

variable "notification_endpoint" {
  description = "The subscription notification endpoint. Email address to be notified."
  default     = null
  type        = string
}

variable "notification_protocol" {
  description = "The notification protocol used."
  default     = "EMAIL"
  type        = string
}

variable "notification_topic" {
  description = "The name of the notification topic"
  default     = "bastion"
  type        = string
}

# tagging
variable "freeform_tags" {
  description   = "Freeform tags with useful miscellaneous information."
  type          = map(any)
  default       = {}
}
