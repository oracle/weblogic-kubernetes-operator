#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  Common validation functions shared by all other scripts that process inputs properties.
#

#
# Function to note that a validate error has occurred
#
function validationError {
  printError $*
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
    if [ -z "$val" ]; then
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
# Function to check if a value is lowercase
# $1 - name of object being checked
# $2 - value to check
function validateLowerCase {
  local lcVal=$(toLower $2)
  if [ "$lcVal" != "$2" ]; then
    validationError "The value of $1 must be lowercase: $2"
  fi
}

#
# Function to check if a value is lowercase and legal DNS name
# $1 - name of object being checked
# $2 - value to check
function validateDNS1123LegalName {
  local val=$(toDNS1123Legal $2)
  if [ "$val" != "$2" ]; then
    validationError "The value of $1 contains invalid charaters: $2"
  fi
}

#
# Function to validate the namespace
#
function validateNamespace {
  validateLowerCase "namespace" ${namespace}
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

#
# Function to validate the version of the inputs file
#
function validateVersion {
  local requiredVersion=${requiredInputsVersion}
  if [ "${version}" != "${requiredVersion}" ]; then
    validationError "Invalid version: \"${version}\".  Must be ${requiredVersion}."
  fi
}

#
# Function to ensure the domain uid is a legal DNS name
#
function validateDomainUid {
  validateLowerCase "domainUID" ${domainUID}
  validateDNS1123LegalName domainUID ${domainUID}
}

#
# Function to ensure the namespace is lowercase
#
function validateNamespace {
  validateLowerCase "namespace" ${namespace}
}

#
# Create an instance of clusterName to be used in cases where a legal DNS name is required.
#
function validateClusterName {
  clusterNameSVC=$(toDNS1123Legal $clusterName)
}

#
# Create an instance of adminServerName to be used in cases where a legal DNS name is required.
#
function validateAdminServerName {
  adminServerNameSVC=$(toDNS1123Legal $adminServerName)
}

#
# Create an instance of adminServerName to be used in cases where a legal DNS name is required.
#
function validateManagedServerNameBase {
  managedServerNameBaseSVC=$(toDNS1123Legal $managedServerNameBase)
}

#
# Function to validate the secret name
#
function validateWeblogicCredentialsSecretName {
  validateLowerCase "weblogicCredentialsSecretName" ${weblogicCredentialsSecretName}
}

# try to execute kubectl to see whether kubectl is available
function validateKubectlAvailable {
  if ! [ -x "$(command -v kubectl)" ]; then
    validationError "kubectl is not installed"
  fi
}

# Function to validate the server startup control value
#
function validateStartupControl {
  validateInputParamsSpecified startupControl
  if [ ! -z "${startupControl}" ]; then
    case ${startupControl} in
      "NONE")
      ;;
      "ALL")
      ;;
      "ADMIN")
      ;;
      "SPECIFIED")
      ;;
      "AUTO")
      ;;
      *)
        validationError "Invalid value for startupControl: ${startupControl}. Valid values are 'NONE', 'ALL', 'ADMIN', 'SPECIFIED', and 'AUTO'."
      ;;
    esac
  fi
}

#
# Function to validate the cluster type value
#
function validateClusterType {
  validateInputParamsSpecified clusterType
  if [ ! -z "${clusterType}" ]; then
    case ${clusterType} in
      "CONFIGURED")
      ;;
      "DYNAMIC")
      ;;
      *)
        validationError "Invalid value for clusterType: ${clusterType}. Valid values are 'CONFIGURED' and 'DYNAMIC'."
      ;;
    esac
  fi
}

#
# Function to validate the weblogic domain storage reclaim policy
#
function validateWeblogicDomainStorageReclaimPolicy {
  validateInputParamsSpecified weblogicDomainStorageReclaimPolicy
  if [ ! -z "${weblogicDomainStorageReclaimPolicy}" ]; then
    case ${weblogicDomainStorageReclaimPolicy} in
      "Retain")
      ;;
      "Delete")
        if [ "${weblogicDomainStoragePath:0:5}" != "/tmp/" ]; then
          validationError "ERROR - Invalid value for weblogicDomainStorageReclaimPolicy ${weblogicDomainStorageReclaimPolicy} with weblogicDomainStoragePath ${weblogicDomainStoragePath} that is not /tmp/"
        fi
      ;;
      "Recycle")
      ;;
      *)
        validationError "Invalid value for weblogicDomainStorageReclaimPolicy: ${weblogicDomainStorageReclaimPolicy}. Valid values are Retain, Delete and Recycle."
      ;;
    esac
  fi
}

#
# Function to validate the weblogic domain storage type
#
function validateWeblogicDomainStorageType {
  validateInputParamsSpecified weblogicDomainStorageType
  if [ ! -z "${weblogicDomainStorageType}" ]; then
    case ${weblogicDomainStorageType} in
      "HOST_PATH")
      ;;
      "NFS")
        validateInputParamsSpecified weblogicDomainStorageNFSServer
      ;;
      *)
        validationError "Invalid value for weblogicDomainStorageType: ${weblogicDomainStorageType}. Valid values are HOST_PATH and NFS."
      ;;
    esac
  fi
}

#
# Function to validate the load balancer value
#
function validateLoadBalancer {
  validateInputParamsSpecified loadBalancer
  if [ ! -z "${loadBalancer}" ]; then
    case ${loadBalancer} in
      "TRAEFIK")
      ;;
      "APACHE")
      ;;
      "VOYAGER")
      ;;
      "NONE")
      ;;
      *)
        validationError "Invalid value for loadBalancer: ${loadBalancer}. Valid values are APACHE, TRAEFIK, VOYAGER and NONE."
      ;;
    esac
  fi
}


