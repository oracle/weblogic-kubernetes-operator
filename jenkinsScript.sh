#!/bin/bash
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script should be run on Jenkins Job to run the integration tests

echo "WORKSPACE ${WORKSPACE}"

if [ -z "$WORKSPACE" ]; then
   echo "Error: WORKSPACE env variable has to be set "
   exit 1
fi

if [ -z "$APACHE_MAVEN_HOME" ]; then
   echo "Error: APACHE_MAVEN_HOME env variable is not set"
   exit 1
fi


if [ -z "$HELM_VERSION" ]; then 
   echo "Error: HELM_VERSION env variable is not set"
   exit 1
fi

if [ -z "$KUBECTL_VERSION" ]; then
   echo "Error: KUBECTL_VERSION env variable is not set"
   exit 1
fi

if [ -z "$KIND_VERSION" ]; then
   echo "Error: KIND_VERSION env variable is not set"
   exit 1
fi

if [ -z "$IT_TEST" ]; then
   echo "Error: IT_TEST env variable is not set"
   exit 1
fi

if [ -z "$PARALLEL_RUN" ]; then
   echo "Error: PARALLEL_RUN env variable is not set"
   exit 1
fi
if [ -z "$WDT_DOWNLOAD_URL" ]; then
   echo "Error: WDT_DOWNLOAD_URL env variable is not set"
   exit 1
fi

if [ -z "$WIT_DOWNLOAD_URL" ]; then
   echo "Error: WIT_DOWNLOAD_URL env variable is not set"
   exit 1
fi
if [ -z "$NUMBER_OF_THREADS" ]; then
   echo "Error: NUMBER_OF_THREADS env variable is not set"
   exit 1
fi
if [ -z "$JAVA_HOME" ]; then
   echo "Error: JAVA_HOME env variable is not set, should be at least 11.0.10"
   exit 1
fi
if [ -z "$OCR_PASSWORD" ]; then
   echo "Error: OCR_PASSWORD env variable is not set"
   exit 1
fi
if [ -z "$OCR_USERNAME" ]; then
   echo "Error: OCR_USERNAME env variable is not set"
   exit 1
fi
if [ -z "$OCIR_USERNAME" ]; then
   echo "Error: OCIR_USERNAME env variable is not set"
   exit 1
fi
if [ -z "$OCIR_PASSWORD" ]; then
   echo "Error: OCIR_PASSWORD env variable is not set"
   exit 1
fi
if [ -z "$OCIR_EMAIL" ]; then
   echo "Error: OCIR_EMAIL env variable is not set"
   exit 1
fi

mkdir -p ${WORKSPACE}/bin

export PATH=${JAVA_HOME}/bin:${APACHE_MAVEN_HOME}/bin:${WORKSPACE}/bin:$PATH

which java
java -version

which mvn
mvn --version

echo 'Set up helm...'
curl -LO --retry 3 https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz
tar -xf helm-v${HELM_VERSION}-linux-amd64.tar.gz
cp linux-amd64/helm ${WORKSPACE}/bin/helm
helm version

echo 'Set up kubectl...'
curl -LO --retry 3 https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl
mv kubectl bin/kubectl
chmod +x bin/kubectl
kubectl version --client=true

echo 'Set up kind...'
curl -Lo ./kind --retry 3 https://kind.sigs.k8s.io/dl/v${KIND_VERSION}/kind-$(uname)-amd64
chmod +x ./kind
mv ./kind bin/kind
kind version

export TWO_CLUSTERS=false


export RESULT_ROOT=${WORKSPACE}/RESULT_ROOT

cd $WORKSPACE
[ -d ${WORKSPACE}/logdir ] && rm -rf ${WORKSPACE}/logdir && mkdir -p ${WORKSPACE}/logdir
pwd
ls

export BRANCH_NAME=${BRANCH}

echo "soft limits"
ulimit -a
echo "hard limits"
ulimit -aH

echo 'Run build...'
mvn clean install

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add stable https://charts.helm.sh/stable --force-update
helm repo update

echo "Run tests.."
sh -x ./kindtest.sh -t "${IT_TEST}" -v ${KUBE_VERSION} -p ${PARALLEL_RUN} -d ${WDT_DOWNLOAD_URL} -i ${WIT_DOWNLOAD_URL} -x ${NUMBER_OF_THREADS}

