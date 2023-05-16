#!/bin/bash
# Copyright (c) 2021, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script checks for the below required environment variables on Jenkins and runs the integration tests
# APACHE_MAVEN_HOME
# HELM_VERSION
# KUBE_VERSION
# KIND_VERSION
# IT_TEST
# WDT_DOWNLOAD_URL
# WIT_DOWNLOAD_URL
# NUMBER_OF_THREADS
# JAVA_HOME
# BASE_IMAGES_REPO
# BASE_IMAGES_REPO_USERNAME
# BASE_IMAGES_REPO_PASSWORD
# BASE_IMAGES_REPO_EMAIL

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

# Record start time in a format appropriate for journalctl --since
start_time=$(date +"%Y-%m-%d %H:%M:%S")

echo "WORKSPACE ${WORKSPACE}"

checkEnvVars  \
   APACHE_MAVEN_HOME  \
   HELM_VERSION  \
   KUBE_VERSION \
   KIND_VERSION  \
   IT_TEST  \
   WDT_DOWNLOAD_URL  \
   WIT_DOWNLOAD_URL  \
   NUMBER_OF_THREADS  \
   JAVA_HOME  \
   BASE_IMAGES_REPO \
   BASE_IMAGES_REPO_USERNAME  \
   BASE_IMAGES_REPO_PASSWORD  \
   BASE_IMAGES_REPO_EMAIL


mkdir -p ${WORKSPACE}/bin

export PATH=${JAVA_HOME}/bin:${APACHE_MAVEN_HOME}/bin:${WORKSPACE}/bin:$PATH

which java
java -version
checkJavaVersion

which mvn
mvn --version

echo 'Info: Set up helm...'
curl -Lo "helm.tar.gz" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/helm%2Fhelm-v${HELM_VERSION}.tar.gz"
tar zxf helm.tar.gz
cp linux-amd64/helm ${WORKSPACE}/bin/helm
helm version

KCLI="kubectl" # this string has a deliberate exclusion in the 'validateCLI.sh' validation check for direct use of the k8s cli
echo "Info: Set up ${KCLI}..."
curl -Lo "${WORKSPACE}/bin/${KCLI}" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/${KCLI}%2F${KCLI}-v${KUBE_VERSION}"
chmod +x ${WORKSPACE}/bin/${KCLI}
${KCLI} version --client=true -o yaml

echo 'Info: Set up kind...'
curl -Lo "${WORKSPACE}/bin/kind" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/kind%2Fkind-v${KIND_VERSION}"
chmod +x "${WORKSPACE}/bin/kind"
kind version

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

mkdir $WORKSPACE/jdk
cd $WORKSPACE/jdk
wget https://download.oracle.com/java/17/archive/jdk-17.0.5_linux-x64_bin.tar.gz
tar -xvf jdk-17.0.5_linux-x64_bin.tar.gz

export JAVA_HOME=$WORKSPACE/jdk/jdk-17.0.5
cd $WORKSPACE
echo 'Info: Run build...'
mvn clean install

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add stable https://charts.helm.sh/stable --force-update
helm repo update

echo "Info: Run tests.."
sh -x ./kindtest.sh -t "${IT_TEST}" -v ${KUBE_VERSION} -p ${PARALLEL_RUN} -d ${WDT_DOWNLOAD_URL} -i ${WIT_DOWNLOAD_URL} -x ${NUMBER_OF_THREADS} -m ${MAVEN_PROFILE_NAME}

mkdir -m777 -p "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results"
journalctl --utc --dmesg --system --since "$start_time" > "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results/journalctl-compute.out"

sudo chown -R opc "${WORKSPACE}/logdir/${BUILD_TAG}"
