#!/bin/bash
# Copyright (c) 2023, Oracle and/or its affiliates.

## Begin WKO IT specific 
test_filter="**/It*"
parallel_run="false"
threads="2"
wdt_download_url="https://github.com/oracle/weblogic-deploy-tooling/releases/latest"
wit_download_url="https://github.com/oracle/weblogic-image-tool/releases/latest"
wle_download_url="https://github.com/oracle/weblogic-logging-exporter/releases/latest"
maven_profile_name="v8o"

while getopts "v:n:o:t:c:x:p:d:i:l:m:sh" opt; do
  case $opt in
    v) k8s_version="${OPTARG}"
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

## End WKO IT specific 

# Setup defaults
K8S_VERSION=${k8s_version}
ADMIN_CLUSTER=true
MANAGED_CLUSTER=false
BUCKET_NAME="verrazzano-builds"
OBJ_NAME_PREFIX=""
SKIP_REGISTER=false

# Validate the command line inputs
function validate_inputs() {
  # Validate the Kubernetes version for creating the Kind clusters
  case "${K8S_VERSION}" in
    1.19) KIND_IMAGE="kindest/node:v1.19.16@sha256:d9c819e8668de8d5030708e484a9fdff44d95ec4675d136ef0a0a584e587f65c";;
    1.20) KIND_IMAGE="kindest/node:v1.20.15@sha256:6f2d011dffe182bad80b85f6c00e8ca9d86b5b8922cdf433d53575c4c5212248";;
    1.21) KIND_IMAGE="kindest/node:v1.21.12@sha256:f316b33dd88f8196379f38feb80545ef3ed44d9197dca1bfd48bcb1583210207";;
    1.22) KIND_IMAGE="kindest/node:v1.22.9@sha256:8135260b959dfe320206eb36b3aeda9cffcb262f4b44cda6b33f7bb73f453105";;
    1.23) KIND_IMAGE="kindest/node:v1.23.6@sha256:b1fa224cc6c7ff32455e0b1fd9cbfd3d3bc87ecaa8fcb06961ed1afb3db0f9ae";;
    1.24) KIND_IMAGE="kindest/node:v1.24.0@sha256:0866296e693efe1fed79d5e6c7af8df71fc73ae45e3679af05342239cdc5bc8e";;
    *)
      echo "ERROR: Invalid value for Kubernetes Version ${K8S_VERSION}."
      exit 1;;
  esac

  if [ -n "${INSTALL_VERSION}" ] && [ -n "${VZ_BRANCH}" ]; then
    echo "ERROR: cannot specify both branch and install version."
    exit 1
  fi

  if [ -n "${COMMIT}" ] && [ -z "${VZ_BRANCH}" ]; then
    echo "ERROR: cannot specify commit if no branch is specified"
    exit 1
  fi

  if [ -z "${VZ_BRANCH}" ]; then
    VZ_BRANCH=master
  fi
  if [ -n "${COMMIT}" ]; then
      BUCKET_NAME="verrazzano-builds-by-commit"
      OBJ_NAME_PREFIX="ephemeral/"
      VZ_BRANCH_AND_COMMIT="${VZ_BRANCH}/${COMMIT}"
      echo "A commit is specified, using ${BUCKET_NAME} bucket"
  else
      VZ_BRANCH_AND_COMMIT=$VZ_BRANCH
  fi
  echo "Branch is ${VZ_BRANCH}, prefix is ${OBJ_NAME_PREFIX}"
  # If install version is not specified, use branch to derive operator URL
  if [ -z "${INSTALL_VERSION}" ]; then
    OPERATOR_URL="https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/${BUCKET_NAME}/o/${OBJ_NAME_PREFIX}${VZ_BRANCH_AND_COMMIT}/operator.yaml"
    return
  fi

  if [[ $INSTALL_VERSION = v* ]]; then
    if [[ $INSTALL_VERSION = v1.0* ]] || [[ $INSTALL_VERSION = v1.1* ]] || [[ $INSTALL_VERSION = v1.2* ]] || \
     [[ $INSTALL_VERSION = v1.3* ]]; then
      OPERATOR_URL="https://github.com/verrazzano/verrazzano/releases/download/${INSTALL_VERSION}/operator.yaml"
      return
    else
      OPERATOR_URL="https://github.com/verrazzano/verrazzano/releases/download/${INSTALL_VERSION}/verrazzano-platform-operator.yaml"
      return
    fi
  fi

  # Validate the Verrazzano installation version
  case "${INSTALL_VERSION}" in
    release-1.0) OPERATOR_URL=https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/verrazzano-builds/o/release-1.0/operator.yaml;;
    release-1.1) OPERATOR_URL=https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/verrazzano-builds/o/release-1.1/operator.yaml;;
    release-1.2) OPERATOR_URL=https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/verrazzano-builds/o/release-1.2/operator.yaml;;
    release-1.3) OPERATOR_URL=https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/verrazzano-builds/o/release-1.3/operator.yaml;;
    release-1.4) OPERATOR_URL=https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/verrazzano-builds/o/release-1.4/operator.yaml;;
    master) OPERATOR_URL=https://objectstorage.us-phoenix-1.oraclecloud.com/n/stevengreenberginc/b/verrazzano-builds/o/master/operator.yaml;;
    *)
      echo "The install version '${INSTALL_VERSION}' is not valid"
      exit 1;;
  esac

}

