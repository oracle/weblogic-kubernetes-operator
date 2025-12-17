# Copyright (c) 2024 Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

locals {

  all_ports = -1

  # Protocols
  # See https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
  all_protocols = "all"
  icmp_protocol = 1
  tcp_protocol  = 6
  udp_protocol  = 17

  anywhere          = "0.0.0.0/0"
  rule_type_nsg     = "NETWORK_SECURITY_GROUP"
  rule_type_cidr    = "CIDR_BLOCK"
  rule_type_service = "SERVICE_CIDR_BLOCK"

  bastion_ip = one(element([module.c1[*].bastion_public_ip], 0))

  operator_ip = one(element([module.c1[*].operator_private_ip], 0))
  operator_install_kubectl_from_repo = true
  #control_plane_subnet_id = "ocid1.subnet.oc1.phx.aaaaaaaay235gkzcszhasbraiy34e3g6kc4iyk47yzon3u5qjvhewaasq2za"  
  # TODO: check when is 15021 required for public
  public_lb_allowed_ports = [80, 443, 15021]

  # ports required to be opened for inter-cluster communication between for Istio
  service_mesh_ports = [15012, 15017, 15021, 15443]

  regions = {
    # Africa
    johannesburg = "af-johannesburg-1"

    # Asia
    chuncheon = "ap-chuncheon-1"
    hyderabad = "ap-hyderabad-1"
    mumbai    = "ap-mumbai-1"
    osaka     = "ap-osaka-1"
    seoul     = "ap-seoul-1"
    singapore = "ap-singapore-1"
    tokyo     = "ap-tokyo-1"

    # Europe
    amsterdam = "eu-amsterdam-1"
    frankfurt = "eu-frankfurt-1"
    london    = "uk-london-1"
    madrid    = "eu-madrid-1"
    marseille = "eu-marseille-1"
    milan     = "eu-milan-1"
    newport   = "uk-cardiff-1"
    paris     = "eu-paris-1"
    stockholm = "eu-stockholm-1"
    zurich    = "eu-zurich-1"

    # Middle East
    abudhabi  = "me-abudhabi-1"
    dubai     = "me-dubai-1"
    jeddah    = "me-jeddah-1"
    jerusalem = "il-jerusalem-1"

    # Oceania
    melbourne = "ap-melbourne-1"
    sydney    = "ap-sydney-1"


    # South America
    bogota     = "sa-bogota-1"
    santiago   = "sa-santiago-1"
    saupaulo   = "sa-saupaulo-1"
    valparaiso = "sa-valparaiso-1"
    vinhedo    = "sa-vinhedo-1"

    # North America
    ashburn   = "us-ashburn-1"
    chicago   = "us-chicago-1"
    monterrey = "mx-monterrey-1"
    montreal  = "ca-montreal-1"
    phoenix   = "us-phoenix-1"
    queretaro = "mx-queretaro-1"
    sanjose   = "us-sanjose-1"
    toronto   = "ca-toronto-1"

    # US Gov FedRamp
    us-gov-ashburn = "us-langley-1"
    us-gov-phoenix = "us-luke-1"

    # US Gov DISA L5
    us-dod-east  = "us-gov-ashburn-1"
    us-dod-north = "us-gov-chicago-1"
    us-dod-west  = "us-gov-phoenix-1"

    # UK Gov
    uk-gov-south = "uk-gov-london-1"
    uk-gov-west  = "uk-gov-cardiff-1"

    # Australia Gov
    au-gov-cbr = "ap-dcc-canberra-1"

  }

  worker_cloud_init = [
    {
      content      = <<-EOT
    runcmd:
    - 'echo "Kernel module configuration for Istio and worker node initialization"'
    - 'modprobe br_netfilter'
    - 'modprobe nf_nat'
    - 'modprobe xt_REDIRECT'
    - 'modprobe xt_owner'
    - 'modprobe iptable_nat'
    - 'modprobe iptable_mangle'
    - 'modprobe iptable_filter'
    - '/usr/libexec/oci-growfs -y'
    - 'timedatectl set-timezone Australia/Sydney'
    - 'curl --fail -H "Authorization: Bearer Oracle" -L0 http://169.254.169.254/opc/v2/instance/metadata/oke_init_script | base64 --decode >/var/run/oke-init.sh'
    - 'bash -x /var/run/oke-init.sh'
    EOT
      content_type = "text/cloud-config",
    }
  ]
}
