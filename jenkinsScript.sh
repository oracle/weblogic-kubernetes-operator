#!/bin/bash
# Copyright (c) 2021,2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script checks for the below required environment variables on Jenkins and runs the integration tests
# APACHE_MAVEN_HOME
# HELM_VERSION
# KUBECTL_VERSION
# KIND_VERSION
# IT_TEST
# WDT_DOWNLOAD_URL
# WIT_DOWNLOAD_URL
# NUMBER_OF_THREADS
# JAVA_HOME
# OCR_PASSWORD
# OCR_USERNAME
# OCIR_USERNAME
# OCIR_PASSWORD
# OCIR_EMAIL

set -o errexit
set -o pipefail

checkEnvVars() {
  local has_errors=false
  while [ ! -z "${1}" ]; do
    if [ -z "${!1}" ]; then
      echo "Error: '${1}' env variable is not set"
      has_errors=true
    else
      if [ "${1/PASSWORD//}" = "${1}" ]; then
        echo "Info: env var ${1}='${!1}'"
      else
        echo "Info: env var ${1}='***'"
      fi
    fi
    shift
  done
  if [ ! "$has_errors" = "false" ]; then
    echo "Error: Missing env vars, exiting."
    exit 1
  fi
}
ver() { printf %02d%02d%02d%02d%02d $(echo "$1" | tr '.' ' '); }
checkJavaVersion() {
  java_version=`java -version 2>&1 >/dev/null | grep 'java version' | awk '{print $3}'`
  echo "Info: java version ${java_version}"
  if [ $(ver $java_version) -lt $(ver "11.0.10") ]; then
    echo "Error: Java version should be 11.0.10 or higher"
    exit 1
  fi
}
dockerLogin() {
  echo "Info: about to do docker login"
  if [ ! -z ${DOCKER_USERNAME+x} ] && [ ! -z ${DOCKER_PASSWORD+x} ]; then
    out=$(echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin)
    res=$?
    if [ $res -ne 0 ]; then
      echo 'docker login failed'
      exit 1
    fi
  else
    echo "Info: Docker credentials DOCKER_USERNAME and DOCKER_PASSWORD are not set."
  fi
}

# Record start time in a format appropriate for journalctl --since
start_time=$(date +"%Y-%m-%d %H:%M:%S")

echo "WORKSPACE ${WORKSPACE}"

checkEnvVars  \
   APACHE_MAVEN_HOME  \
   HELM_VERSION  \
   KUBECTL_VERSION \
   KIND_VERSION  \
   IT_TEST  \
   WDT_DOWNLOAD_URL  \
   WIT_DOWNLOAD_URL  \
   NUMBER_OF_THREADS  \
   JAVA_HOME  \
   OCR_PASSWORD  \
   OCR_USERNAME  \
   OCIR_USERNAME  \
   OCIR_PASSWORD  \
   OCIR_EMAIL


mkdir -p ${WORKSPACE}/bin

export PATH=${JAVA_HOME}/bin:${APACHE_MAVEN_HOME}/bin:${WORKSPACE}/bin:$PATH

which java
java -version
checkJavaVersion

which mvn
mvn --version

echo 'Info: Set up helm...'
curl --ipv4 -LO --retry 3 https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz
tar -xf helm-v${HELM_VERSION}-linux-amd64.tar.gz
cp linux-amd64/helm ${WORKSPACE}/bin/helm
helm version

echo 'Info: Set up kubectl...'
set +e
echo 'Info: download from object storage'
curl --ipv4 -LO --retry 3 https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/weblogicondocker/b/bucket-wko-jenkins/o/v${KUBECTL_VERSION}_kubectl
mv v${KUBECTL_VERSION}_kubectl bin/kubectl
chmod +x bin/kubectl
out=$(kubectl version --client=true)
res=$?
if [ $res -ne 0 ]; then
  for i in 1 2 3 ; do
   curl --ipv4 -LO --retry 3 https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl
   mv kubectl bin/kubectl
   chmod +x bin/kubectl
   out=$(kubectl version --client=true)
   res=$?
   [ $res -eq 0 ] && break
   sleep 10
  done
fi
set -e
kubectl version --client=true

echo 'Info: Set up kind...'
curl --ipv4 -Lo ./kind --retry 3 https://kind.sigs.k8s.io/dl/v${KIND_VERSION}/kind-$(uname)-amd64
chmod +x ./kind
mv ./kind bin/kind
kind version

export TWO_CLUSTERS=false
export RESULT_ROOT=${WORKSPACE}/RESULT_ROOT
export BRANCH_NAME=${BRANCH}

cd $WORKSPACE
[ -d ${WORKSPACE}/logdir ] && rm -rf ${WORKSPACE}/logdir && mkdir -p ${WORKSPACE}/logdir
pwd
ls

echo "Info: soft limits"
ulimit -a
echo "Info: hard limits"
ulimit -aH

dockerLogin

echo 'Info: Run build...'
mvn clean install

echo "Info: Run tests.."
export OUTDIR="${WORKSPACE}/staging"
mkdir -m777 -p "${OUTDIR}"
sh -x ./kindtest.sh -t "${IT_TEST}" -v ${KUBE_VERSION} -p ${PARALLEL_RUN} -d ${WDT_DOWNLOAD_URL} -i ${WIT_DOWNLOAD_URL} -x ${NUMBER_OF_THREADS} -m ${MAVEN_PROFILE_NAME} -o "${OUTDIR}"

mkdir -m777 -p "${OUTDIR}/wl_k8s_test_results"
journalctl --utc --dmesg --system --since "$start_time" > "${OUTDIR}/wl_k8s_test_results/journalctl-compute.out"

mkdir -m777 -p "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results"
cd "${OUTDIR}"
tar -cvf "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results/results.tar" *