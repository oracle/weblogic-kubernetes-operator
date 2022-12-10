#!/usr/bin/env bash
# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  Common validation functions shared by all other scripts that process inputs properties.
#

#
# Function to note that a validate error has occurred
#
validationError() {
  printError $*
  validateErrors=true
}

#
# Function to cause the script to fail if there were any validation errors
#
failIfValidationErrors() {
  if [ "$validateErrors" = true ]; then
    fail 'The errors listed above must be resolved before the script can continue'
  fi
}

#
# Function to validate that a list of required input parameters were specified
#
validateInputParamsSpecified() {
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
validateBooleanInputParamsSpecified() {
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
validateIntegerInputParamsSpecified() {
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
validateLowerCase() {
  local lcVal=$(toLower $2)
  if [ "$lcVal" != "$2" ]; then
    validationError "The value of $1 must be lowercase: $2"
  fi
}

#
# Function to check if a value is a valid WLS domain name.
# must include only alphanumeric characters, hyphens (-)
# or underscore characters (_) and contain at least one letter
# but must start with an alphanumeric or underscore character.
#
# $1 - name of object being checked
# $2 - value to check
validateWlsDomainName() {
  echo "validateWlsDomainName called with $2"
  if ! [[ "$2" =~ ^[a-z_][a-z0-9_.-]*$ ]] ; then
    validationError "$1 with value of $2 is not a valid WebLogic domain name. "\
     "A valid WebLogic domain name must include only alphanumeric characters, hyphens (-) "\
     "or underscore characters (_) but must start with an alphanumeric or underscore character."
  else
    if ! [[ "$2" =~ ^.*[a-z0-9].*$ ]] ; then
      validationError "$1 with value of $2 is not a valid WebLogic domain name. "\
       "A valid WebLogic domain name must contain at least one alphanumeric character."
    fi
  fi
}

#
# Function to check if a value is lowercase and legal DNS name
# $1 - name of object being checked
# $2 - value to check
validateDNS1123LegalName() {
  local val=$(toDNS1123Legal $2)
  if [ "$val" != "$2" ]; then
    validationError "The value of $1 contains invalid charaters: $2"
  fi
}

#
# Function to validate the namespace
#
validateNamespace() {
  validateLowerCase "namespace" ${namespace}
}

#
# Function to validate the version of the inputs file
#
validateVersion() {
  local requiredVersion=${requiredInputsVersion}
  if [ "${version}" != "${requiredVersion}" ]; then
    validationError "Invalid version: \"${version}\".  Must be ${requiredVersion}."
  fi
}

#
# Function to ensure the domain uid is a legal DNS name
# Because the domain uid is also used as a WebLogic domain
# name, it must also be a valid WebLogic domain name.
#
validateDomainUid() {
  validateLowerCase "domainUID" "${domainUID}"
  validateDNS1123LegalName "domainUID" "${domainUID}"
  validateWlsDomainName "domainUID" "${domainUID}"
}

#
# Function to ensure the namespace is lowercase
#
validateNamespace() {
  validateLowerCase "namespace" ${namespace}
}

#
# Create an instance of clusterName to be used in cases where a legal DNS name is required.
#
validateClusterName() {
  clusterNameSVC=$(toDNS1123Legal $clusterName)
}

#
# Create an instance of adminServerName to be used in cases where a legal DNS name is required.
#
validateAdminServerName() {
  adminServerNameSVC=$(toDNS1123Legal $adminServerName)
}

#
# Create an instance of adminServerName to be used in cases where a legal DNS name is required.
#
validateManagedServerNameBase() {
  managedServerNameBaseSVC=$(toDNS1123Legal $managedServerNameBase)
}

#
# Function to validate the secret name
#
validateWeblogicCredentialsSecretName() {
  validateLowerCase "weblogicCredentialsSecretName" ${weblogicCredentialsSecretName}
}

#
# Function to validate the weblogic image pull policy
#
validateWeblogicImagePullPolicy() {
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
# Function to validate the fmwDomainType
#
validateFmwDomainType() {
  if [ ! -z ${fmwDomainType} ]; then
    case ${fmwDomainType} in
      "JRF")
      ;;
      "RestrictedJRF")
      ;;
      *)
        validationError "Invalid value for fmwDomainType: ${fmwDomainType}. Valid values are JRF or restrictedJRF."
      ;;
    esac
  else
    # Set the default
    fmwDomainType="JRF"
  fi
  failIfValidationErrors
}

#
# Function to validate the weblogic image pull secret name
#
validateWeblogicImagePullSecretName() {
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
validateWeblogicImagePullSecret() {
  # The kubernetes secret for pulling images from a container registry is optional.
  # If it was specified, make sure it exists.
  validateSecretExists ${imagePullSecretName} ${namespace}
  failIfValidationErrors
}

# try to execute ${KUBERNETES_CLI:-kubectl} to see whether ${KUBERNETES_CLI:-kubectl} is available
validateKubernetesCLIAvailable() {
  if ! [ -x "$(command -v ${KUBERNETES_CLI:-kubectl})" ]; then
    validationError "${KUBERNETES_CLI:-kubectl} is not installed"
  fi
}

# Function to validate the server start policy value
#
validateServerStartPolicy() {
  validateInputParamsSpecified serverStartPolicy
  if [ ! -z "${serverStartPolicy}" ]; then
    case ${serverStartPolicy} in
      "Never")
      ;;
      "Always")
      ;;
      "IfNeeded")
      ;;
      "AdminOnly")
      ;;
      *)
        validationError "Invalid value for serverStartPolicy: ${serverStartPolicy}. Valid values are 'Never', 'Always', 'IfNeeded', and 'AdminOnly'."
      ;;
    esac
  fi
}

