#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Description:
#
#  This script install a given version of istio using Helm v3.x
#  Default istio version is 1.7.3 
#  https://istio.io/docs/setup/install/istioctl/
#  https://istio.io/latest/docs/setup/install/standalone-operator/
#  https://github.com/istio/istio/releases
#  https://github.com/istio/istio/tags


# Usage:
#
#  $0 [istio-version] [install-dir]

# Define functions

function install_istio {

version=$1
workdir=$2

istiodir=${workdir}/istio-${version}
echo "Installing Istio version [${version}] in location [${istiodir}]"

kubectl delete namespace istio-system --ignore-not-found
# istio installation will create the namespace 'istio-system' 
# kubectl create namespace istio-system

( cd $workdir;  
  curl -L https://istio.io/downloadIstio | ISTIO_VERSION=${version} TARGET_ARCH=x86_64 sh -
)

( cd ${istiodir}
  bin/istioctl x precheck 
  bin/istioctl install --set profile=demo -y
  bin/istioctl verify-install
  bin/istioctl version
)
}

# MAIN
version=${1:-1.7.3}
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
   HTTP2_PORT=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.spec.ports[?(@.name=="http2")].nodePort}')
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
