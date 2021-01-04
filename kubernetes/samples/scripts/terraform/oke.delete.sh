#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script deletes provisioned OKE Kubernetes cluster using terraform (https://www.terraform.io/)
#

set -o errexit
set -o pipefail

function prop {
  grep "${1}" ${oci_property_file}| grep -v "#" | cut -d'=' -f2
}

function cleanupLB {
  echo 'Clean up left over LB'
  myvcn_id=`oci network vcn list --compartment-id $compartment_ocid  --display-name=${clusterName}_vcn | jq -r '.data[] | .id'`
  declare -a vcnidarray
  vcnidarray=(${myvcn_id// /})
  myip=`oci lb load-balancer list --compartment-id $compartment_ocid |jq -r '.data[] | .id'`
  mysubnets=`oci network subnet list --vcn-id=${vcnidarray[0]} --display-name=${clusterName}-LB-${1} --compartment-id $compartment_ocid | jq -r '.data[] | .id'`

  declare -a iparray
  declare -a mysubnetsidarray
  mysubnetsidarray=(${mysubnets// /})

  iparray=(${myip// /})
  vcn_cidr_prefix=$(prop 'vcn.cidr.prefix')
  for k in "${mysubnetsidarray[@]}"
    do
      for i in "${iparray[@]}"
         do
            lb=`oci lb load-balancer get --load-balancer-id=$i`
            if [[ (-z "${lb##*$vcn_cidr_prefix*}") || (-z "${lb##*$k*}") ]] ;then
               echo "deleting lb with id $i"
               oci lb load-balancer delete --load-balancer-id=$i --force || true
            fi
        done
    done
}

function deleteOKE {
  cd ${terraform_script_dir}
  terraform init -var-file=${terraform_script_dir}/${clusterName}.tfvars
  terraform plan -var-file=${terraform_script_dir}/${clusterName}.tfvars
  terraform destroy -auto-approve -var-file=${terraform_script_dir}/${clusterName}.tfvars
}

#MAIN
oci_property_file=${1:-$PWD/oci.props}
terraform_script_dir=${2:-$PWD}
clusterName=$(prop 'okeclustername')
compartment_ocid=$(prop 'compartment.ocid')
vcn_cidr_prefix=$(prop 'vcn.cidr.prefix')
export KUBECONFIG=${terraform_script_dir}/${clusterName}_kubeconfig
export PATH=${terraform_script_dir}/terraforminstall:$PATH
echo 'Deleting cluster'
#check and cleanup any left over running Load Balancers
cleanupLB Subnet01
cleanupLB Subnet02
deleteOKE