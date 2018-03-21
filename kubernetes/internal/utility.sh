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
  printError $*
  exit 1
}

#
# Function to note that a validate error has occurred
#
function validationError {
  printError $*
  validateErrors=true
}

# Function to print an error message
function printError {
  echo [ERROR] $*
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
# Function to validate that a list of input parameters have boolean values.
# It assumes that validateInputParamsSpecified will also be called for these params.
#
function validateBooleanInputParamsSpecified {
  validateInputParamsSpecified $*
  for p in $*; do
    local name=$p
    local val=${!name}
    if ! [ -z $val ]; then
      if [ "true" != "$val" ] && [ "false" != "$val" ]; then
        validationError "The value of $name must be true or false: $val"
      fi
    fi
  done
}

#
# Function to validate that a list of input parameters have integer values.
#
function validateIntegerInputParamsSpecified {
  validateInputParamsSpecified $*
  for p in $*; do
    local name=$p
    local val=${!name}
    if ! [ -z $val ]; then
      local intVal=""
      printf -v intVal '%d' "$val" 2>/dev/null
      if ! [ "${val}" == "${intVal}" ]; then
        validationError "The value of $name must be an integer: $val"
      fi
    fi
  done
}

#
# Function to validate a kubernetes secret exists
# $1 - the name of the secret
# $2 - namespace
function validateSecretExists {
  # delegate to a function supplied by the caller so that while unit testing,
  # where kubectl and kubernetes are not available, we can stub out this check
  validateThatSecretExists $*
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
  local lcVal=$(toLower $2)
  if [ "$lcVal" != "$2" ]; then
    validationError "The value of $1 must be lowercase: $2"
  fi
}

#
# Function to validate the namespace
#
function validateNamespace {
  validateLowerCase "namespace" ${namespace}
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

#
# Function to validate that either the output dir does not exist,
# or that if it does, it does not contain any generated yaml files
# and does not contain an inputs file that differs from the one
# the script is using
# $1   - the output directory to validate
# $2   - the name of the input file the create script is using
# $3   - the name of the input file that is put into the output directory
# $4-n - the names of the generated yaml files
function validateOutputDir {
  local dir=$1
  shift
  if [ -e ${dir} ]; then
    # the output directory already exists
    if [ -d ${dir} ]; then
      # the output directory is a directory
      local in1=$1
      shift
      local in2=${1}
      shift
      internalValidateInputsFileDoesNotExistOrIsTheSame ${dir} ${in1} ${in2}
      internalValidateGeneratedYamlFilesDoNotExist ${dir} $@
    else
      validationError "${dir} exists but is not a directory."
    fi
  fi
}

#
# Internal function to validate that the inputs file does not exist in the
# outputs directory or is the same as the inputs file the script is using
# $1 - the output directory to validate
# $2 - the name of the input file the create script is using
# $3 - the name of the input file that is put into the output directory
function internalValidateInputsFileDoesNotExistOrIsTheSame {
  local dir=$1
  local in1=$2
  local in2=$3
  local f="${dir}/${in2}"
  if [ -e ${f} ]; then
    if [ -f ${f} ]; then
      local differences=`diff -q ${f} ${in1}`
      if ! [ -z "${differences}" ]; then
        validationError "${f} is different than ${in1}"
      fi
    else
      validationError "${f} exists and is not a file."
    fi
  fi
}

#
# Internal unction to validate that the generated yaml files do not exist
# in the outputs directory
# $1 - the output directory to validate
# $2-n - the names of the generated yaml files
function internalValidateGeneratedYamlFilesDoNotExist {
  local dir=$1
  shift
  for var in "$@"; do
    local f="${dir}/${var}"
    if [ -e ${f} ]; then
      validationError "${f} exists."
    fi
  done
}

# Copy the inputs file from the command line into the output directory
# for the domain/operator unless the output directory already has an
# inputs file and the file is the same as the one from the commandline.
# $1 the inputs file from the command line
# $2 the file in the output directory that needs to be made the same as $1
function copyInputsFileToOutputDirectory {
  local from=$1
  local to=$2
  local doCopy="true"
  if [ -f "${to}" ]; then
    local difference=`diff ${from} ${to}`
    if [ -z "${difference}" ]; then
      # the output file already exists and is the same as the inputs file.
      # don't make a copy.
      doCopy="false"
    fi
  fi
  if [ "${doCopy}" = "true" ]; then
    cp ${from} ${to}
  fi
}
