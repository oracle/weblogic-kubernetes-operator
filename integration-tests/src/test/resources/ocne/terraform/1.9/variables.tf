# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

// OCI Identity Variables
variable "user_id" {
  type        = string
  description = "The OCID of the user that will be used by terraform to create OCI resources. To get the value, see Where to Get the Tenancy's OCID and User's OCID."
}

variable "region" {
  type        = string
  description = "The OCI region where resources will be created. To get the value, See Regions and Availability Domains - https://docs.oracle.com/en-us/iaas/Content/General/Concepts/regions.htm#top"
  default     = ""
}

variable "tenancy_id" {
  type        = string
  description = "The OCID of your tenancy. To get the value, see Where to Get the Tenancy's OCID and User's OCID - https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#five."
}

variable "api_private_key_path" {
  type        = string
  description = "The path to the private key used by the OCI user to authenticate with OCI API's. OCI plugin for the HashiCorp Vault allows only the 4096 key size for the OCI API signing key (RSA key pair in PEM format). So please use the 4096 key size when creating the RSA key pair. For details on how to create and configure keys see How to Generate an API Signing Key (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#two) and How to Upload the Public Key (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#three)"
  default     = ""
}

variable "private_key_password" {
  type        = string
  description = "The password used to decrypt the OCI user's private key. (Optional) Passphrase used for the api_private_key, if it is encrypted."
  default     = ""
}

variable "fingerprint" {
  type        = string
  description = "Fingerprint for the key pair being used. To get the value, see How to Get the Key's Fingerprint. https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#four"
}


// Common OCI resource variables
variable "availability_domain_id" {
  type        = string
  description = "The ID of the availability domain inside the `region` in which to create this deployment"
}

variable "compartment_id" {
  type        = string
  description = "The OCID of the compartment."
}

variable "prefix" {
  type        = string
  description = "A unique prefix to attach to the name of all cloud resources that are created as a part of this deployment"
}

variable "instance_shape" {
  type        = map(any)
  description = "The OCI instance shape to use for all compute resources that are created as part of this deployment"
  default = {
    shape = "VM.Standard.E4.Flex", ocpus = 2, memory = 64
  }
}

variable "image_ocid" {
  type        = string
  description = "The OCID of the OS image to use when creating all compute resources that are part of this deployment"
  default     = ""
}

variable "os_version" {
  type        = string
  description = "The version of Oracle Linux to use as the base image for all compute resources that are part of this deployemnt"
  default     = "8"
}

variable "kernel_version" {
  type        = string
  description = "Kernel version to be installed"
  default     = "ol8_UEKR6"
}

variable "compute_user" {
  type        = string
  description = "A user configured with sudo access and authenticated with ssh_public_key and ssh_private_key on each compute resource"
  default     = "opc"
}

variable "node_ocids" {
  description = "Comma separated list of Kubernetes nodes (both control plane and worker nodes) with their Oracle Cloud Identifiers (OCIDs). The format for the list is: FQDN=OCID,..."
  default     = ""
}

// Compute instance specific variables
variable "ssh_public_key_path" {
  type        = string
  description = "The SSH public key path to use when configuring access to any compute resources created as part of this deployment"
}

variable "ssh_private_key_path" {
  type        = string
  description = "The SSH private key path that goes with the SSH public key that is used when accessing compute resources that are created as part of this deployment"
}

// Instance pool specific variables
variable "vault_pool_size" {
  type        = number
  description = "The initial number of Vault instances to spawn as part of this deployment"
  default     = "1"
}

variable "load_balancer_shape" {
  type        = map(string)
  description = "The OCI load balancer shape to use when creating load balancers for this deployment"
  default = {
    shape    = "flexible"
    flex_min = "10"
    flex_max = "50"
  }
}

variable "load_balancer_policy" {
  type        = string
  description = "The traffic policy to apply to any load balancers that are created as part of this deployment"
  default     = "LEAST_CONNECTIONS"
}

variable "vault_namespace" {
  type        = string
  description = "OCI Object storage bucket namespace string used to create the OCI Object Storage Bucket to support the [HashiCorp Vault OCI Object Storage Backend](https://www.vaultproject.io/docs/configuration/storage/oci-object-storage). To get the namespace string see [Understanding Object Storage Namespaces](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/understandingnamespaces.htm]."
  default     = ""
}

variable "vault_version" {
  type        = string
  description = "The version of Vault to deploy"
  default     = "1.3.4"
}

variable "proxy" {
  type        = string
  description = "A proxy server to use for https and http communication when downloading or otherwise fetching data from external networks"
  default     = ""
}

variable "no_proxy" {
  type        = string
  description = "No proxy setting, if used"
  default     = ""
}

