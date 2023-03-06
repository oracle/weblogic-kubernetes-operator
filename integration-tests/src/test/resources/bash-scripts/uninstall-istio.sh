#!/bin/bash
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Description:
#
#  This script uninstall a given version of istio using Helm v3.x
#  Default istio version is 1.13.2
#  https://istio.io/docs/setup/install/istioctl/
#  https://istio.io/latest/docs/setup/install/standalone-operator/

# Usage:
#
#  $0 [istio-version] [install-dir]

# Define functions

uninstall_istio() {

version=$1
workdir=$2

istiodir=${workdir}/istio-${version}
echo "Uninstalling Istio version [${version}] from location [${istiodir}]"
( cd ${istiodir}
  bin/istioctl x uninstall --purge -y
)
cd ${workdir}
rm -rf ${istiodir}
}

# MAIN
version=${1:-1.13.2}
workdir=${2:-`pwd`}

istiodir=${workdir}/istio-${version}

if [ ! -d ${istiodir} ]; then
 echo "Istio version [${version}] is NOT installed at location [${istiodir}]"
 exit 0
fi
   uninstall_istio ${version} ${workdir}
   exit 0 
fi

