#!/usr/bin/env bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
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

#
# Function to validate the weblogic image pull policy
#
function validateWeblogicImagePullPolicy {
  if [ ! -z ${imagePullPolicy} ]; then
    case ${imagePullPolicy} in
      "IfNotPresent")
      ;;
      "Always")
      ;;
      "Never")
      ;;
      *)
        validationError "Invalid value for imagePullPolicy: ${imagePullPolicy}. Valid values are IfNotPresent, Always, and Never."
      ;;
    esac
  else
    # Set the default
    imagePullPolicy="IfNotPresent"
  fi
  failIfValidationErrors
}

#
# Function to validate the weblogic image pull secret name
#
function validateWeblogicImagePullSecretName {
  if [ ! -z ${imagePullSecretName} ]; then
    validateLowerCase imagePullSecretName ${imagePullSecretName}
    imagePullSecretPrefix=""
    if [ "${generateOnly}" = false ]; then
      validateWeblogicImagePullSecret
    fi
  else
    # Set name blank when not specified, and comment out the yaml
    imagePullSecretName=""
    imagePullSecretPrefix="#"
  fi
}

#
# Function to validate the weblogic image pull secret exists
#
function validateWeblogicImagePullSecret {
  # The kubernetes secret for pulling images from the docker store is optional.
  # If it was specified, make sure it exists.
  validateSecretExists ${imagePullSecretName} ${namespace}
  failIfValidationErrors
}

# try to execute kubectl to see whether kubectl is available
function validateKubectlAvailable {
  if ! [ -x "$(command -v kubectl)" ]; then
    validationError "kubectl is not installed"
  fi
}

# Function to validate the server start policy value
#
function validateServerStartPolicy {
  validateInputParamsSpecified serverStartPolicy
  if [ ! -z "${serverStartPolicy}" ]; then
    case ${serverStartPolicy} in
      "NEVER")
      ;;
      "ALWAYS")
      ;;
      "IF_NEEDED")
      ;;
      "ADMIN_ONLY")
      ;;
      *)
        validationError "Invalid value for serverStartPolicy: ${serverStartPolicy}. Valid values are 'NEVER', 'ALWAYS', 'IF_NEEDED', and 'ADMIN_ONLY'."
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

#
# Function to validate a kubernetes secret exists
# $1 - the name of the secret
# $2 - namespace
function validateSecretExists {
  echo "Checking to see if the secret ${1} exists in namespace ${2}"
  local SECRET=`kubectl get secret ${1} -n ${2} | grep ${1} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The secret ${1} was not found in namespace ${2}"
  fi
}

#
# Function to validate the domain secret
#
function validateDomainSecret {
  # Verify the secret exists
  validateSecretExists ${weblogicCredentialsSecretName} ${namespace}
  failIfValidationErrors

  # Verify the secret contains a username
  SECRET=`kubectl get secret ${weblogicCredentialsSecretName} -n ${namespace} -o jsonpath='{.data}'| grep username: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${weblogicCredentialsSecretName} in namespace ${namespace} does contain a username"
  fi

  # Verify the secret contains a password
  SECRET=`kubectl get secret ${weblogicCredentialsSecretName} -n ${namespace} -o jsonpath='{.data}'| grep password: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${weblogicCredentialsSecretName} in namespace ${namespace} does contain a password"
  fi
  failIfValidationErrors
}

#
# Function to validate the common input parameters
#
function validateCommonInputs {
  # Parse the commonn inputs file
  parseCommonInputs

  validateInputParamsSpecified \
    adminServerName \
    domainUID \
    clusterName \
    managedServerNameBase \
    namespace \
    includeServerOutInPodLog \
    version

  validateIntegerInputParamsSpecified \
    adminPort \
    configuredManagedServerCount \
    initialManagedServerReplicas \
    managedServerPort \
    t3ChannelPort \
    adminNodePort

  validateBooleanInputParamsSpecified \
    productionModeEnabled \
    exposeAdminT3Channel \
    exposeAdminNodePort \
    includeServerOutInPodLog

  export requiredInputsVersion="create-weblogic-sample-domain-inputs-v1"
  validateVersion

  validateDomainUid
  validateNamespace
  validateAdminServerName
  validateManagedServerNameBase
  validateClusterName
  validateWeblogicCredentialsSecretName
  validateServerStartPolicy
  validateWeblogicImagePullPolicy
  validateWeblogicImagePullSecretName

  failIfValidationErrors
}

#
# Function to validate the domain's persistent volume claim has been created
#
function validateDomainPVC {
  # Check if the persistent volume claim is already available
  checkPvcExists ${persistentVolumeClaimName} ${namespace}
  if [ "${PVC_EXISTS}" = "false" ]; then
    validationError "The domain persistent volume claim ${persistentVolumeClaimName} does not exist in namespace ${namespace}"
  fi
  failIfValidationErrors
}

