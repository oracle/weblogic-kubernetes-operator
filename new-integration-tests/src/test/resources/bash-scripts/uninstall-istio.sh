#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Description:
#
#  This script uninstall a given version of istio using Helm v3.x
#  Default istio version is 1.5.4 
#  https://istio.io/docs/setup/install/istioctl/
#  https://istio.io/latest/docs/setup/install/standalone-operator/

# Usage:
#
#  $0 [istio-version] [install-dir]

# Define functions

function uninstall_istio {

version=$1
workdir=$2

istiodir=${workdir}/istio-${version}

echo "Uninstalling Istio version [${version}] from location [${istiodir}]"

( cd ${istiodir}
  helm template istio-init install/kubernetes/helm/istio-init --namespace istio-system | kubectl delete -f -
  kubectl -n istio-system wait --for=condition=complete job --all
  helm template istio install/kubernetes/helm/istio --namespace istio-system | kubectl delete -f -
)
kubectl delete namespace istio-system --ignore-not-found
rm -rf ${istiodir}
}

# MAIN
version=${1:-1.5.4}
workdir=${2:-`pwd`}

istiodir=${workdir}/istio-${version}

if [ ! -d ${istiodir} ]; then
 echo "Istio version [${version}] is NOT installed at location [${istiodir}]"
 exit 0
fi
   uninstall_istio ${version} ${workdir}
   exit 0 
fi

