#!/bin/bash
set -o errexit

if [[ -z "$WORKSPACE" ]]; then
  kinddir="/scratch/$USER/kindtest"
else
  kinddir="${WORKSPACE}/logdir/${BUILD_TAG}"
fi
mkdir -m777 -p "${kinddir}"
export RESULT_ROOT="${kinddir}/wl_k8s_test_results"
if [ -d "$RESULT_ROOT" ]; then
  rm -Rf "$RESULT_ROOT/*"
else
  mkdir -m777 "$RESULT_ROOT"
fi

export PV_ROOT="${kinddir}/k8s-pvroot"
if [ -d "$PV_ROOT" ]; then
  rm -Rf "$PV_ROOT/*"
else
  mkdir -m777 "$PV_ROOT"
fi

echo 'Remove old cluster (if any)...'
kind delete cluster

# desired cluster name; default is "kind"
KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-kind}"

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
kind_version='v1.15.7@sha256:e2df133f80ef633c53c0200114fce2ed5e1f6947477dbc83261a6a921169488d'
cat <<EOF | kind create cluster --name "${KIND_CLUSTER_NAME}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${reg_port}"]
    endpoint = ["http://${reg_host}:${reg_port}"]
nodes:
  - role: control-plane
    image: kindest/node:${kind_version}
  - role: worker
    image: kindest/node:${kind_version}
    extraMounts:
      - hostPath: ${PV_ROOT}
        containerPath: ${PV_ROOT}
EOF

kubectl cluster-info --context kind-kind
kubectl get node -o wide

for node in $(kind get nodes --name "${KIND_CLUSTER_NAME}"); do
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
