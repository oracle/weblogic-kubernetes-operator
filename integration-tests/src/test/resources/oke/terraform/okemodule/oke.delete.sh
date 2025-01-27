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

cleanupLB() {
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
            echo "deleting lb with id $i   $lb"
            if [[ (-z "${lb##*$vcn_cidr_prefix*}") || (-z "${lb##*$k*}") ]] ;then
               echo "deleting lb with id $i"
               sleep 60
               oci lb load-balancer delete --load-balancer-id=$i --force || true
            fi
        done
    done
  myip=`oci lb load-balancer list --compartment-id $compartment_ocid |jq -r '.data[] | .id'`
  iparray=(${myip// /})
   for k in "${mysubnetsidarray[@]}"
      do
        for i in "${iparray[@]}"
           do
              lb=`oci lb load-balancer get --load-balancer-id=$i`
              echo "deleting lb with id $i   $lb"
              if [[ (-z "${lb##*$vcn_cidr_prefix*}") || (-z "${lb##*$k*}") ]] ;then
                 echo "deleting lb with id $i"
                 sleep 60
                 oci lb load-balancer delete --load-balancer-id=$i --force || true
              fi
          done
      done
}

deleteOKE() {
  cd ${terraform_script_dir}
  terraform init -var-file=${terraform_script_dir}/${clusterName}.tfvars > /dev/null
  terraform plan -var-file=${terraform_script_dir}/${clusterName}.tfvars > /dev/null
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
export TF_LOG=ERROR
export PATH=${terraform_script_dir}/terraforminstall:$PATH
echo 'Deleting cluster'

deleteOKE || true
