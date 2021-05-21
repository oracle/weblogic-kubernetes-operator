#!/bin/bash
# Copyright (c) 2021, Oracle and/or its affiliates.
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

function checkEnvVars {
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
function ver { printf %02d%02d%02d%02d%02d $(echo "$1" | tr '.' ' '); }
function checkJavaVersion {
  java_version=`java -version 2>&1 >/dev/null | grep 'java version' | awk '{print $3}'`
  echo "Info: java version ${java_version}"
  if [ $(ver $java_version) -lt $(ver "11.0.10") ]; then
    echo "Error: Java version should be 11.0.10 or higher"
    exit 1
  fi
}

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
curl -LO --retry 3 https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz
tar -xf helm-v${HELM_VERSION}-linux-amd64.tar.gz
cp linux-amd64/helm ${WORKSPACE}/bin/helm
helm version

echo 'Info: Set up kubectl...'
curl -LO --retry 3 https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl
mv kubectl bin/kubectl
chmod +x bin/kubectl
kubectl version --client=true

echo 'Info: Set up kind...'
curl -Lo ./kind --retry 3 https://kind.sigs.k8s.io/dl/v${KIND_VERSION}/kind-$(uname)-amd64
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

echo 'Info: Run build...'
mvn clean install

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add stable https://charts.helm.sh/stable --force-update
helm repo update

echo "Info: Run tests.."
sh -x ./kindtest.sh -t "${IT_TEST}" -v ${KUBE_VERSION} -p ${PARALLEL_RUN} -d ${WDT_DOWNLOAD_URL} -i ${WIT_DOWNLOAD_URL} -x ${NUMBER_OF_THREADS}


