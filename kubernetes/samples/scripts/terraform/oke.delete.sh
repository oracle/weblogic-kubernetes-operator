#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script deletes provisioned OKE Kubernetes cluster using terraform (https://www.terraform.io/) 
#
#

set -o errexit
set -o pipefail

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} [-v <version>] [-n <name>] [-o <directory>] [-t <tests>] [-c <name>] [-p true|false] [-x <number_of_threads>] [-d <wdt_download_url>] [-i <wit_download_url>] [-m <maven_profile_name>] [-h]"
  echo "  -v Kubernetes version (optional) "
  echo "      (default: 1.15.11, supported values: 1.18, 1.18.2, 1.17, 1.17.5, 1.16, 1.16.9, 1.15, 1.15.11, 1.14, 1.14.10) "
  echo "  -n Kind cluster name (optional) "
  echo "      (default: kind) "
  echo "  -o Output directory (optional) "
  echo "      (default: \${WORKSPACE}/logdir/\${BUILD_TAG}, if \${WORKSPACE} defined, else /scratch/\${USER}/kindtest) "
  echo "  -t Test filter (optional) "
  echo "      (default: **/It*) "
  echo "  -c CNI implementation (optional) "
  echo "      (default: kindnet, supported values: kindnet, calico) "
  echo "  -p Run It classes in parallel"
  echo "      (default: false) "
  echo "  -x Number of threads to run the classes in parallel"
  echo "      (default: 2) "
  echo "  -d WDT download URL"
  echo "      (default: https://github.com/oracle/weblogic-deploy-tooling/releases/latest) "
  echo "  -i WIT download URL"
  echo "      (default: https://github.com/oracle/weblogic-image-tool/releases/latest) "
  echo "  -m Run integration-tests or wls-image-cert or fmw-image-cert"
  echo "      (default: integration-tests, supported values: wls-image-cert, fmw-image-cert) "
  echo "  -h Help"
  exit $1
}

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
export PATH={terraform_script_dir}/terraforminstall:$PATH

echo 'Deleting cluster'

#check and cleanup any left over running Load Balancers
cleanupLB Subnet01
cleanupLB Subnet02
cd ${terraform_script_dir}
terraform init -var-file=${terraform_script_dir}/${clusterName}.tfvars
terraform plan -var-file=${terraform_script_dir}/${clusterName}.tfvars
terraform destroy -auto-approve -var-file=${terraform_script_dir}/${clusterName}.tfvars