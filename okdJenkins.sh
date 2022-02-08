#!/bin/bash
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script checks for the below required environment variables on Jenkins and runs the integration tests
# APACHE_MAVEN_HOME
# HELM_VERSION
# KUBECTL_VERSION
# #IIT_TEST
# WDT_DOWNLOAD_URL
# WIT_DOWNLOAD_URL
# NUMBER_OF_THREADS
# JAVA_HOME
# OCR_PASSWORD
# OCR_USERNAME
# OCIR_USERNAME
# OCIR_PASSWORD
# OCIR_EMAIL
# NFS_SERVER
# PV_ROOT
# OKD

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

echo "WORKSPACE ${WORKSPACE}"

checkEnvVars  \
   APACHE_MAVEN_HOME  \
   HELM_VERSION  \
   KUBECTL_VERSION \
   IT_TEST  \
   WDT_DOWNLOAD_URL  \
   WIT_DOWNLOAD_URL  \
   NUMBER_OF_THREADS  \
   JAVA_HOME  \
   OCR_PASSWORD  \
   OCR_USERNAME  \
   OCIR_USERNAME  \
   OCIR_PASSWORD  \
   OCIR_EMAIL  \
   NFS_SERVER  \
   PV_ROOT  \
   OKD  \

mkdir -p ${WORKSPACE}/bin

export PATH=${JAVA_HOME}/bin:${APACHE_MAVEN_HOME}/bin:${WORKSPACE}/bin:$PATH

which java
java -version
checkJavaVersion

which mvn
mvn --version

docker version

export RESULT_ROOT=${WORKSPACE}/RESULT_ROOT
export BRANCH_NAME=${BRANCH}

cd $WORKSPACE
[ -d ${WORKSPACE}/logdir ] && rm -rf ${WORKSPACE}/logdir && mkdir -p ${WORKSPACE}/logdir
pwd
ls

echo "Info: Run tests.."
sh -x ./okdtest.sh -t "${IT_TEST}" -v ${KUBE_VERSION} -p ${PARALLEL_RUN} -d ${WDT_DOWNLOAD_URL} -i ${WIT_DOWNLOAD_URL} -x ${NUMBER_OF_THREADS} -m ${MAVEN_PROFILE_NAME}
