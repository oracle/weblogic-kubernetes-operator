#!/bin/bash -x
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Description:
#
#  This script install a given version of istio using Helm v3.x
#  Default istio version is 1.13.2
#  https://istio.io/docs/setup/install/istioctl/
#  https://istio.io/latest/docs/setup/install/standalone-operator/
#  https://github.com/istio/istio/releases
#  https://github.com/istio/istio/tags

# Usage:
#
#  $0 [istio-version] [install-dir]

# Define functions

KUBERNETES_CLI=${KUBERNETES_CLI:-kubectl}

install_istio() {

version=$1
workdir=$2

istiodir=${workdir}/istio-${version}
echo "Installing Istio version [${version}] in location [${istiodir}]"

${KUBERNETES_CLI} delete namespace istio-system --ignore-not-found
# create the namespace 'istio-system' 
${KUBERNETES_CLI} create namespace istio-system

( cd $workdir;
  curl -Lo "istio.tar.gz" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/istio%2Fistio-${version}-linux-amd64.tar.gz";
  tar zxf istio.tar.gz
)

( ${KUBERNETES_CLI} create secret generic docker-istio-secret --type=kubernetes.io/dockerconfigjson --from-file=.dockerconfigjson=$HOME/.docker/config.json -n istio-system )

 # set custom docker registry to gcr.io/istio-release to avoid 
 # docker.io/istio dependency.
 echo "Set the image registry to gcr.io/istio-release during istio installation"
( cd ${istiodir}
  bin/istioctl x precheck
  bin/istioctl install --set meshConfig.enablePrometheusMerge=false --set values.global.imagePullSecrets[0]=docker-istio-secret --set hub=gcr.io/istio-release --set components.cni.enabled=true --set profile=demo -y
  bin/istioctl verify-install
  bin/istioctl version
)
}

# MAIN
version=${1:-1.13.2}
workdir=${2:-`pwd`}

if [ ! -d ${workdir} ]; then 
  mkdir -p $workdir
fi

istiodir=${workdir}/istio-${version}
if [ -d ${istiodir} ]; then 
   echo "Istio version [${version}] alreday installed at [${istiodir}]"
   exit 0 
else 
   install_istio ${version} ${workdir}
   # Additional check for Istio Service. 
   # Make sure a not-null Service Port returned.
   HTTP2_PORT=$(${KUBERNETES_CLI} -n istio-system get service istio-ingressgateway -o jsonpath='{.spec.ports[?(@.name=="http2")].nodePort}')
   if [ -z ${HTTP2_PORT} ]; then 
     echo "Istio installation fails"
     echo "Istio Http2 NodePort Service is not listening"
     exit -1
   else 
     echo "Istio installation is SUCCESS"
     echo "Http2 NodePort Service is listening on port [${HTTP2_PORT}]"
     exit 0
   fi
fi
