#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script provisions a OKE Kubernetes cluster using terraform (https://www.terraform.io/) and runs the new
# integration test suite against that cluster.
#
#
# As of May 6, 2020, the tests are clean on Kubernetes 1.16 with the following JDK workarounds:
# 1. Maven must be run with OpenJDK 11.0.7, available here: https://github.com/AdoptOpenJDK/openjdk11-upstream-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_linux_11.0.7_10.tar.gz
#    This is because of a critical bug fix. Unfortunately, the Oracle JDK 11.0.7 release was based on an earlier build and doesn't have the fix.
# 2. The WebLogic Image Tool will not accept an OpenJDK JDK. Set WIT_JAVA_HOME to an Oracle JDK Java Home.
#    For example, "export WIT_JAVA_HOME=/usr/java/jdk-11.0.7" before running this script.
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
myvcn_id=`oci network vcn list --compartment-id $compartment_ocid  --display-name=${OKE_CLUSTER_NAME}_vcn | jq -r '.data[] | .id'`
declare -a vcnidarray
vcnidarray=(${myvcn_id// /})
myip=`oci lb load-balancer list --compartment-id $compartment_ocid |jq -r '.data[] | .id'`
mysubnets=`oci network subnet list --vcn-id=${vcnidarray[0]} --display-name=${OKE_CLUSTER_NAME}-LB-${1} --compartment-id $compartment_ocid | jq -r '.data[] | .id'`

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
k8s_version="1.15.11"
if [[ -z "${WORKSPACE}" ]]; then
  outdir="/scratch/${USER}/oketest"
  export WORKSPACE=${PWD}
else
  outdir="${WORKSPACE}/logdir/${BUILD_TAG}"
fi
test_filter="**/It*"
parallel_run="false"
threads="2"
wdt_download_url="https://github.com/oracle/weblogic-deploy-tooling/releases/latest"
wit_download_url="https://github.com/oracle/weblogic-image-tool/releases/latest"
maven_profile_name="integration-tests"

while getopts ":h:n:o:t:v:x:s:p:d:i:m:b:" opt; do
  case $opt in
    v) k8s_version="${OPTARG}"
    ;;
    n) terraform_script_dir_name="${OPTARG}"
    ;;
    s) oci_property_file="${OPTARG}"
    ;;
    b) availability_domain="${OPTARG}"
    ;;
    o) outdir="${OPTARG}"
    ;;
    t) test_filter="${OPTARG}"
    ;;
    x) threads="${OPTARG}"
    ;;
    p) parallel_run="${OPTARG}"
    ;;
    d) wdt_download_url="${OPTARG}"
    ;;
    i) wit_download_url="${OPTARG}"
    ;;
    m) maven_profile_name="${OPTARG}"
    ;;
    h) usage 0
    ;;
    d) echo "Ignoring -d=${OPTARG}"
    ;;
    i) echo "Ignoring -i=${OPTARG}"
    ;;
    *) usage 1
    ;;
  esac
done


echo "Using Kubernetes version: ${k8s_version}"


mkdir -m777 -p "${outdir}"
export RESULT_ROOT="${outdir}/wl_k8s_test_results"
if [ -d "${RESULT_ROOT}" ]; then
  rm -Rf "${RESULT_ROOT}/*"
else
  mkdir -m777 "${RESULT_ROOT}"
fi

echo "Results will be in ${RESULT_ROOT}"

export PV_ROOT="${outdir}/k8s-pvroot"
if [ -d "${PV_ROOT}" ]; then
  rm -Rf "${PV_ROOT}/*"
else
  mkdir -m777 "${PV_ROOT}"
fi

echo "Persistent volume files, if any, will be in ${PV_ROOT}"

echo 'Remove old cluster (if any)...'
#kind delete cluster --name ${kind_name} --kubeconfig "${RESULT_ROOT}/kubeconfig"


