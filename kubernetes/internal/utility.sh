#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Functions that are shared between the create-weblogic-domain.sh and create-weblogic-operator.sh scripts
#

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  echo [ERROR] $1
  exit 1
}

#
# Function to note that a validate error has occurred
#
function validationError {
  echo "[ERROR] $1"
  validateErrors=true
}

#
# Function to cause the script to fail if there were any validation errors
#
function failIfValidationErrors {
  if [ "$validateErrors" = true ]; then
    fail 'The errors listed above must be resolved before the script can continue'
  fi
}

#
# Function to validate that a list of required input parameters were specified
#
function validateInputParamsSpecified {
  for p in $*; do
    local name=$p
    local val=${!name}
    if [ -z $val ]; then
      validationError "The ${name} parameter in ${valuesInputFile} is missing, null or empty"
    fi
  done
}

#
# Function to validate a kubernetes secret exists
# $1 - the name of the secret
# $2 - namespace
function validateSecretExists {
  # Verify the secret exists
  echo "Checking to see if the secret ${1} exists in namespace ${2}"
  local SECRET=`kubectl get secret ${1} -n ${2} | grep ${1} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${1} was not found in namespace ${2}"
  fi
}

#
# Function to parse a yaml file and generate the bash exports
# $1 - Input filename
# $2 - Output filename
function parseYaml {
  local s='[[:space:]]*' w='[a-zA-Z0-9_]*' fs=$(echo @|tr @ '\034')
  sed -ne "s|^\($s\):|\1|" \
     -e "s|^\($s\)\($w\)$s:$s[\"']\(.*\)[\"']$s\$|\1$fs\2$fs\3|p" \
     -e "s|^\($s\)\($w\)$s:$s\(.*\)$s\$|\1$fs\2$fs\3|p"  $1 |
  awk -F$fs '{
    if (length($3) > 0) {
       printf("export %s=\"%s\"\n", $2, $3);
    }
  }' > $2
}

#
# Function to parse the common parameter inputs file
#
function parseCommonInputs {
  exportValuesFile="/tmp/export-values.sh"
  parseYaml ${valuesInputFile} ${exportValuesFile}

  if [ ! -f ${exportValuesFile} ]; then
    echo Unable to locate the parsed output of ${valuesInputFile}.
    fail 'The file ${exportValuesFile} could not be found.'
  fi

  # Define the environment variables that will be used to fill in template values
  echo Input parameters being used
  cat ${exportValuesFile}
  echo
  source ${exportValuesFile}
  rm ${exportValuesFile}
}

#
# Function to delete a kubernetes object
# $1 object type
# $2 object name
# $3 yaml file
function deleteK8sObj {
  # If the yaml file does not exist yet, unable to do the delete
  if [ ! -f $3 ]; then
    fail "Unable to delete object type $1 with name $2 because file $3 does not exist"
  fi

  echo Checking if object type $1 with name $2 exists
  K8SOBJ=`kubectl get $1 -n ${namespace} | grep $2 | wc | awk ' { print $1; }'`
  if [ "${K8SOBJ}" = "1" ]; then
    echo Deleting $2 using $3
    kubectl delete -f $3
  fi
}

#
# Function to lowercase a value
# $1 - value to convert to lowercase
function toLower {
  local lc=`echo $1 | tr "[:upper:]" "[:lower:]"`
  echo "$lc"
}

#
# Function to check if a value is lowercase
# $1 - value to check
# $2 - name of object being checked
function validateLowerCase {
  local lcVal=$(toLower $1)
  if [ "$lcVal" != "$1" ]; then
    validationError "The value of $2 must be lowercase: '$1' "
  fi
}

#
# Function to validate the namespace
#
function validateNamespace {
  validateLowerCase ${namespace} "namespace"
}

#
# Function to check if a persistent volume exists
# $1 - name of volume
function checkPvExists {

  echo "Checking if the persistent volume ${1} exists"
  PV_EXISTS=`kubectl get pv | grep ${1} | wc | awk ' { print $1; } '`
  if [ "${PV_EXISTS}" = "1" ]; then
    echo "The persistent volume ${1} already exists"
    PV_EXISTS="true"
  else
    echo "The persistent volume ${1} does not exist"
    PV_EXISTS="false"
  fi
}

#
# Function to check if a persistent volume claim exists
# $1 - name of persistent volume claim
# $2 - namespace
function checkPvcExists {
  echo "Checking if the persistent volume claim ${1} in namespace ${2} exists"
  PVC_EXISTS=`kubectl get pvc -n ${2} | grep ${1} | wc | awk ' { print $1; } '`
  if [ "${PVC_EXISTS}" = "1" ]; then
    echo "The persistent volume claim ${1} already exists in namespace ${2}"
    PVC_EXISTS="true"
  else
    echo "The persistent volume claim ${1} does not exist in namespace ${2}"
    PVC_EXISTS="false"
  fi
}

#
# Check the state of a persistent volume.
# $1 - name of volume
# $2 - expected state of volume
function checkPvState {

  echo "Checking if the persistent volume ${1:?} is ${2:?}"
  local pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
  attempts=0
  while [ ! "$pv_state" = "$2" ] && [ ! $attempts -eq 10 ]; do
    attempts=$((attempts + 1))
    sleep 1
    pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
  done
  if [ "$pv_state" != "$2" ]; then
    fail "The persistent volume state should be $2 but is $pv_state"
  fi
}
