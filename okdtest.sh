#!/bin/bash
# Copyright (c) 2022, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
set -o errexit
set -o pipefail

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

usage() {
  echo "usage: ${script} [-v <version>] [-n <name>] [-o <directory>] [-t <tests>] [-p true|false] [-x <number_of_threads>] [-d <wdt_download_url>] [-i <wit_download_url>] [-l <wle_download_url>] [-m <maven_profile_name>] [-h]"
  echo "  -v Kubernetes version (optional) "
  echo "      (default: 1.21, supported values depend on the kind version. See kindversions.properties) "
  echo "  -o Output directory (optional) "
  echo "      (default: \${WORKSPACE}/logdir/\${BUILD_TAG}, if \${WORKSPACE} defined, else /scratch/\${USER}/kindtest) "
  echo "  -t Test filter (optional) "
  echo "      (default: **/It*) "
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
  echo "  -m Run integration-tests or wko-okd-wls-srg or wko-okd-wls-mrg or wko-okd-fmw-cert"
  echo "      (default: wko-okd-wls-srg, supported values: wko-okd-wls-mrg, wko-okd-fmw-cert) "
  echo "  -h Help"
  exit $1
}

k8s_version="1.21"

KUBERNETES_CLI=${KUBERNETES_CLI:-kubectl}

echo "checking nodes"
${KUBERNETES_CLI} get nodes -o wide

if [[ -z "${WORKSPACE}" ]]; then
  outdir="/home/opc/okdtest"
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

while getopts "v:n:o:t:x:p:d:i:l:m:h" opt; do
  case $opt in
    v) k8s_version="${OPTARG}"
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

if [ -d "${PV_ROOT}" ]; then
  rm -Rf "${PV_ROOT}/*"
fi

echo "Persistent volume files, if any, will be in ${PV_ROOT}"

echo "cleaning up k8s artifacts"
${KUBERNETES_CLI} get ns --no-headers | awk '$1 ~ /^ns-/{print $1}' | xargs ${KUBERNETES_CLI} delete ns || true
${KUBERNETES_CLI} get ns --no-headers | awk '/weblogic/{print $1}' | xargs ${KUBERNETES_CLI} delete ns || true
${KUBERNETES_CLI} get ns --no-headers | awk '/test-/{print $1}' | xargs ${KUBERNETES_CLI} delete ns || true
${KUBERNETES_CLI} delete pv domain1-weblogic-sample-pv --wait=false || true
${KUBERNETES_CLI} delete pv domain2-weblogic-sample-pv --wait=false || true
${KUBERNETES_CLI} delete pv pv-testalertmanager --wait=false || true
${KUBERNETES_CLI} delete pv pv-testgrafana --wait=false || true
${KUBERNETES_CLI} delete pv pv-testprometheus --wait=false || true

${KUBERNETES_CLI} delete pv pv-testalertmanagertest1 --wait=false || true
${KUBERNETES_CLI} delete pv pv-testgrafanatest1 --wait=false || true
${KUBERNETES_CLI} delete pv pv-testprometheustest1 --wait=false || true

${KUBERNETES_CLI} delete pv pv-testalertmanagertest2 --wait=false || true
${KUBERNETES_CLI} delete pv pv-testgrafanatest2 --wait=false || true
${KUBERNETES_CLI} delete pv pv-testprometheustest2 --wait=false || true

${KUBERNETES_CLI} delete pv pv-testalertmanagertest3 --wait=false || true
${KUBERNETES_CLI} delete pv pv-testgrafanatest3 --wait=false || true
${KUBERNETES_CLI} delete pv pv-testprometheustest3 --wait=false || true

${KUBERNETES_CLI} delete crd $(${KUBERNETES_CLI} get crd | grep weblogic) || true

${KUBERNETES_CLI} get ingressroutes -A --no-headers | awk '/tdlbs-/{print $2}' | xargs ${KUBERNETES_CLI} delete ingressroute || true
${KUBERNETES_CLI} get clusterroles --no-headers | awk '/ns-/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterroles || true
${KUBERNETES_CLI} get clusterroles --no-headers | awk '/appscode/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterroles || true
${KUBERNETES_CLI} get clusterroles --no-headers | awk '/nginx-/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterroles || true
${KUBERNETES_CLI} get clusterroles --no-headers | awk '/traefik-/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterroles || true

${KUBERNETES_CLI} get clusterrolebindings --no-headers | awk '/ns-/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterrolebindings || true
${KUBERNETES_CLI} get clusterrolebindings --no-headers | awk '/appscode/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterrolebindings || true
${KUBERNETES_CLI} get clusterrolebindings --no-headers | awk '/nginx-/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterrolebindings || true
${KUBERNETES_CLI} get clusterrolebindings --no-headers | awk '/traefik-/{print $1}' | xargs ${KUBERNETES_CLI} delete clusterrolebindings || true

sudo rm -rf ${PV_ROOT}/*

export OKD=true
echo "${WLSIMG_BUILDER:-docker} info"
${WLSIMG_BUILDER:-docker} info
${WLSIMG_BUILDER:-docker} ps

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

set +e
echo 'Run build...'
mvn clean install -Dskip.unit.tests=true

echo 'IT_TEST = ${IT_TEST}'
echo 'Run tests...'

if [ "${test_filter}" != "**/It*" ]; then
  mvn -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -Dit.test="${test_filter}" -DPARALLEL_CLASSES="${parallel_run}" -DNUMBER_OF_THREADS="${threads}" -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/okdtest.log"
else
  mvn -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -DPARALLEL_CLASSES="${parallel_run}" -DNUMBER_OF_THREADS="${threads}" -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/okdtest.log"
fi
