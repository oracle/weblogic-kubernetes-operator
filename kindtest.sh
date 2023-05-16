#!/bin/bash
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script provisions a Kubernetes cluster using Kind (https://kind.sigs.k8s.io/) and runs the new
# integration test suite against that cluster.
#
# To install Kind:
#    curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.18.0/kind-$(uname)-amd64
#    chmod +x ./kind
#    mv ./kind /some-dir-in-your-PATH/kind
#
# Kind creates Kubernetes nodes on your local box by running each node as a Docker container. This
# makes it very easy to setup and tear down clusters. Presently, this script creates a cluster with a
# master node and a worker node. Each node will run the same Kubernetes version. Future enhancements to
# this script could allow for the configuration of a different number of nodes or for nodes to run a
# differing set of Kubernetes versions.
#
# Kind nodes do not have access to the Docker repository on the local machine. Therefore this script works
# around this limitation by running a Docker registry in a Docker container and connecting the networks so
# that the Kubernetes nodes have visibility to the registry. When you run tests, you will see that Docker
# images are pushed to this local registry and then are pulled to the Kubernetes nodes as needed.
# You can see the images on a node by running: "${WLSIMG_BUILDER:-docker} exec -it kind-worker crictl images" where kind-worker
# is the name of the Docker container.
#
# As of May 6, 2020, the tests are clean on Kubernetes 1.16 with the following JDK workarounds:
# 1. Maven must be run with OpenJDK 11.0.7, available here: https://github.com/AdoptOpenJDK/openjdk11-upstream-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_linux_11.0.7_10.tar.gz
#    This is because of a critical bug fix. Unfortunately, the Oracle JDK 11.0.7 release was based on an earlier build and doesn't have the fix.
# 2. The WebLogic Image Tool will not accept an OpenJDK JDK. Set WIT_JAVA_HOME to an Oracle JDK Java Home.
#    For example, "export WIT_JAVA_HOME=/usr/java/jdk-11.0.7" before running this script.
#
# If you want to access the cluster, use "kubectl cluster-info --context kind-kind" where the trailing "kind" is the value of the "-n" argument.
set -o errexit
set -o pipefail

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
KUBERNETES_CLI=${KUBERNETES_CLI:-kubectl}

usage() {
  echo "usage: ${script} [-v <version>] [-n <name>] [-s] [-o <directory>] [-t <tests>] [-c <name>] [-p true|false] [-x <number_of_threads>] [-d <wdt_download_url>] [-i <wit_download_url>] [-l <wle_download_url>] [-m <maven_profile_name>] [-h]"
  echo "  -v Kubernetes version (optional) "
  echo "      (default: 1.21, supported values depend on the kind version. See kindversions.properties) "
  echo "  -n Kind cluster name (optional) "
  echo "      (default: kind) "
  echo "  -s Skip tests. If this option is specified then the cluster is created, but no tests are run. "
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
  echo "  -l WLE download URL"
  echo "      (default: https://github.com/oracle/weblogic-logging-exporter/releases/latest) "
  echo "  -m Run integration-tests or the other maven_profile_name in the pom.xml"
  echo "      (default: integration-tests) "
  echo "  -h Help"
  exit $1
}

captureLogs() {
  echo "Capture Kind logs..."
  mkdir "${RESULT_ROOT}/kubelogs"
  kind export logs "${RESULT_ROOT}/kubelogs" --name "${kind_name}" --verbosity 99
}

k8s_version_string=$($KUBERNETES_CLI version --client -o json|jq -rj '.clientVersion|.gitVersion')
# Remove the first character from version string e.g. v1.24.0
k8s_version=${k8s_version_string#?}
#k8s_version="1.21"

kind_name="kind"
if [[ -z "${WORKSPACE}" ]]; then
  outdir="/scratch/${USER}/kindtest"
else
  outdir="${WORKSPACE}/logdir/${BUILD_TAG}"
fi
test_filter="**/It*"
cni_implementation="kindnet"
parallel_run="false"
threads="2"
wdt_download_url="https://github.com/oracle/weblogic-deploy-tooling/releases/latest"
wit_download_url="https://github.com/oracle/weblogic-image-tool/releases/latest"
wle_download_url="https://github.com/oracle/weblogic-logging-exporter/releases/latest"
maven_profile_name="integration-tests"
skip_tests=false

while getopts "v:n:o:t:c:x:p:d:i:l:m:sh" opt; do
  case $opt in
    v) k8s_version="${OPTARG}"
    ;;
    n) kind_name="${OPTARG}"
    ;;
    o) outdir="${OPTARG}"
    ;;
    t) test_filter="${OPTARG}"
    ;;
    c) cni_implementation="${OPTARG}"
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
    s) skip_tests=true
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