function delete_cluster() {
  local name=$1
  kind delete cluster --name $1
}

# Create the Kind cluster with the given name
function create_cluster() {
local name=$1
kind create cluster --name $1 --config - <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    image: ${KIND_IMAGE}
    kubeadmConfigPatches:
      - |
        kind: ClusterConfiguration
        apiServer:
          extraArgs:
            "service-account-issuer": "kubernetes.default.svc"
            "service-account-signing-key-file": "/etc/kubernetes/pki/sa.key"
#    extraMounts:
#      - containerPath: /var/lib/kubelet/config.json
#        hostPath: $HOME/.docker/config.json
EOF
}


function install_metallb() {
 local address_range=$1
 local kc=$2
$kc apply -f https://raw.githubusercontent.com/metallb/metallb/v0.11.0/manifests/namespace.yaml
$kc apply -f https://raw.githubusercontent.com/metallb/metallb/v0.11.0/manifests/metallb.yaml
$kc create secret generic -n metallb-system memberlist --from-literal=secretkey="$(openssl rand -base64 128)"

$kc apply -f - <<-EOF
apiVersion: v1
kind: ConfigMap
metadata:
  namespace: metallb-system
  name: config
data:
  config: |
    address-pools:
    - name: my-ip-space
      protocol: layer2
      addresses:
      - $address_range
EOF
}

function install_verrazzano_managed() {
$K1 apply -f ${OPERATOR_URL} || exit 1
$K1 -n verrazzano-install rollout status deployment/verrazzano-platform-operator
$K1 apply -f - <<EOF
apiVersion: install.verrazzano.io/v1alpha1
kind: Verrazzano
metadata:
  name: managed1
spec:
  profile: managed-cluster
  defaultVolumeSource:
    persistentVolumeClaim:
      claimName: vmi # set storage for the metrics stack
  volumeClaimSpecTemplates:
    - metadata:
        name: vmi
      spec:
        resources:
          requests:
            storage: 2Gi
EOF
}

function install_verrazzano_admin() {
$KA apply -f ${OPERATOR_URL} || exit 1
$KA -n verrazzano-install rollout status deployment/verrazzano-platform-operator
$KA apply -f - <<EOF
apiVersion: install.verrazzano.io/v1alpha1
kind: Verrazzano
metadata:
  name: admin
spec:
  profile: dev
  defaultVolumeSource:
    persistentVolumeClaim:
      claimName: vmi # set storage for the metrics stack
  components:
    keycloak:
      mysql:
        volumeSource:
          persistentVolumeClaim:
            claimName: mysql # set storage for keycloak's MySql instance
  volumeClaimSpecTemplates:
    - metadata:
        name: mysql
      spec:
        resources:
          requests:
            storage: 2Gi
    - metadata:
        name: vmi
      spec:
        resources:
          requests:
            storage: 2Gi
EOF
}

function wait_for_installs() {
  if [ "$ADMIN_CLUSTER" != "false" ] ; then
    $KA wait --timeout=30m --for=condition=InstallComplete verrazzano/admin
  fi
  if [ "$MANAGED_CLUSTER" != "false" ] ; then
    $K1 wait --timeout=30m --for=condition=InstallComplete verrazzano/managed1
  fi
}

