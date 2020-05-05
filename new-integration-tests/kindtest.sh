#!/bin/bash
set -o errexit

if [[ -z "$WORKSPACE" ]]; then
  kinddir="/scratch/$USER/kindtest"
else
  kinddir="${WORKSPACE}/logdir/${BUILD_TAG}"
fi
mkdir -m777 -p "${kinddir}"
export RESULT_ROOT="${kinddir}/wl_k8s_test_results"
export PV_ROOT="${kinddir}/k8s-pvroot"
mkdir -m777 "$RESULT_ROOT"
mkdir -m777 "$PV_ROOT"

echo 'Remove old cluster (if any)...'
kind delete cluster

echo 'Create registry container unless it already exists'
reg_name='kind-registry'
reg_port='5000'
running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
if [ "${running}" != 'true' ]; then
  docker run \
    -d --restart=always -p "${reg_port}:5000" --name "${reg_name}" \
    registry:2
fi

echo 'Create cluster...'
cp kind-config.yaml.template "$RESULT_ROOT/kind-config.yaml"
sed -i -e "s|HOSTPATH|${PV_ROOT}|g" "$RESULT_ROOT/kind-config.yaml"
kind create cluster --config="$RESULT_ROOT/kind-config.yaml"

echo 'Create a cluster with the local registry enabled in containerd'
kind_version='v1.15.7@sha256:e2df133f80ef633c53c0200114fce2ed5e1f6947477dbc83261a6a921169488d'
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${reg_port}"]
    endpoint = ["http://${reg_name}:${reg_port}"]
nodes:
  - role: control-plane
    image: kindest/node:${kind_version}
    # this 'tricks' apiserver into listening on 0.0.0.0:6443
    extraPortMappings:
      - containerPort: 6443
        hostPort: 6443
        listenAddress: "0.0.0.0"  # needed on gitlab DinD runner so we can talk to it from another container
        protocol: tcp
  - role: worker
    image: kindest/node:${kind_version}
    extraMounts:
      - hostPath: ${PV_ROOT}
        containerPath: ${PV_ROOT}
EOF

kubectl cluster-info --context kind-kind
kubectl get node -o wide

echo 'Checking for required ENVVARs'
[[ -z "$DOCKER_USERNAME" ]] && { echo "Error: DOCKER_USERNAME must be set"; exit 1; }
[[ -z "$DOCKER_PASSWORD" ]] && { echo "Error: DOCKER_PASSWORD must be set"; exit 1; }
[[ -z "$DOCKER_EMAIL" ]] && { echo "Error: DOCKER_EMAIL must be set"; exit 1; }
[[ -z "$REPO_USERNAME" ]] && { echo "Error: REPO_USERNAME must be set"; exit 1; }
[[ -z "$REPO_PASSWORD" ]] && { echo "Error: REPO_PASSWORD must be set"; exit 1; }
[[ -z "$REPO_REGISTRY" ]] && { echo "Error: REPO_REGISTRY must be set"; exit 1; }
[[ -z "$REPO_EMAIL" ]] && { echo "Error: REPO_EMAIL must be set"; exit 1; }
[[ -z "$OCR_USERNAME" ]] && { echo "Error: OCR_USERNAME must be set"; exit 1; }
[[ -z "$OCR_PASSWORD" ]] && { echo "Error: OCR_PASSWORD must be set"; exit 1; }

echo 'Set up test running ENVVARs...'
export KIND_REPO="localhost:${reg_port}/"
export K8S_NODEPORT_HOST=`kubectl get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}'`

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"

echo 'Run tests...'
time mvn -pl new-integration-tests -P integration-tests verify 2>&1 | tee "$RESULT_ROOT/kindtest.log"