versionprop() {
  grep "${1}_${2}=" "${scriptDir}/kindversions.properties"|cut -d'=' -f2
}

kind_version=$(kind version)
#kind_series="0.18.0"
case "${kind_version}" in
  "kind v0.15."*)
    kind_series="0.15.0"
    ;;
  "kind v0.16."*)
    kind_series="0.16.0"
    ;;
  "kind v0.17."*)
    kind_series="0.17.0"
    ;;
  "kind v0.18."*)
    kind_series="0.18.0"
    ;;
  *)
    echo "Unsupported Kind Version [${kind_version}]"
    exit -1
esac

kind_image=$(versionprop "${kind_series}" "${k8s_version}")
echo "Kind Image String [${kind_kind_image}]"
if [ -z "${kind_image}" ]; then
  kv=$(kind version -q)
  echo "Unsupported Kubernetes/Kind Combination ${k8s_version}/${kv}"
  exit 1
fi

echo "Using KIND VERSION [${kind_version}]"
echo "Using KUBERNETES VERSION [${k8s_version}]"
echo "Using KIND IMAGE [${kind_image}]"

disableDefaultCNI="false"
if [ "${cni_implementation}" = "calico" ]; then
  disableDefaultCNI="true"
elif [ "${cni_implementation}" != "kindnet" ]; then
  echo "Unsupported CNI implementation: ${cni_implementation}"
  exit 1
fi

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
kind delete cluster --name ${kind_name} --kubeconfig "${RESULT_ROOT}/kubeconfig"

kind_network='kind'
reg_name='kind-registry'
reg_port='5000'

echo 'Create registry container unless it already exists'
running="$(${WLSIMG_BUILDER:-docker} inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
if [ "${running}" = 'false' ]; then
  ${WLSIMG_BUILDER:-docker} rm --force "${reg_name}"
fi
if [ "${running}" = 'true' ]; then
  ${WLSIMG_BUILDER:-docker} stop "${reg_name}"
  ${WLSIMG_BUILDER:-docker} rm --force "${reg_name}"
fi
${WLSIMG_BUILDER:-docker} run \
  -d --restart=always -p "127.0.0.1:${reg_port}:5000" --name "${reg_name}" \
  phx.ocir.io/weblogick8s/test-images/docker/registry:2

reg_host="${reg_name}"
echo "Registry Host: ${reg_host}"

echo 'Create a cluster with the local registry enabled in containerd'
cat <<EOF | kind create cluster --name "${kind_name}" --kubeconfig "${RESULT_ROOT}/kubeconfig" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  disableDefaultCNI: ${disableDefaultCNI}
  podSubnet: 192.168.0.0/16
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${reg_port}"]
    endpoint = ["http://${reg_host}:${reg_port}"]
nodes:
  - role: control-plane
    image: ${kind_image}
  - role: worker
    image: ${kind_image}
    extraMounts:
      - hostPath: ${PV_ROOT}
        containerPath: ${PV_ROOT}
EOF

echo "Access your cluster in other terminals with:"
echo "  export KUBECONFIG=\"${RESULT_ROOT}/kubeconfig\""
echo "  ${KUBERNETES_CLI} cluster-info --context \"kind-${kind_name}\""
export KUBECONFIG="${RESULT_ROOT}/kubeconfig"
${KUBERNETES_CLI} cluster-info --context "kind-${kind_name}"

if [ "${cni_implementation}" = "calico" ]; then
  echo "Install Calico"
  ${KUBERNETES_CLI} create -f https://docs.projectcalico.org/manifests/tigera-operator.yaml
  ${KUBERNETES_CLI} create -f https://docs.projectcalico.org/manifests/custom-resources.yaml
fi

