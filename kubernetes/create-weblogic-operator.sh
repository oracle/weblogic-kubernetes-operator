#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$(dirname "${script}")" > /dev/null 2>&1 ; pwd -P)"
internalDir="${scriptDir}/internal"

# This is the script that the customers use to create an operator.
# It requires that the customer has a real environment that includes
# keytool, openssl, kubectl and kubernetes.

# pass the name of this script to the internal create script
createScript="${script}"

# pass the location of the default operator inputs yaml file to the internal create script
defaultOperatorInputsFile="${scriptDir}/create-weblogic-operator-inputs.yaml"

# use the script that uses keytool and openssl to generate certificates and keys
genOprCertScript="${internalDir}/generate-weblogic-operator-cert.sh"

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

# call the internal script to create the operator
source ${internalDir}/create-weblogic-operator.sh
