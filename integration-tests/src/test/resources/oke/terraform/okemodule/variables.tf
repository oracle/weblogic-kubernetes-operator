# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

# OCI Provider parameters
variable "api_fingerprint" {
  default     = ""
  description = "Fingerprint of the API private key to use with OCI API."
  type        = string
}

variable "api_private_key_path" {
  default     = ""
  description = "The path to the OCI API private key."
  type        = string
}
variable "vcn_id" {
  default     = ""
  description = "The vnc id."
  type        = string
}

variable "home_region" {
  description = "The tenancy's home region. Use the short form in lower case e.g. phoenix."
  type        = string
}

variable "tenancy_id" {
  description = "The tenancy id of the OCI Cloud Account in which to create the resources."
  type        = string
}

variable "user_id" {
  description = "The id of the user that Terraform will use to create the resources."
  type        = string
  default     = ""
}

variable "control_plane_subnet_id" {
  description = "The id of the control_plane_subnet_id."
  type        = string
  default     = ""
}

variable "control_plane_is_public" {
  default     = true
  description = "Whether the Kubernetes control plane endpoint should be allocated a public IP address to enable access over public internet."
  type        = bool
}

variable "assign_public_ip_to_control_plane" {
  default     = true
  description = "Whether to assign a public IP address to the API endpoint for public access. Requires the control plane subnet to be public to assign a public IP address."
  type        = bool
}
# General OCI parameters
variable "compartment_id" {
  description = "The compartment id where to create all resources."
  type        = string
}

# ssh keys
variable "ssh_private_key_path" {
  default     = "none"
  description = "The path to ssh private key."
  type        = string
}

variable "ssh_public_key_path" {
  default     = "none"
  description = "The path to ssh public key."
  type        = string
}

# clusters

variable "clusters" {
  description = "A map of cidrs for vcns, pods and services for each region"
  type        = map(any)
  default = {
    c1 = { region = "sydney", vcn = "10.1.0.0/16", pods = "10.201.0.0/16", services = "10.101.0.0/16", enabled = true }
  }
}

variable "cluster_name" {
  default     = "c1"
  description = "The cluster name."
  type        = string
}

variable "cluster_id" {
  default     = null
  description = "An existing OKE cluster OCID when `create_cluster = false`."
  type        = string
}

variable "create_cluster" {
  default     = true
  description = "Whether to create the OKE cluster and dependent resources."
  type        = bool
}

variable "kubernetes_version" {
  default     = "v1.29.1"
  description = "The version of Kubernetes to use."
  type        = string
}

variable "cluster_type" {
  default     = "basic"
  description = "Whether to use basic or enhanced OKE clusters"
  type        = string

  validation {
    condition     = contains(["basic", "enhanced"], lower(var.cluster_type))
    error_message = "Accepted values are 'basic' or 'enhanced'."
  }
}

variable "mount_target_ocid" {
  default     = "public"
}

variable "availability_domain" {
  default     = "mFEn:PHX-AD-1"
}

variable "oke_control_plane" {
  default     = "public"
  description = "Whether to keep all OKE control planes public or private."
  type        = string

  validation {
    condition     = contains(["public", "private"], lower(var.oke_control_plane))
    error_message = "Accepted values are 'public' or 'private'."
  }
}

variable "preferred_cni" {
  default     = "flannel"
  description = "Whether to use flannel or NPN"
  type        = string

  validation {
    condition     = contains(["flannel", "npn"], lower(var.preferred_cni))
    error_message = "Accepted values are 'flannel' or 'npn'."
  }
}

# node pools
variable "timezone" {
  type        = string
  description = "Preferred timezone of computes"
  default     = "Australia/Sydney"
}

variable "worker_subnet_id" { type = string }
variable "pub_lb_id" { type = string }
variable "node_shape" { type = string }

variable "nodepools" {
  type        = any
  description = "Node pools for all clusters"
  default = {
    np1 = {
      shape            = "VM.Standard.E4.Flex",
      ocpus            = 2,
      memory           = 32,
      size             = 3,
      boot_volume_size = 150,
    }
  }
}

variable "output_detail" {
  default     = true
  description = "Whether to include detailed output in state."
  type        = bool
}

variable "cluster_ca_cert" {
  default     = null
  description = "Base64+PEM-encoded cluster CA certificate for unmanaged instance pools. Determined automatically when 'create_cluster' = true or 'cluster_id' is provided."
  type        = string
}

variable "assign_public_ip_to_control_plane" {
  type        = bool
  description = "Whether to assign a public IP to the Kubernetes API endpoint"
  default     = true
}


