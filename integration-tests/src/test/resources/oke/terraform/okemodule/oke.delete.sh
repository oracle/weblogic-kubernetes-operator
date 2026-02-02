#!/bin/bash
# Copyright (c) 2024, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script deletes provisioned OKE Kubernetes cluster using terraform (https://www.terraform.io/)
#

set -o errexit
set -o pipefail

# OCI CLI must be explicit in Jenkins
export OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True

if [[ -z "${OCI_CLI_CONFIG_FILE}" ]]; then
  export OCI_CLI_CONFIG_FILE="${HOME}/.oci/config"
fi

if [[ -z "${OCI_CLI_PROFILE}" ]]; then
  export OCI_CLI_PROFILE="DEFAULT"
fi

echo "[DEBUG] Using OCI config: ${OCI_CLI_CONFIG_FILE}"
echo "[DEBUG] Using OCI profile: ${OCI_CLI_PROFILE}"

lb_query_for_cluster() {
  local cluster="$1"
  echo 'data[?"freeform-tags"."oke.cluster.name" == '"'"${cluster}"'"' ].{"id":id,"name":"display-name","state":"lifecycle-state"}'
}



prop() {
  grep "${1}" ${oci_property_file}| grep -v "#" | cut -d'=' -f2
}

debug() {
  echo "[DEBUG] $*"
}


debug "oke.delete.sh: okeclustername=${clusterName}"


cleanupLB() {
  echo "Cleaning Load Balancers for cluster ${clusterName}"

  oci lb load-balancer list \
    --compartment-id "$compartment_ocid" \
    --all \
    --query "data[?\"defined-tags\".\"Oracle-Tags\".\"CreatedBy\"=='${clusterName}'].id | []" \
    --raw-output | while read -r lb_id; do
      [[ -z "$lb_id" || "$lb_id" == "null" || "$lb_id" == "[]" ]] && continue
      echo "Deleting LB $lb_id"
      oci lb load-balancer delete \
        --load-balancer-id "$lb_id" \
        --force || true
  done
}

listClusterLBs() {
   oci lb load-balancer list \
    --compartment-id "$compartment_ocid" \
    --query "$(lb_query_for_cluster "$okeclustername")" \
    --all \
    --output table
}


verifyNoLeftoverLBs() {
  echo "Verifying no Load Balancers remain for cluster ${clusterName}"

  leftover=$(oci lb load-balancer list \
    --compartment-id "$compartment_ocid" \
    --all \
    --query "data[?\"defined-tags\".\"Oracle-Tags\".\"CreatedBy\"=='${clusterName}'].id | []" \
    --raw-output)

  # Treat [] / null / empty as "no leftovers"
  if [[ -z "$leftover" || "$leftover" == "null" || "$leftover" == "[]" ]]; then
    echo "No leftover Load Balancers found for cluster ${clusterName}"
    return 0
  fi

  echo "[ERROR] Leftover Load Balancers detected for cluster ${clusterName}:"
  echo "$leftover"
  exit 1
}


deleteOKE() {
	debug "deleteOKE(): destroying cluster ${clusterName}"

  cd ${terraform_script_dir}
  terraform init -var-file=${terraform_script_dir}/${clusterName}.tfvars > /dev/null
  terraform plan -var-file=${terraform_script_dir}/${clusterName}.tfvars > /dev/null
  debug "deleteOKE(): destroying cluster ${clusterName}"

  terraform destroy -auto-approve -var-file=${terraform_script_dir}/${clusterName}.tfvars > /dev/null
}


#MAIN
oci_property_file=${1:-$PWD/oci.props}
terraform_script_dir=${2:-$PWD}
availability_domain=${3:-mFEn:PHX-AD-1}
clusterName=$(prop 'okeclustername')
compartment_ocid=$(prop 'compartment.ocid')
vcn_cidr_prefix=$(prop 'vcn.cidr.prefix')
export KUBECONFIG=${terraform_script_dir}/${clusterName}_kubeconfig
export PATH=${terraform_script_dir}/terraforminstall:$PATH
export TF_LOG=ERROR
echo 'Deleting cluster'

deleteOKE || true
echo "=== Load Balancers BEFORE cleanup for ${clusterName} ==="
listClusterLBs || true
cleanupLB
sleep 30
verifyNoLeftoverLBs
