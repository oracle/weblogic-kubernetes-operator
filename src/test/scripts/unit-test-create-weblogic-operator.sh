#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
kubernetesDir="${scriptDir}/../../../kubernetes"
internalDir="${kubernetesDir}/internal"

createOperatorScript="${script}"
defaultOperatorInputsFile="${kubernetesDir}/create-weblogic-operator-inputs.yaml"
genOprCertScript="${scriptDir}/unit-test-generate-weblogic-operator-cert.sh"

function validateKubectlAvailable {
  # kubectl is not available while unit testing - that's ok
  : # need a no-op line - empty functions are not allowed in bash
}

function validateThatSecretExists {
  # kubectl and kubernetes are not available while unit testing - that's ok
  : # need a no-op line - empty functions are not allowed in bash
}

source ${kubernetesDir}/internal/create-weblogic-operator.sh
