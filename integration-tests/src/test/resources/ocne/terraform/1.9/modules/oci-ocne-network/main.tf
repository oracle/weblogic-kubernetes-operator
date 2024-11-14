# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

# Modules and Resources
module "vcn" {
  source  = "oracle-terraform-modules/vcn/oci"
  version = "3.5.4"
  count  = var.deploy_networking ? 1 : 0

  # Required
  compartment_id    = var.compartment_id

  # Optional
  vcn_name          = "${var.prefix}-vcn"
  vcn_dns_label     = var.vcn_dns_label
  create_internet_gateway = true
  create_nat_gateway = true
  create_service_gateway = false
  vcn_cidrs = [var.vnc_cidr_block]
  freeform_tags = var.freeform_tags
}

resource "oci_core_subnet" "tf_vcn_private_subnet" {
  count = var.deploy_networking ? 1 : 0

  # Required
  compartment_id = var.compartment_id
  vcn_id         = var.deploy_networking ? module.vcn[0].vcn_id : var.vcn_id
  cidr_block     = var.vnc_private_subnet_cidr_block

  # Optional
  route_table_id    = var.deploy_networking ? module.vcn[0].nat_route_id : var.nat_route_id
  dns_label         = var.private_dns_label
  dhcp_options_id   = oci_core_dhcp_options.tf_dhcp_options[0].id
  security_list_ids = [oci_core_security_list.tf_private_security_list[0].id]
  display_name      = "${var.prefix}-private-subnet"
  freeform_tags     = var.freeform_tags
}

resource "oci_core_security_list" "tf_private_security_list" {
  count = var.deploy_networking ? 1 : 0

  compartment_id = var.compartment_id
  vcn_id         = var.deploy_networking ? module.vcn[0].vcn_id : var.vcn_id
  display_name   = "${var.prefix}-sg-private-subnet"

  freeform_tags  = var.freeform_tags

  egress_security_rules {
    stateless        = false
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
  }

  ingress_security_rules {
    stateless   = false
    source      = var.vnc_cidr_block
    source_type = "CIDR_BLOCK"

    # Get protocol numbers from https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml TCP is 6
    protocol = "6"
    tcp_options {
      min = 1
      max = 65535
    }
  }

  ingress_security_rules {
    stateless   = false
    source      = var.vnc_cidr_block
    source_type = "CIDR_BLOCK"

    # Get protocol numbers from https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml UDP is 17
    protocol = "17"
    udp_options {
      min = 1
      max = 65535
    }
  }

  ingress_security_rules {
    stateless   = false
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    # Get protocol numbers from https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml ICMP is 1  
    protocol = "1"

    # For ICMP type and code see: https://www.iana.org/assignments/icmp-parameters/icmp-parameters.xhtml
    icmp_options {
      type = 3
      code = 4
    }
  }

  ingress_security_rules {
    stateless   = false
    source      = var.vnc_cidr_block
    source_type = "CIDR_BLOCK"
    # Get protocol numbers from https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml ICMP is 1  
    protocol = "1"

    # For ICMP type and code see: https://www.iana.org/assignments/icmp-parameters/icmp-parameters.xhtml
    icmp_options {
      type = 3
    }
  }
}

resource "oci_core_dhcp_options" "tf_dhcp_options" {
  count = var.deploy_networking ? 1 : 0

  # Required
  compartment_id = var.compartment_id
  vcn_id         = var.deploy_networking ? module.vcn[0].vcn_id : var.vcn_id

  #Options for type are either "DomainNameServer" or "SearchDomain"
  options {
    type        = "DomainNameServer"
    server_type = "VcnLocalPlusInternet"
  }

  # Optional
  display_name  = "ocne-dhcp-options"
  freeform_tags = var.freeform_tags
}