echo 'Create a OKE cluster'
mkdir -p "${WORKSPACE}/terraform"
cp -rf ${terraform_script_dir_name}/*.tf ${WORKSPACE}/terraform/.
cp -rf ${WORKSPACE}/kubernetes/samples/scripts/terraform/template.tfvars ${WORKSPACE}/terraform/.
mkdir -p ${WORKSPACE}/terraform/terraforminstall

sh ${WORKSPACE}/kubernetes/samples/scripts/terraform/oke.create.sh ${oci_property_file} ${WORKSPACE}/terraform

OKE_CLUSTER_NAME=$(prop 'okeclustername')

export KUBECONFIG=${WORKSPACE}/terraform/${OKE_CLUSTER_NAME}_kubeconfig
export PATH=${WORKSPACE}/terraform/terraforminstall:$PATH

echo "creating storage class to setup OFSS ..."

echo "getting MountTarget ID"
compartment_ocid=$(prop 'compartment.ocid')
MT_ID=`oci fs mount-target  list --compartment-id=$compartment_ocid  --display-name=${OKE_CLUSTER_NAME}-mt --availability-domain=${availability_domain} | jq -r '.data[] | .id'`
export MT_ID=$MT_ID
mt_privateip_id=`oci fs mount-target  list --compartment-id=$compartment_ocid  --display-name=${OKE_CLUSTER_NAME}-mt --availability-domain=${availability_domain} | jq -r '.data[] | ."private-ip-ids"[]'`

mt_private_ip=`oci network private-ip get --private-ip-id $mt_privateip_id | jq -r '.data | ."ip-address"'`
export NFS_SERVER=$mt_private_ip
echo "Using NFS Server ${NFS_SERVER}"
cat << EOF | kubectl apply -f -
kind: StorageClass
apiVersion: storage.k8s.io/v1beta1
metadata:
  name: oci-fss
provisioner: oracle.com/oci-fss
parameters:
  # Insert mount target from the FSS here
  mntTargetId: ${MT_ID}
EOF

echo 'Set up test running ENVVARs...'
NODE_IP=`kubectl get nodes -o wide| awk '{print $7}'| tail -n+3`


if [ -z "$NODE_IP" ]; then
	echo "retry get node ip ";
    sleep 15;
    NODE_IP=`kubectl get nodes -o wide| awk '{print $7}'| tail -n+3`
fi
export K8S_NODEPORT_HOST=$NODE_IP
export JAVA_HOME="${JAVA_HOME:-`type -p java|xargs readlink -f|xargs dirname|xargs dirname`}"

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

cd ${WORKSPACE}

echo 'Run tests...'
if [ "${maven_profile_name}" = "oke-cert" ]; then
echo "Running mvn -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -pl integration-tests -P ${maven_profile_name} verify"
mvn -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/oke.log"
elseif [ "${parallel_run}" = "false" ]; then
  echo "Running mvn -Dit.test=${test_filter} -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -pl integration-tests -P integration-tests verify"
  mvn -Dit.test="${test_filter}, !ItExternalRmiTunneling, !ItSamples, !ItMiiSample, !ItTwoDomainsLoadBalancers, !ItMonitoringExporter, !ItPodRestart" -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/oke.log"
  else
    echo "Running mvn -Dit.test=${test_filter}, !ItOperatorUpgrade, !ItDedicatedMode -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -DPARALLEL_CLASSES=${parallel_run} -DNUMBER_OF_THREADS=${threads}  -pl integration-tests -P ${MVN_PROFILE} verify"
    #mvn -Dit.test="${IT_TEST}" -Dwdt.download.url="${WDT_DOWNLOAD_URL}" -Dwit.download.url="${WIT_DOWNLOAD_URL}" -DPARALLEL_CLASSES="${PARALLEL_RUN}" -DNUMBER_OF_THREADS="2" -pl integration-tests -P ${MVN_PROFILE} verify 2>&1 | tee "${RESULT_ROOT}/oke.log"
     mvn -Dit.test="${test_filter}, !ItTwoDomainsLoadBalancers, !ItExternalRmiTunneling, !ItMiiSample, !ItSamples, !ItPodRestart" -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -DPARALLEL_CLASSES="${parallel_run}" -DNUMBER_OF_THREADS="2" -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/oke.log"
  fi
fi

echo 'Deleting cluster'

cleanupLB Subnet01
cleanupLB Subnet02
cd ${WORKSPACE}/terraform
terraform init -var-file=${WORKSPACE}/terraform/${OKE_CLUSTER_NAME}.tfvars
terraform plan -var-file=${WORKSPACE}/terraform/${OKE_CLUSTER_NAME}.tfvars
terraform destroy -auto-approve -var-file=${WORKSPACE}/terraform/${OKE_CLUSTER_NAME}.tfvars