#
# Function to validate the weblogic domain storage reclaim policy
#
validateWeblogicDomainStorageReclaimPolicy() {
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
validateWeblogicDomainStorageType() {
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
validateLoadBalancer() {
  validateInputParamsSpecified loadBalancer
  if [ ! -z "${loadBalancer}" ]; then
    case ${loadBalancer} in
      "TRAEFIK")
      ;;
      "APACHE")
      ;;
      "NONE")
      ;;
      *)
        validationError "Invalid value for loadBalancer: ${loadBalancer}. Valid values are APACHE, TRAEFIK and NONE."
      ;;
    esac
  fi
}

#
# Function to validate a kubernetes secret exists
# $1 - the name of the secret
# $2 - namespace
validateSecretExists() {
  echo "Checking to see if the secret ${1} exists in namespace ${2}"
  local SECRET=`${KUBERNETES_CLI:-kubectl} get secret ${1} -n ${2} | grep ${1} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The secret ${1} was not found in namespace ${2}"
  fi
}

#
# Function to validate the domain secret
#
validateDomainSecret() {
  # Verify the secret exists
  validateSecretExists ${weblogicCredentialsSecretName} ${namespace}
  failIfValidationErrors

  # Verify the secret contains a username
  SECRET=`${KUBERNETES_CLI:-kubectl} get secret ${weblogicCredentialsSecretName} -n ${namespace} -o jsonpath='{.data}' | tr -d '"' | grep username: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${weblogicCredentialsSecretName} in namespace ${namespace} does not contain a username"
  fi

  # Verify the secret contains a password
  SECRET=`${KUBERNETES_CLI:-kubectl} get secret ${weblogicCredentialsSecretName} -n ${namespace} -o jsonpath='{.data}' | tr -d '"'| grep password: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${weblogicCredentialsSecretName} in namespace ${namespace} does not contain a password"
  fi
  failIfValidationErrors
}

#
# function to validate if we will be using wdt or wlst to create the domain
#
validateDomainFilesDir() {
  useWdt=true
  if [ -z "${createDomainFilesDir}" ] || [ "${createDomainFilesDir}" == "wlst" ]; then
    useWdt=false
  fi
}

#
# Function to validate the common input parameters
#
validateCommonInputs() {
  sample_name=${1:-"other"}

  # Parse the common inputs file
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
    initialManagedServerReplicas \
    managedServerPort \
    t3ChannelPort \
    adminNodePort

  if [ ! "${sample_name}" == "fmw-domain-home-in-image" ]; then
    validateIntegerInputParamsSpecified configuredManagedServerCount
  fi

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
  validateFmwDomainType
  validateDomainFilesDir
  # Below three validate methods are used for MII integration testing
  validateWdtDomainType
  validateWdtModelFile
  validateWdtModelPropertiesFile

  failIfValidationErrors
}

#
# Function to validate the domain's persistent volume claim has been created
#
validateDomainPVC() {
  # Check if the persistent volume claim is already available
  checkPvcExists ${persistentVolumeClaimName} ${namespace}
  if [ "${PVC_EXISTS}" = "false" ]; then
    validationError "The domain persistent volume claim ${persistentVolumeClaimName} does not exist in namespace ${namespace}"
  fi
  failIfValidationErrors
}

#
# Function to validate the WDT model file exists
# used for MII integration testing
#
validateWdtModelFile() {
  # Check if the model file exists
  if [ ! -z $wdtModelFile ]; then
    if [ ! -f $wdtModelFile ]; then
      validationError "The WDT model file ${wdtModelFile} does not exist"
    fi
  fi
  failIfValidationErrors
}

#
# Function to validate the WDT model property file exists
# used for MII integration testing
#
validateWdtModelPropertiesFile() {
  # Check if the model property file exists
  if [ ! -z $wdtModelPropertiesFile ]; then
    if [ ! -f $wdtModelPropertiesFile ]; then
      validationError "The WDT model property file ${wdtModelPropertiesFile} does not exist"
    fi
  fi
  failIfValidationErrors
}

# Function to validate the wdtDomainType
# used for MII integration testing
validateWdtDomainType() {
  if [ ! -z ${wdtDomainType} ]; then
    case ${wdtDomainType} in
      "WLS")
      ;;
      "JRF")
      ;;
      "RestrictedJRF")
      ;;
      *)
        validationError "Invalid value for wdtDomainType: ${wdtDomainType}. Valid values are WLS or JRF or restrictedJRF."
      ;;
    esac
  else
    # Set the default
    wdtDomainType="WLS"
  fi
  failIfValidationErrors
}

