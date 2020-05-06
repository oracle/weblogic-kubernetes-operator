#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
set -o errexit

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} [-v <version>] [-n <name>] [-o <directory>] [-h]"
  echo "  -v Kubernetes version (optional) "
  echo "      (default: 1.15.11, supported values: 1.18, 1.18.2, 1.17, 1.17.5, 1.16, 1.16.9, 1.15, 1.15.11, 1.14, 1.14.10) "
  echo "  -n Kind cluster name (optional) "
  echo "      (default: kind) "
  echo "  -o Output directory (optional) "
  echo "      (default: \${WORKSPACE}/logdir/\${BUILD_TAG}, if \${WORKSPACE} defined, else /scratch/\$USER/kindtest) "
  echo "  -h Help"
  exit $1
}

k8s_version="1.15.11"
kind_name="kind"
if [[ -z "$WORKSPACE" ]]; then
  outdir="/scratch/$USER/kindtest"
else
  outdir="${WORKSPACE}/logdir/${BUILD_TAG}"
fi

while getopts ":h:n:o:v:" opt; do
  case $opt in
    v) k8s_version="${OPTARG}"
    ;;
    n) kind_name="${OPTARG}"
    ;;
    o) outdir="${OPTARG}"
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
if [ -z "$kind_image" ]; then
  echo "Unsupported Kubernetes version: ${k8s_version}"
  exit 1
fi

mkdir -m777 -p "${outdir}"
export RESULT_ROOT="${outdir}/wl_k8s_test_results"
if [ -d "$RESULT_ROOT" ]; then
  rm -Rf "$RESULT_ROOT/*"
else
  mkdir -m777 "$RESULT_ROOT"
fi

export PV_ROOT="${outdir}/k8s-pvroot"
if [ -d "$PV_ROOT" ]; then
  rm -Rf "$PV_ROOT/*"
else
  mkdir -m777 "$PV_ROOT"
fi

echo 'Remove old cluster (if any)...'
kind delete cluster --name ${kind_name}

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
cat <<EOF | kind create cluster --name "${kind_name}" --config=-
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

kubectl cluster-info --context "kind-${kind_name}"
kubectl get node -o wide

for node in $(kind get nodes --name "${kind_name}"); do
  kubectl annotate node "${node}" tilt.dev/registry=localhost:${reg_port};
done

if [ "${kind_network}" != "bridge" ]; then
  containers=$(docker network inspect ${kind_network} -f "{{range .Containers}}{{.Name}} {{end}}")
  needs_connect="true"
  for c in $containers; do
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
time mvn -pl new-integration-tests -P integration-tests verify 2>&1 | tee "$RESULT_ROOT/kindtest.log"
