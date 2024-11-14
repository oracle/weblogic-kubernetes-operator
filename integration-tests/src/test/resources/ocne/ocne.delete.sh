#!/bin/bash
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script deletes provisioned OKE Kubernetes cluster using terraform (https://www.terraform.io/)
#

set -o errexit
set -o pipefail

prop() {
  grep "${1}" ${oci_property_file}| grep -v "#" | cut -d'=' -f2
}

deleteOCNECluster() {
  cd ${terraform_script_dir}
  terraform init -var-file=${terraform_script_dir}/terraform.tfvars
  terraform plan -var-file=${terraform_script_dir}/terraform.tfvars
  terraform destroy -auto-approve -var-file=${terraform_script_dir}/terraform.tfvars
}


#MAIN
oci_property_file=${1:-$PWD/oci.props}
terraform_script_dir=${2:-$PWD}

terraformDir=$(prop 'terraform.installdir')
export PATH=${terraformDir}:$PATH

echo 'Deleting cluster'
deleteOCNECluster || true
