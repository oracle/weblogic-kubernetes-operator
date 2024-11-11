# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// Compute instance specific variables
variable "ssh_public_key_path" {
  type        = string
  description = "The SSH public key path to use when configuring access to any compute resources created as part of this deployment"
}

variable "ssh_private_key_path" {
  type        = string
  description = "THe SSH private key path that goes with the SSH public key that is used when accessing compute resources that are created as part of this deployment"
}

variable "proxy" {
  type        = string
  description = "A proxy server to use for https and http communication when downloading or otherwise fetching data from external networks"
}

variable "no_proxy" {
  type        = string
  description = "No proxy setting, if used"
  default     = ""
}

variable "control_plane_node_count" {
  type        = number
  description = "The number of Kubernetes control plane nodes to deploy"
}

variable "control_plane_nodes" {
  description = "A list of IP address of Kubernetes control plane nodes"
}

variable "worker_node_count" {
  type        = number
  description = "The number of Kubernetes worker nodes to deploy"
}

variable "worker_nodes" {
  description = "A list of IP address of Kubernetes worker nodes"
}

variable "apiserver_ip" {
  type        = string
  description = "IP address of the OCNE API server node"
}

variable "compute_user" {
  type        = string
  description = "A user configured with sudo access and authenticated with ssh_public_key_path and ssh_private_key_path on each compute resource"
  default     = "opc"
}

variable "ocne_version" {
  type        = string
  description = "The version and release of OCNE to deploy"
}

variable "standalone_api_server" {
  type        = bool
  description = "If true, a dedicated compute instance is allocated for the OCNE API Server.  Otherwise, it will be deployed onto one of the Kubernetes control plane nodes"
  default     = true
}

variable "environment_name" {
  type        = string
  description = "This module allocates Hashicorp Vault certificates for most OCNE components, including olcnectl.  For olcnectl to acquire key material from a Vault instance, an environment is required to attach them to.  For convenience, it is possible to provide the name of an environment that this OCNE deployment intends to use"
}

variable "kubernetes_name" {
  type        = string
  description = "The name of the instance of the OCNE Kubernetes module to deploy into the given environment"
}

variable "kube_apiserver_ip" {
  type        = string
  description = "The IP of Kubernetes API server where Kubernetes clients should connect to when communicating with the Kubernetes cluster"
}

variable "kube_apiserver_port" {
  type        = number
  description = "The port that the Kubernetes API server should use to serve traffic, as well as any configured load balancers"
  default     = "6443"
}

variable "virtual_ip" {
  type        = bool
  description = "Setup Kubernetes API server endpoint on a virtual IP address representing all the Kubernetes control plane nodes"
  default     = false
}

variable "container_registry" {
  type        = string
  description = "The base URI of the container registry where all Kubernetes container images are located"
  default     = "container-registry.oracle.com/olcne"
}

variable "pod_network_iface" {
  type        = string
  description = "A regular expression that selects the appropriate network interfaces to use for pod networking.  Can also match IP addresses rather than interface names.  If set to the empty string, pod networking will use all available interfaces."
  default     = ""
}

variable "os_version" {
  type        = string
  description = "The version of Oracle Linux to use as the base image for all compute resources that are part of this deployemnt"
}

variable "node_labels_prefix" {
  type        = string
  description = "Label for Kubernetes nodes to set the availability domain for pods. The label should be in the format: failure-domain.beta.kubernetes.io/zone=region-identifier-AD-availability-domain-number "
  default     = "failure-domain.beta.kubernetes.io/zone"
}

variable "node_ocids" {
  description = "Comma separated list of Kubernetes nodes (both control plane and worker nodes) with their Oracle Cloud Identifiers (OCIDs). The format for the list is: FQDN=OCID,..."
  default     = ""
}

// Vault variables
variable "use_vault" {
  type        = bool
  description = "Decides if Vault is used to requisition certificates for OCNE daemons.  If true, then certificates are allocated using a Vault instance.  Otherwise, this module will generate certificates and distribute them to each node"
  default     = false
}

variable "vault_uri" {
  type        = string
  description = "The URI of a Hashicorp Vault instance that OCNE Agents and OCNE API Servers can use to requisition key material.  The authentication token provided by way of the certificate_signing_token argument must be able to authenticate with the server at this URI"
}

variable "certificate_signing_token" {
  type        = string
  description = "The Hashicorp Vault token that OCNE Agents and OCNE API Servers should use to authenticate with Vault"
}

variable "enable_bastion" {
  type        = bool
  description = "Decides if bastion is installed.  Intended for internal use.  Set to false."
}

variable "bastion_public_ip" {
  type        = string
  description = "Public IP address of the Bastion host"
}

variable "bastion_user" {
  type        = string
  description = "User name on the Bastion host"
  default     = "opc"
}

variable "bastion_private_key_path" {
  type        = string
  description = "The location of the SSH private key for the Bastion host"
}

variable "restrict_service_externalip_cidrs" {
  type        = string
  description = "A set of CIDR blocks to allow for the externalIp field in Kubernetes Services"
  default     = ""
}

variable "debug" {
  type        = bool
  description = "Enable provision debug logging"
  default     = false
}

variable "config_file_path" {
  type        = string
  description = "The path to the OCNE configuration file"
  default     = ""
}

variable "oci_api_key_path" {
  type        = string
  description = "The path to the API key used by the OCI user to authenticate with OCI API's. OCI plugin for the HashiCorp Vault allows only the 4096 key size for the OCI API signing key (RSA key pair in PEM format). So please use the 4096 key size when creating the RSA key pair. For details on how to create and configure keys see How to Generate an API Signing Key (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#two) and How to Upload the Public Key (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#three)"
  default     = ""
}

variable "kubevirt_config" {
  type        = string
  description = "The path to A multi-part yaml document containing any Kubernetes resources supporting an installation of the KubeVirt operator."
  default     = ""
}