function register_managed_cluster() {
CA_SECRET_FILE=managed1.yaml
kubectl config use-context kind-managed1
TLS_SECRET=$(kubectl -n cattle-system get secret tls-ca-additional -o json | jq -r '.data."ca-additional.pem"')
if [ ! -z "${TLS_SECRET%%*( )}" ] && [ "null" != "${TLS_SECRET}" ] ; then
  CA_CERT=$(kubectl  -n cattle-system get secret tls-ca-additional -o json | jq -r '.data."ca-additional.pem"' | base64 --decode)
else
  TLS_SECRET=$(kubectl -n verrazzano-system get secret verrazzano-tls -o json | jq -r '.data."ca.crt"')
  if [ ! -z "${TLS_SECRET%%*( )}" ] && [ "null" != "${TLS_SECRET}" ] ; then
    CA_CERT=$(kubectl  -n verrazzano-system get secret verrazzano-tls -o json | jq -r '.data."ca.crt"' | base64 --decode)
  fi
fi
if [ ! -z "${CA_CERT}" ] ; then
   $K1 create secret generic "ca-secret-managed1" -n verrazzano-mc --from-literal=cacrt="$CA_CERT" --dry-run=client -o yaml > ${CA_SECRET_FILE}
fi

kubectl config use-context kind-admin
$KA apply -f ${CA_SECRET_FILE}

ADMIN_K8S_SERVER_ADDRESS="$(kind get kubeconfig --internal --name admin | grep "server:" | awk '{ print $2 }')"

$KA apply -f <<EOF -
apiVersion: v1
kind: ConfigMap
metadata:
  name: verrazzano-admin-cluster
  namespace: verrazzano-mc
data:
  server: "${ADMIN_K8S_SERVER_ADDRESS}"
EOF

$KA apply -f <<EOF -
apiVersion: clusters.verrazzano.io/v1alpha1
kind: VerrazzanoManagedCluster
metadata:
  name: managed1
  namespace: verrazzano-mc
spec:
  description: "Test VerrazzanoManagedCluster object"
  caSecret: ca-secret-managed1
EOF

# wait for the VMC to be ready - at this point the manifest secret would have been generated
$KA wait --for=condition=Ready vmc managed1 -n verrazzano-mc

$KA get secret verrazzano-cluster-managed1-manifest -n verrazzano-mc -o jsonpath={.data.yaml} | base64 --decode > register.yaml

$K1 apply -f register.yaml
}

function deploy_app() {
$KA apply -f <<EOF -
apiVersion: clusters.verrazzano.io/v1alpha1
kind: VerrazzanoProject
metadata:
  name: test-project
  namespace: verrazzano-mc
spec:
  template:
    namespaces:
      - metadata:
          name: hello-helidon
  placement:
    clusters:
      - name: managed1
EOF

$KA apply -f <<EOF -
apiVersion: clusters.verrazzano.io/v1alpha1
kind: MultiClusterComponent
metadata:
  name: hello-helidon-component
  namespace: hello-helidon
spec:
  template:
    spec:
      workload:
        apiVersion: oam.verrazzano.io/v1alpha1
        kind: VerrazzanoHelidonWorkload
        metadata:
          name: hello-helidon-workload
          namespace: hello-helidon
          labels:
            app: hello-helidon
        spec:
          deploymentTemplate:
            metadata:
              name: hello-helidon-deployment
            podSpec:
              containers:
                - name: hello-helidon-container
                  image: "ghcr.io/verrazzano/example-helidon-greet-app-v1:1.0.0-1-20210728181814-eb1e622"
                  ports:
                    - containerPort: 8080
                      name: http
  placement:
    clusters:
      - name: managed1
EOF

$KA apply -f <<EOF -
apiVersion: clusters.verrazzano.io/v1alpha1
kind: MultiClusterApplicationConfiguration
metadata:
  name: hello-helidon-appconf
  namespace: hello-helidon
spec:
  template:
    metadata:
      annotations:
        version: v1.0.0
        description: "Hello Helidon application"
    spec:
      components:
        - componentName: hello-helidon-component
          traits:
            - trait:
                apiVersion: oam.verrazzano.io/v1alpha1
                kind: MetricsTrait
                spec:
                  scraper: verrazzano-system/vmi-system-prometheus-0
            - trait:
                apiVersion: oam.verrazzano.io/v1alpha1
                kind: IngressTrait
                metadata:
                  name: hello-helidon-ingress
                spec:
                  rules:
                    - paths:
                        - path: "/greet"
                          pathType: Prefix
  placement:
    clusters:
      - name: managed1
EOF
}

