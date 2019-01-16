# Example TF variables file for cluster creation
# 
# Clone this and upate it with your own info as needed
#

#
# User-specific vars - you can get these easily from the OCI console from your user page
#

# OCID can be obtained from the user info page in the OCI console
user_ocid="@USEROCID@"
# API key fingerprint and private key location, needed for API access -- you should have added a public API key through the OCI console first
fingerprint="@OCIAPIPUBKEYFINGERPRINT@" 
private_key_path="@OCIPRIVATEKEYPATH@"

# Required tenancy vars
tenancy_ocid="@TENANCYOCID@"
compartment_ocid="@COMPARTMENTOCID@"
compartment_name="@COMPARTMENTNAME@"

#
# Cluster-specific vars
#

# VCN CIDR -- must be unique within the compartment in the tenancy
# - assuming 1:1 cluster:vcn 
# - this can be obtained either through OCI console, or from the Otto clusters page https://confluence.oraclecorp.com/confluence/display/ODX/Otto+OKE+Clusters
#
# BE SURE TO SET BOTH VARS -- the first 2 octets for each variable have to match
vcn_cidr_prefix="@VCNCIDRPREFIX@"
vcn_cidr="@VCNCIDR@"

# Cluster name and k8s version
cluster_kubernetes_version="@OKEK8SVERSION@"
cluster_name="@OKECLUSTERNAME@"

# Node pool info
node_pool_kubernetes_version="@OKEK8SVERSION@"
node_pool_name="@OKECLUSTERNAME@_workers"
node_pool_node_shape="@NODEPOOLSHAPE@"
node_pool_quantity_per_subnet=1

# SSH public key, for SSH access to nodes in the cluster
node_pool_ssh_public_key="@NODEPOOLSSHPUBKEY@"



