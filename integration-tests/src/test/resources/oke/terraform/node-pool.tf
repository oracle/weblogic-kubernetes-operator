resource "oci_containerengine_node_pool" "tfsample_node_pool" {
  #Required
  cluster_id         = oci_containerengine_cluster.tfsample_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.node_pool_kubernetes_version
  name               = var.node_pool_name
  node_shape         = var.node_pool_node_shape
  subnet_ids = [oci_core_subnet.oke-subnet-worker-1.id, oci_core_subnet.oke-subnet-worker-2.id]

  timeouts {
      create = "60m"
      delete = "2h"
  }

  node_eviction_node_pool_settings{
	is_force_delete_after_grace_duration = true
	eviction_grace_duration = "PT0M"
  }
    #node_config_details{
     #   placement_configs{
      #      availability_domain = data.oci_identity_availability_domains.ADs.availability_domains[0]["name"]
       #     subnet_id = oci_core_subnet.oke-subnet-worker-1.id
        #} 
        #placement_configs{
         #   availability_domain = data.oci_identity_availability_domains.ADs.availability_domains[1]["name"]
          #  subnet_id = oci_core_subnet.oke-subnet-worker-2.id
        #}
        #size = 2
    #}

    # Using image Oracle-Linux-7.x-<date>
    # Find image OCID for your region from https://docs.oracle.com/iaas/images/ 
    node_source_details {
         #image_id = "ocid1.image.oc1.phx.aaaaaaaagfkjmrd3s6wkzq6vuokfmcwyr74hcgn7yex3jvueme3767ajnonq"
         image_id = "ocid1.image.oc1.phx.aaaaaaaaiwr25nhuo5vfiobwlsmj6qrab6xhzzlqyl7cwmmzeq6mck3w57va"
         source_type = "image"
    }
 
    # Optional
    initial_node_labels {
        key = "name"
        value = "var.cluster_name"
    }    
    ssh_public_key      = var.node_pool_ssh_public_key
}