${KUBERNETES_CLI} get node -o wide

for node in $(kind get nodes --name "${kind_name}"); do
  ${KUBERNETES_CLI} annotate node "${node}" tilt.dev/registry=localhost:${reg_port};
done

containers=$(${WLSIMG_BUILDER:-docker} network inspect ${kind_network} -f "{{range .Containers}}{{.Name}} {{end}}")
needs_connect="true"
for c in ${containers}; do
  if [ "$c" = "${reg_name}" ]; then
    needs_connect="false"
  fi
done
if [ "${needs_connect}" = "true" ]; then
  ${WLSIMG_BUILDER:-docker} network connect "${kind_network}" "${reg_name}" || true
fi

# Document the local registry
# https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
cat <<EOF | ${KUBERNETES_CLI} apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${reg_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF

echo 'Set up test running ENVVARs...'
export KIND_REPO="localhost:${reg_port}/"
export K8S_NODEPORT_HOST=`${KUBERNETES_CLI} get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}'`
export no_proxy="${no_proxy},${K8S_NODEPORT_HOST}"
echo 'no_proxy ${no_proxy}'
if [[ "$OSTYPE" == "darwin"* ]]; then
  export JAVA_HOME=$(/usr/libexec/java_home)
else
  export JAVA_HOME="${JAVA_HOME:-`type -p java|xargs readlink -f|xargs dirname|xargs dirname`}"
fi

if [ "$skip_tests" = true ] ; then
  echo 'Cluster created. Skipping tests.'
  exit 0
fi

echo "${WLSIMG_BUILDER:-docker} info"
${WLSIMG_BUILDER:-docker} info
${WLSIMG_BUILDER:-docker} ps

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

# Check for invalid Test Filter/Maven Profile Combination
if [ "${maven_profile_name}" == "integration-tests" ] && 
   [ "${test_filter}" == "**/It*" ] ; then
     echo "(ERROR) All tests cannot be run with [integration-tests] profile, choose different profile"
     exit 0
fi

if [ "${maven_profile_name}" == "kind-sequential" ]; then
   echo "Overriding the parallel_run to false for kind-sequential profiler"
   parallel_run=false
   threads=1
fi
# If IT_TEST is set, integration-test profile is used to run the tests and 
# MAVEN_PROFILE_NAME parameter is ignored

# If a specific maven profile is chosen, all tests are run with the chosen 
# profile and IT_TEST parameter is ignored

echo "Run tests..."
if [ "${test_filter}" != "**/It*" ]; then
  echo "Overriding the profile to integration-test"
  echo "Running mvn -Dit.test=${test_filter} -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -Dwle.download.url=${wle_download_url} -DPARALLEL_CLASSES=${parallel_run} -DNUMBER_OF_THREADS=${threads}  -pl integration-tests -P integration-tests verify"
  time mvn -Dit.test="${test_filter}" -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -DPARALLEL_CLASSES="${parallel_run}" -DNUMBER_OF_THREADS="${threads}" -pl integration-tests -P integration-tests verify 2>&1 | tee "${RESULT_ROOT}/kindtest.log" || captureLogs
else
    echo "Running Integration tests with profile  [${maven_profile_name}]"
    echo "Running mvn -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -Dwle.download.url=${wle_download_url} -DPARALLEL_CLASSES=${parallel_run} -DNUMBER_OF_THREADS=${threads} -pl integration-tests -P ${maven_profile_name} verify"
    time mvn -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -DPARALLEL_CLASSES="${parallel_run}" -DNUMBER_OF_THREADS="${threads}" -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/kindtest.log" || captureLogs
fi

echo "Collect journalctl logs"
${WLSIMG_BUILDER:-docker} exec kind-worker journalctl --utc --dmesg --system > "${RESULT_ROOT}/journalctl-kind-worker.out"
${WLSIMG_BUILDER:-docker} exec kind-control-plane journalctl --utc --dmesg --system > "${RESULT_ROOT}/journalctl-kind-control-plane.out"

echo "Destroy cluster and registry"
kind delete cluster --name "${kind_name}"
${WLSIMG_BUILDER:-docker} stop "${reg_name}"
${WLSIMG_BUILDER:-docker} rm --force "${reg_name}"