variable "ocne_version" {
  type        = string
  description = "The version and release of OCNE to deploy. For more details on the versions, please see the [OCNE Release Notes](https://docs.oracle.com/en/operating-systems/olcne/1.7/relnotes/components.html#components). The default value '1.7' is to install the latest patch version of 1.7. To install a specific patch version, please set the value to `<major.minor.patch>` or `<major.minor.patch>-<release>`."
  default     = "1.7"
}

variable "yum_repo_url" {
  type        = string
  description = "The URI of the yum repository that hosts all OCNE packages"
  default     = "http://yum.oracle.com/repo/OracleLinux/OL8/olcne16/x86_64"
}

variable "control_plane_node_count" {
  type        = number
  description = "The number of Kubernetes control plane nodes to deploy"
  default     = 3
}

variable "worker_node_count" {
  type        = number
  description = "The number of Kubernetes worker nodes to deploy"
  default     = 3
}

variable "standalone_api_server" {
  type        = bool
  description = "If true, a dedicated compute instance is allocated for the OCNE API Server.  Otherwise, it will be deployed onto one of the Kubernetes control plane nodes"
  default     = true
}

variable "environment_name" {
  type        = string
  description = "The name of the OCNE Environment that is created by this module to deploy module instances into"
  default     = "myenvironment"
}

variable "kubernetes_name" {
  type        = string
  description = "The name of the instance of the OCNE Kubernetes module that is installed as part of this deployment"
  default     = "mycluster"
}

variable "kube_apiserver_port" {
  type        = string
  description = "The port to use for the Kubernetes API server that is created as part of this deployment"
  default     = "6443"
}

variable "container_registry" {
  type        = string
  description = "The container image registry that contains all container images that are consumed by this deployment"
  default     = "container-registry.oracle.com/olcne"
}

variable "extra_cas" {
  type        = list(any)
  description = "Any extra trusted certificates for compute resources"
  default     = []
}

// Networking variables
variable "vcn_id" {
  type        = string
  description = "The OCID of the OCI Virtual Cloud Network in which to create any subnets that might be generated as part of this deployment"
  default     = ""
}

variable "subnet_id" {
  type        = string
  description = "The OCID of a pre-existing subnet that all newly created cloud resources will be configured to use.  If this variable to set to the empty string, a network configuration will be generated automatically"
  default     = ""
}

variable "ig_route_id" {
  type    = string
  default = ""
}

variable "nat_route_id" {
  type    = string
  default = ""
}

variable "deploy_networking" {
  type        = bool
  description = "Decides if VCN is installed."
  default     = true
}

// Vault variables
variable "use_vault" {
  type        = bool
  description = "Decides if Vault is used to requisition certificates for OCNE daemons.  If true, then certificates are allocated using a Vault instance.  Otherwise, this module will generate certificates and distribute them to each node"
  default     = false
}

variable "vault_ocid" {
  type        = string
  description = "The OCID of the OCI KMS Vault to use with the Hashicorp Vault automatic unsealing feature"
  default     = ""
}

variable "key_ocid" {
  type        = string
  description = "The OCID of the OCI KMS Vault Key to use with the Hashicorp Vault automatic unsealing feature"
  default     = ""
}

variable "secret_name" {
  type        = string
  description = "The name of the vault secret"
  default     = "vault_keys"
}

variable "ocne_secret_name" {
  type        = string
  description = "The name of the ocne vault secret"
  default     = "ocne_keys"
}

variable "enable_bastion" {
  type        = bool
  description = "Decides if bastion is installed.  Intended for internal use.  Set to false."
  default     = true
}

variable "bastion_shape" {
  type        = map(any)
  description = "The shape of bastion instance."
  default = {
    shape = "VM.Standard.E3.Flex", ocpus = 1, memory = 4
  }
}

variable "bastion_public_ip" {
  type        = string
  description = "Public IP address of an existing Bastion host. This is set when we are not creating a bastion but need to use an existing one."
  default     = ""
}

variable "bastion_user" {
  type        = string
  description = "User name on the Bastion host"
  default     = "opc"
}

variable "bastion_private_key_path" {
  type        = string
  description = "The SSH private key path that goes with the SSH public key that is used when accessing the bastion host. Must be set if enable_bastion is set to true."
  default     = ""
}

variable "enable_notification" {
  description = "Whether to enable ONS notification for the bastion host."
  default     = false
  type        = bool
}

# The three tags, department, environment, and role, were moved
# from modules/terraform-oci-bastion/variables.tf
variable "freeform_tags" {
  description = "Freeform tags with useful miscellaneous information."
  type        = map(any)
  default     = {}
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

variable "provision_mode" {
  type        = string
  description = "Specifies the provision mode."
  default     = "OCNE"
}

variable "virtual_ip" {
  type        = bool
  description = "Setup Kubernetes API server endpoint on a virtual IP address representing all the Kubernetes control plane nodes"
  default     = false
}

variable "config_file_path" {
  type        = string
  description = "The path to the OCNE configuration file - https://docs.oracle.com/en/operating-systems/olcne/1.7/olcnectl/config.html"
  default     = ""
}
