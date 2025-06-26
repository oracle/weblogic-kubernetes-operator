# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# ============================================
# User-specific provider configuration
# ============================================

api_fingerprint       = "@OCIAPIPUBKEYFINGERPRINT@"
api_private_key_path  = "@OCIPRIVATEKEYPATH@"
home_region           = "phoenix"
tenancy_id            = "@TENANCYOCID@"
user_id               = "@USEROCID@"

# ============================================
# OKE Cluster configuration
# ============================================

cluster_name          = "@OKECLUSTERNAME@"
compartment_id        = "@COMPOCID@"
vcn_id                = "@VCNOCID@"
control_plane_subnet_id = "@PUBSUBNETOCID@"
pub_lb_id             = "@PUBSUBNETOCID@"
worker_subnet_id      = "@PRIVATESUBNETOCID@"
mount_target_ocid     = "@MOUNTTARGETOCID@"

# SSH keys for node access
ssh_private_key_path  = "@NODEPOOLSSHPK@"
ssh_public_key_path   = "@NODEPOOLSSHPUBKEY@"

# ============================================
# Cluster network and CIDRs
# ============================================

clusters = {
  c1 = {
    region   = "@REGIONSHORT@"
    vcn      = "10.1.0.0/16"
    pods     = "10.201.0.0/16"
    services = "10.101.0.0/16"
    enabled  = true
  }
}

# ============================================
# General cluster parameters
# ============================================

kubernetes_version                 = "@OKEK8SVERSION@"
cluster_type                       = "basic"
oke_control_plane                  = "public"
assign_public_ip_to_control_plane = true
node_shape                         = "@NODEPOOLSHAPE@"

# ============================================
# Node pool definition
# ============================================

nodepools = {
  np1 = {
    shape            = "@NODEPOOLSHAPE@"
    ocpus            = 2
    memory           = 64
    size             = 2
    boot_volume_size = 150
  }
}
