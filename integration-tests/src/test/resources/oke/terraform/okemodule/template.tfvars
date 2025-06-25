# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Template to generate TF variables file for cluster creation from property file oci.props
# 
# User-specific vars - you can get these easily from the OCI console from your user page
#

# provider
 api_fingerprint = "@OCIAPIPUBKEYFINGERPRINT@"
#
 api_private_key_path = "@OCIPRIVATEKEYPATH@"
#
 home_region = "phoenix" # Use short form e.g. ashburn from location column https://docs.oracle.com/en-us/iaas/Content/General/Concepts/regions.htm
#
 tenancy_id = "@TENANCYOCID@"
#
 user_id = "@USEROCID@"
#
 cluster_name="@OKECLUSTERNAME@"
 compartment_id = "@COMPOCID@"
 vcn_id = "@VCNOCID@"
 control_plane_subnet_id = "@PUBSUBNETOCID@"
 pub_lb_id = "@PUBSUBNETOCID@"
 worker_subnet_id = "@PRIVATESUBNETOCID@"

#MountTarget
mount_target_ocid="@MOUNTTARGETOCID@"
#
# # ssh
 ssh_private_key_path = "@NODEPOOLSSHPK@"
 ssh_public_key_path  = "@NODEPOOLSSHPUBKEY@"
#
# # clusters
# ## For regions, # Use short form e.g. ashburn from location column https://docs.oracle.com/en-us/iaas/Content/General/Concepts/regions.htm
# ## VCN, Pods and services clusters must not overlap with each other and with those of other clusters.
 clusters = {
   c1 = { region = "@REGIONSHORT@", vcn = "10.1.0.0/16", pods = "10.201.0.0/16", services = "10.101.0.0/16", enabled = true }
     }
#
     kubernetes_version = "@OKEK8SVERSION@"
#
     cluster_type = "basic"
#
     oke_control_plane = "public"
     node_shape = "@NODEPOOLSHAPE@"
nodepools = {
  np1 = {
    shape            = "@NODEPOOLSHAPE@",
    ocpus            = 2,
    memory           = 64,
    size             = 2,
    boot_volume_size = 150,
  }
}

