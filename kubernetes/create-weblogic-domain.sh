#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Description
#  This script automates the creation of a WebLogic domain within a Kubernetes cluster.
#
#  The domain creation inputs can be customized by editing create-weblogic-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$(dirname "${script}")" > /dev/null 2>&1 ; pwd -P)"
internalDir="${scriptDir}/internal"

# This is the script that the customers use to create a domain.
# It requires that the customer has a real environment that includes
# kubernetes.

# pass the name of this script to the internal create script
createScript="${script}"

# try to execute kubectl to see whether kubectl is available
function validateKubectlAvailable {
  if ! [ -x "$(command -v kubectl)" ]; then
    validationError "kubectl is not installed"
  fi
}

# use kubectl to validate whether a secret has been registered with kubernetes
function validateThatSecretExists {
  # Verify the secret exists
  echo "Checking to see if the secret ${1} exists in namespace ${2}"
  local SECRET=`kubectl get secret ${1} -n ${2} | grep ${1} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The secret ${1} was not found in namespace ${2}"
  fi
}

# call the internal script to create the domain
source ${internalDir}/create-weblogic-domain.sh
