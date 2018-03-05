#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
internalDir="${scriptDir}/internal"

# abstract away the parts of the create script that are not available while unit testing:
# a) can't generate certificates because keytool is not available
# b) can't verify if a secret is registered with kubernetes because kubectl and kubernetes are not available

createOperatorScript="${script}"
defaultOperatorInputsFile="${scriptDir}/create-weblogic-operator-inputs.yaml"
genOprCertScript="${internalDir}/generate-weblogic-operator-cert.sh"

function validateKubectlAvailable {
  if ! [ -x "$(command -v kubectl)" ]; then
    validationError "kubectl is not installed"
  fi
}

function validateThatSecretExists {
  # Verify the secret exists
  echo "Checking to see if the secret ${1} exists in namespace ${2}"
  local SECRET=`kubectl get secret ${1} -n ${2} | grep ${1} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The secret ${1} was not found in namespace ${2}"
  fi
}

source ${internalDir}/create-weblogic-operator.sh
