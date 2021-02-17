#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script provisions a OKE Kubernetes cluster using terraform (https://www.terraform.io/) and runs the new
# integration test suite against that cluster. Blog https://blogs.oracle.com/weblogicserver/easily-create-an-oci-container-engine-for-kubernetes-cluster-with-terraform-installer-to-run-weblogic-server
# provides detailed explanation for OCI properties file creation.
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
  echo "usage: ${script} [-n <terraform config files directory>] [-o <directory>] [-t <tests>] [-c <name>] [-p true|false] [-x <number_of_threads>] [-d <wdt_download_url>] [-i <wit_download_url>] [-l <wle_download_url>] [-m <maven_profile_name>] [-h]"
  echo "  -n Terraform config files directory "
  echo "  -o Output directory (optional) "
  echo "      (default: \${WORKSPACE}/logdir/\${BUILD_TAG}, if \${WORKSPACE} defined, else /scratch/\${USER}/kindtest) "
  echo "  -b  Availability Domain Name "
  echo "      (for example: VPGL:PHX-AD-1 , check limits quota with OCI admin)"
  echo "  -t Test filter (optional) "
  echo "      (default: **/It*) "
  echo "  -s Oracle Cloud Infra properties file  "
  echo "  -p Run It classes in parallel"
  echo "      (default: false) "
  echo "  -x Number of threads to run the classes in parallel"
  echo "      (default: 2) "
  echo "  -d WDT download URL"
  echo "      (default: https://github.com/oracle/weblogic-deploy-tooling/releases/latest) "
  echo "  -i WIT download URL"
  echo "      (default: https://github.com/oracle/weblogic-image-tool/releases/latest) "
  echo "  -l WLE download URL"
  echo "      (default: https://github.com/oracle/weblogic-logging-exporter/releases/latest) "
  echo "  -m Run integration-tests or oke-cert "
  echo "      (default: integration-tests, supported values: oke-cert) "
  echo "  -h Help"
  exit $1
}

function prop {
    grep "${1}" ${oci_property_file}| grep -v "#" | cut -d'=' -f2
}

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
wle_download_url="https://github.com/oracle/weblogic-logging-exporter/releases/latest"
maven_profile_name="integration-tests"

while getopts ":h:n:o:t:x:s:p:d:i:l:m:b:" opt; do
  case $opt in
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
    l) wle_download_url="${OPTARG}"
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

k8s_version=$(prop 'k8s.version')
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

echo 'Create a OKE cluster'
mkdir -p "${WORKSPACE}/terraform"
cp -rf ${terraform_script_dir_name}/*.tf ${WORKSPACE}/terraform/.
cp -rf ${WORKSPACE}/kubernetes/samples/scripts/terraform/template.tfvars ${WORKSPACE}/terraform/.
cp -rf ${WORKSPACE}/kubernetes/samples/scripts/terraform/*.sh ${WORKSPACE}/terraform/.
chmod 777 ${WORKSPACE}/terraform/*.sh
mkdir -p ${WORKSPACE}/terraform/terraforminstall

if ! sh ${WORKSPACE}/terraform/oke.create.sh ${oci_property_file} ${WORKSPACE}/terraform ; then
  sh ${WORKSPACE}/terraform/oke.delete.sh ${oci_property_file} ${WORKSPACE}/terraform
fi

clusterName=$(prop 'okeclustername')

export KUBECONFIG=${WORKSPACE}/terraform/${clusterName}_kubeconfig
export PATH=${WORKSPACE}/terraform/terraforminstall:$PATH

echo "creating storage class to setup OFSS ..."

echo "getting MountTarget ID"
compartment_ocid=$(prop 'compartment.ocid')
mount_target_id=`oci fs mount-target  list --compartment-id=$compartment_ocid  --display-name=${clusterName}-mt --availability-domain=${availability_domain} | jq -r '.data[] | .id'`
mt_privateip_id=`oci fs mount-target  list --compartment-id=$compartment_ocid  --display-name=${clusterName}-mt --availability-domain=${availability_domain} | jq -r '.data[] | ."private-ip-ids"[]'`
mt_private_ip=`oci network private-ip get --private-ip-id $mt_privateip_id | jq -r '.data | ."ip-address"'`

export NFS_SERVER=$mt_private_ip
echo "Using NFS Server ${NFS_SERVER}"
echo "Creating Storage Class to mount OFSS"
cat << EOF | kubectl apply -f -
kind: StorageClass
apiVersion: storage.k8s.io/v1beta1
metadata:
  name: oci-fss
provisioner: oracle.com/oci-fss
parameters:
  # Insert mount target from the FSS here
  mntTargetId: ${mount_target_id}
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
  echo "Running mvn -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -Dwle.download.url=${wle_download_url} -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee ${RESULT_ROOT}/oke.log"
  mvn -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/oke.log"
else
  echo "Running mvn -Dit.test=${test_filter}, !ItExternalRmiTunneling, !ItSamples, !ItMiiSample, !ItTwoDomainsLoadBalancers, !ItMonitoringExporter, !ItPodRestart -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -Dwle.download.url=${wle_download_url} -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P integration-tests verify 2>&1 | tee ${RESULT_ROOT}/oke.log"
  mvn -Dit.test="${test_filter}, !ItExternalRmiTunneling, !ItSamples, !ItMiiSample, !ItTwoDomainsLoadBalancers, !ItMonitoringExporter, !ItPodRestart" -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -Djdk.tls.client.protocols=TLSv1.2 -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/oke.log"
fi
