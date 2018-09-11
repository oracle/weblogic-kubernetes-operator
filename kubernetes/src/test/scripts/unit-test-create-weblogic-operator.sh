#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This is the script that the create operator unit tests call to create an operator.
# Since unit tests run in a stripped down environment that doesn't include keytool, openssl,
# kubectl or kubernetes, this script 'mocks' that functionality then calls an internal
# create operator script that generates the yaml files.
#
# Because of the mock behavior, the certificates and keys in the generated yaml files
# are not valid.  But that's fine for the unit tests since these yaml files are never
# used to actually run the operator.

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$(dirname "${script}")" > /dev/null 2>&1 ; pwd -P)"
kubernetesDir="${scriptDir}/../../../../kubernetes"
internalDir="${kubernetesDir}/internal"

# pass the name of this script to the internal create script
createScript="${script}"

# pass the location of the default operator inputs yaml file to the internal create script
defaultOperatorInputsFile="${kubernetesDir}/create-weblogic-operator-inputs.yaml"

# mock out certificate generation by pointing to a unit test version of the script
# that just writes out some predictable text to the cert/key files (instead of actually
# generating certs and keys)
genOprCertScript="${scriptDir}/unit-test-generate-weblogic-operator-cert.sh"

# mock out validating whether kubectl is available
function validateKubectlAvailable {
  # kubectl is not available while unit testing - that's ok
  : # need a no-op line - empty functions are not allowed in bash
}

# mock out validating whether a secret has been registered with the kubernetes cluster
function validateThatSecretExists {
  # kubectl and kubernetes are not available while unit testing
  # so, for testing purposes, treat the secret as not found if the
  # secret name starts with "notfound"
  if [[ ${1} == notfound* ]] ; then
    validationError "The secret ${1} was not found in namespace ${2}"
  fi
}

# call the internal script to create the operator
source ${kubernetesDir}/internal/create-weblogic-operator.sh
