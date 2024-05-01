# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Template to generate TF variables file for cluster creation from property file oci.props
# 
# User-specific vars - you can get these easily from the OCI console from your user page
#

# OCID can be obtained from the user info page in the OCI console
user_ocid="ocid1.user.oc1..aaaaaaaaayn6bm2i2blmrfkxeji2rbp5xbano5ntsw7bpu6nojq7tcehm6pq"
# API key fingerprint and private key location, needed for API access -- you should have added a public API key through the OCI console first
fingerprint="f8:c7:47:f1:55:84:e1:3b:7f:ac:d0:70:a6:b3:a8:8a" 
private_key_path="/home/mkogan/.oci/oci_api_key.pem"

# Required tenancy vars
tenancy_ocid="ocid1.tenancy.oc1..aaaaaaaal2velefrvaq6ydsy5ugkmcn3mn4dvfei5ie2j7vc4vaanykgobhq"
compartment_ocid="ocid1.compartment.oc1..aaaaaaaao2a47fvvvczrne6qpheso3wccykalmkgmt3nzafuo5fs3dnr3kaq"
compartment_name="test"
sub_compartment_ocid="ocid1.compartment.oc1..aaaaaaaapezmdp2gyz57osorhmjug7e5rqqudnlipxpubpdgydfd2mckn72q"
region="us-phoenix-1"

#
# Cluster-specific vars
#

# VCN CIDR -- must be unique within the compartment in the tenancy
# - assuming 1:1 cluster:vcn 
# BE SURE TO SET BOTH VARS -- the first 2 octets for each variable have to match
vcn_cidr_prefix="10.196"
vcn_cidr="10.196.0.0/16"
vcn_ocid="ocid1.vcn.oc1.phx.amaaaaaadgpbgsyafuqsnkkfdeoncvucnrssewt4xlybr25saubdbyw3fidq"

# Subnet

pub_subnet_ocid="ocid1.subnet.oc1.phx.aaaaaaaay235gkzcszhasbraiy34e3g6kc4iyk47yzon3u5qjvhewaasq2za"
private_subnet_ocid="ocid1.subnet.oc1.phx.aaaaaaaah6tarxgsz2gcbaocbugpw5gjznbo7ylgtfb4yyonaezhbkm3a2xa"
# Cluster name and k8s version
cluster_kubernetes_version="v1.26.2"
cluster_name="armcluster"

# Node pool info
node_pool_kubernetes_version="v1.26.2"
node_pool_name="armcluster_workers"
node_pool_node_shape="VM.Standard.E3.Flex"
node_pool_node_image_name="ocid1.image.oc1.phx.aaaaaaaaaizmtmozeudeeuq7o5ir7dkl2bkxbbb3tgomshqbqn6jpomrsjza"
node_pool_quantity_per_subnet=1

# SSH public key, for SSH access to nodes in the cluster
node_pool_ssh_public_key="ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCkz1vH/jvFOclbiwKNiCI+6U6XZob+anZUYDFvIhmFAOqaOQVtD9epkrRXcZT5x+rszDjuhjZAltloENfxW7wD7Kh+sb/Kidcp+DOV4SjlvF85dQ/fiv8L+3UB6PtXI0QBwLT+y1Cw4Q9VBd9iwwN3RMsb7a7sILSYIxKHCtPMTLto+cojlSG0Pbqak8j2w2MAjwaoRiXQMRtD3qVd3TZxNmTZWq7ynyiM7NDFnZ4Zsgk1F/9rkP5N8jpqbmPpACr90DhylB/Z+X0lX6yHK6bXFq6xryPoBBj+zzG2baVq0u+ZcB+zxwnW6oJuKH6B1V5og9/Z/O/0aOLzHssYOqN9 opc@jenkins-100-105-4-17-f5634e86-a56d-4dbe-becf-644f7d5d3f4f"