# usage - display the help for this script
function usage {
    echo
    echo "usage: $0"
    echo "  -a <boolean value>       Whether to install an admin cluster - defaults to true. Any value other than false is assumed to mean true."
    echo "  -m <boolean value>       Whether to install a managed cluster - defaults to true. Any value other than false is assumed to mean true."
    echo "                           Note - managed cluster registration and MC app deployment will only happen if BOTH clusters are enabled."
    echo "  -b branch                The branch from which to install Verrazzano (default is master). Error if both version and branch are specified."
    echo "  -c commit                The commit from which to install Verrazzano for the given branch. Ignored if branch is not specified."
    echo "  -k k8s version           The version of Kubernetes to use for the Kind clusters."
    echo "                           Valid values are 1.19, 1.20, 1.21 and 1.22.  Default is 1.20."
    echo "  -v version               The version of Verrazzano to install. Error if both version and branch are specified."
    echo "                           Valid values are v1.0.0, v1.0.1, v1.0.2, v1.0.3, v1.0.4, release-1.0"
    echo "                                            v1.1.0, v1.1.1, v1.1.2, release-1.1"
    echo "                                            v1.2.0, v1.2.1, v1.2.2, release-1.2"
    echo "  -o output_directory      The full path to the directory in which the yaml will be generated"
    echo "  -h                       Help"
    echo
    exit 1
}

# Parse the command line options
while getopts v:k:b:c:a:m:h flag
do
    case "${flag}" in
        k) K8S_VERSION=${OPTARG};;
        b) VZ_BRANCH=${OPTARG};;
        c) COMMIT=${OPTARG};;
        v) INSTALL_VERSION=${OPTARG};;
        a) ADMIN_CLUSTER=${OPTARG};;
        m) MANAGED_CLUSTER=${OPTARG};;
        h) usage;;
        *) usage;;
    esac
done

# Validate the command line inputs
validate_inputs

echo "$0 -v ${INSTALL_VERSION}"
echo "   KIND_IMAGE=${KIND_IMAGE}"
echo "   OPERATOR_URL=${OPERATOR_URL}"

export KA='kubectl --context kind-admin '
export K1='kubectl --context kind-managed1 '
export KUBECONFIG=~/.kube/config

# Cleanup the previous run and create new Kind clusters
if [ "$REGISTER_ONLY" != "true" ]; then
delete_cluster admin
delete_cluster managed1

SUBNET=$(docker inspect kind | jq '.[0].IPAM.Config[0].Subnet' -r | sed 's|/.*||g')
ADMIN_ADDR_RANGE="${SUBNET%.*}.230-${SUBNET%.*}.250"
MANAGED1_ADDR_RANGE="${SUBNET%.*}.210-${SUBNET%.*}.229"

if [ "$ADMIN_CLUSTER" != "false" ] ; then
echo
  create_cluster admin
  install_metallb $ADMIN_ADDR_RANGE "$KA"
  install_verrazzano_admin
fi
if [ "$MANAGED_CLUSTER" != "false" ] ; then
echo
  create_cluster managed1
  install_metallb $MANAGED1_ADDR_RANGE "$K1"
  install_verrazzano_managed
fi

wait_for_installs
fi

if [ "$ADMIN_CLUSTER" != "false" ] && [ "$MANAGED_CLUSTER" != "false" ] && [ "$SKIP_REGISTER" == "false" ] ; then
  register_managed_cluster
  deploy_app
fi


## WKO IT specific 

outdir="${WORKSPACE}/logdir/${BUILD_TAG}"

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

echo 'Clean up result root...'
rm -rf "${RESULT_ROOT:?}/*"


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
    echo "Running Verrazzano Integration tests with profile  [${maven_profile_name}]"
    echo "Running mvn -Dwdt.download.url=${wdt_download_url} -Dwit.download.url=${wit_download_url} -Dwle.download.url=${wle_download_url} -DPARALLEL_CLASSES=${parallel_run} -DNUMBER_OF_THREADS=${threads} -pl integration-tests -P ${maven_profile_name} verify"
    time mvn -Dwdt.download.url="${wdt_download_url}" -Dwit.download.url="${wit_download_url}" -Dwle.download.url="${wle_download_url}" -DPARALLEL_CLASSES="${parallel_run}" -DNUMBER_OF_THREADS="${threads}" -pl integration-tests -P ${maven_profile_name} verify 2>&1 | tee "${RESULT_ROOT}/kindtest.log" || captureLogs
fi

echo "Collect journalctl logs"
${WLSIMG_BUILDER:-docker} exec kind-worker journalctl --utc --dmesg --system > "${RESULT_ROOT}/journalctl-kind-worker.out"
${WLSIMG_BUILDER:-docker} exec kind-control-plane journalctl --utc --dmesg --system > "${RESULT_ROOT}/journalctl-kind-control-plane.out"

## End WKO IT specific 