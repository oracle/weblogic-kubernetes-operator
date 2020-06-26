#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script provisions a Kubernetes cluster using Kind (https://kind.sigs.k8s.io/) and runs the new
# integration test suite against that cluster.
#
# To install Kind:
#    curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.8.0/kind-$(uname)-amd64
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
# You can see the images on a node by running: "docker exec -it kind-worker crictl images" where kind-worker
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

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} [-v <version>] [-n <name>] [-o <directory>] [-t <tests>] [-h]"
  echo "  -v Kubernetes version (optional) "
  echo "      (default: 1.15.11, supported values: 1.18, 1.18.2, 1.17, 1.17.5, 1.16, 1.16.9, 1.15, 1.15.11, 1.14, 1.14.10) "
  echo "  -n Kind cluster name (optional) "
  echo "      (default: kind) "
  echo "  -o Output directory (optional) "
  echo "      (default: \${WORKSPACE}/logdir/\${BUILD_TAG}, if \${WORKSPACE} defined, else /scratch/\${USER}/kindtest) "
  echo "  -t Test filter (optional) "
  echo "      (default: **/It*) "
  echo "  -p Run It classes in parallel"
  echo "      (default: false) "
  echo "  -h Help"
  exit $1
}

k8s_version="1.15.11"
kind_name="kind"
if [[ -z "${WORKSPACE}" ]]; then
  outdir="/scratch/${USER}/kindtest"
else
  outdir="${WORKSPACE}/logdir/${BUILD_TAG}"
fi
test_filter="**/It*"
parallel_classes="false"

while getopts ":h:n:o:t:v:p:" opt; do
  case $opt in
    v) k8s_version="${OPTARG}"
    ;;
    n) kind_name="${OPTARG}"
    ;;
    o) outdir="${OPTARG}"
    ;;
    t) test_filter="${OPTARG}"
    ;;
    p) parallel_classes="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

function versionprop {
  grep "${1}=" "${scriptDir}/kindversions.properties"|cut -d'=' -f2
}

kind_image=$(versionprop "${k8s_version}")
if [ -z "${kind_image}" ]; then
  echo "Unsupported Kubernetes version: ${k8s_version}"
  exit 1
fi

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
kind delete cluster --name ${kind_name} --kubeconfig "${RESULT_ROOT}/kubeconfig"

kind_version=$(kind version)
kind_network='kind'
reg_name='kind-registry'
reg_port='5000'
case "${kind_version}" in
  "kind v0.7."* | "kind v0.6."* | "kind v0.5."*)
    kind_network='bridge'
    ;;
esac

echo 'Create registry container unless it already exists'
running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
if [ "${running}" != 'true' ]; then
  docker run \
    -d --restart=always -p "${reg_port}:5000" --name "${reg_name}" \
    registry:2
fi

reg_host="${reg_name}"
if [ "${kind_network}" = "bridge" ]; then
    reg_host="$(docker inspect -f '{{.NetworkSettings.IPAddress}}' "${reg_name}")"
fi
echo "Registry Host: ${reg_host}"

echo 'Create a cluster with the local registry enabled in containerd'
cat <<EOF | kind create cluster --name "${kind_name}" --kubeconfig "${RESULT_ROOT}/kubeconfig" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
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
echo "  kubectl cluster-info --context \"kind-${kind_name}\""
export KUBECONFIG="${RESULT_ROOT}/kubeconfig"
kubectl cluster-info --context "kind-${kind_name}"
kubectl get node -o wide

for node in $(kind get nodes --name "${kind_name}"); do
  kubectl annotate node "${node}" tilt.dev/registry=localhost:${reg_port};
done

if [ "${kind_network}" != "bridge" ]; then
  containers=$(docker network inspect ${kind_network} -f "{{range .Containers}}{{.Name}} {{end}}")
  needs_connect="true"
  for c in ${containers}; do
    if [ "$c" = "${reg_name}" ]; then
      needs_connect="false"
    fi
  done
  if [ "${needs_connect}" = "true" ]; then
    docker network connect "${kind_network}" "${reg_name}" || true
  fi
fi

echo 'Set up test running ENVVARs...'
export KIND_REPO="localhost:${reg_port}/"
export K8S_NODEPORT_HOST=`kubectl get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}'`
export JAVA_HOME="${JAVA_HOME:-`type -p java|xargs readlink -f|xargs dirname|xargs dirname`}"

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

echo 'Run tests...'
echo 'Running mvn -Dit.test=\"${test_filter}\" -DPARALLEL_CLASSES=\"${parallel_classes}\" -pl new-integration-tests -P integration-tests verify'
time mvn -Dit.test="${test_filter}" -DPARALLEL_CLASSES="${parallel_classes}" -pl new-integration-tests -P integration-tests verify 2>&1 | tee "${RESULT_ROOT}/kindtest.log"
