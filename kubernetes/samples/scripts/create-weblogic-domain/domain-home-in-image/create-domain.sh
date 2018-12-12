#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home in docker image, and generates the domain resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../../common/utility.sh
source ${scriptDir}/../../common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file -u username -p password [-e] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Ouput directory for the generated properties and YAML files, must be specified."
  echo "  -u Username used in building the Docker image for WebLogic domain in image."
  echo "  -p Password used in building the Docker image for WebLogic domain in image."
  echo "  -e Also create the resources in the generated YAML files, optional."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
executeIt=false
while getopts "evhi:o:u:p:" opt; do
  case $opt in
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    e) executeIt=true
    ;;
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${valuesInputFile} ]; then
  echo "${script}: -i must be specified."
  missingRequiredOption="true"
fi

if [ -z ${username} ]; then
  echo "${script}: -u must be specified."
  missingRequiredOption="true"
fi

if [ -z ${password} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ -z ${outputDir} ]; then
  echo "${script}: -o must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

#
# Function to initialize and validate the output directory
# for the generated properties and yaml files for this domain.
#
function initAndValidateOutputDir {
  domainOutputDir="${outputDir}/weblogic-domains/${domainUID}"
  # Create a directory for this domain's output files
  mkdir -p ${domainOutputDir}

  validateOutputDir \
    ${domainOutputDir} \
    ${valuesInputFile} \
    create-domain-inputs.yaml \
    domain.properties \
    domain.yaml
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

# try to execute docker to see whether docker is available
function validateDockerAvailable {
  if ! [ -x "$(command -v docker)" ]; then
    validationError "docker is not installed"
  fi
}

#
# Function to setup the environment to create domain
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateDockerAvailable
  validateKubectlAvailable

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image/create-domain-inputs.yaml)."
  else
    if [ ! -f ${valuesInputFile} ]; then
      validationError "Unable to locate the input parameters file ${valuesInputFile}"
    fi
  fi

  if [ -z "${outputDir}" ]; then
    validationError "You must use the -o option to specify the name of an existing directory to store the generated properties and yaml files in."
  fi

  domainPropertiesInput="${scriptDir}/properties-template.properties"
  if [ ! -f ${domainPropertiesInput} ]; then
    validationError "The template file ${domainPropertiesInput} for creating a WebLogic domain was not found"
  fi

  dcrInput="${scriptDir}/domain-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain resource was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs

  validateInputParamsSpecified \
    adminServerName \
    domainUID \
    clusterName \
    managedServerNameBase \
    namespace \
    t3PublicAddress \
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
  initAndValidateOutputDir
  validateServerStartPolicy
  validateClusterType
  failIfValidationErrors

  getDockerSample
}

#
# Function to get the dependency docker sample
#
function getDockerSample {
  rm -rf {scriptDir}/docker-images
  git clone https://github.com/oracle/docker-images.git
}

#
# Function to generate the properties and yaml files for creating a domain
#
function createFiles {

  # Make sure the output directory has a copy of the inputs file.
  # The user can either pre-create the output directory, put the inputs
  # file there, and create the domain from it, or the user can put the
  # inputs file some place else and let this script create the output directory
  # (if needed) and copy the inputs file there.
  copyInputsFileToOutputDirectory ${valuesInputFile} "${domainOutputDir}/create-domain-inputs.yaml"

  domainPropertiesOutput="${domainOutputDir}/domain.properties"
  dcrOutput="${domainOutputDir}/domain.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  domainName=${domainUID}

  # Generate the properties file that will be used when creating the weblogic domain
  echo Generating ${domainPropertiesOutput}

  cp ${domainPropertiesInput} ${domainPropertiesOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${domainPropertiesOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${domainPropertiesOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${domainPropertiesOutput}
  sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${domainPropertiesOutput}
  sed -i -e "s:%MANAGED_SERVER_NAME_BASE%:${managedServerNameBase}:g" ${domainPropertiesOutput}
  sed -i -e "s:%CONFIGURED_MANAGED_SERVER_COUNT%:${configuredManagedServerCount}:g" ${domainPropertiesOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${domainPropertiesOutput}
  sed -i -e "s:%PRODUCTION_MODE_ENABLED%:${productionModeEnabled}:g" ${domainPropertiesOutput}
  sed -i -e "s:%CLUSTER_TYPE%:${clusterType}:g" ${domainPropertiesOutput}
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${domainPropertiesOutput}
  sed -i -e "s:%T3_CHANNEL_PORT%:${t3ChannelPort}:g" ${domainPropertiesOutput}
  sed -i -e "s:%T3_PUBLIC_ADDRESS%:${t3PublicAddress}:g" ${domainPropertiesOutput}

  # Generate the yaml to create the domain resource
  echo Generating ${dcrOutput}

  if [ "${exposeAdminT3Channel}" = true ]; then
    exposeAdminT3ChannelPrefix="${enabledPrefix}"
  else
    exposeAdminT3ChannelPrefix="${disabledPrefix}"
  fi

  if [ "${exposeAdminNodePort}" = true ]; then
    exposeAdminNodePortPrefix="${enabledPrefix}"
  else
    exposeAdminNodePortPrefix="${disabledPrefix}"
  fi

  domainHome="/u01/oracle/user_projects/domains/${domainName}"

  if [ -z "${weblogicCredentialsSecretName}" ]; then
    weblogicCredentialsSecretName="${domainUID}-weblogic-credentials"
  fi

  cp ${dcrInput} ${dcrOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_HOME%:${domainHome}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${dcrOutput}
  sed -i -e "s:%INCLUDE_SERVER_OUT_IN_POD_LOG%:${includeServerOutInPodLog}:g" ${dcrOutput}
  sed -i -e "s:%SERVER_START_POLICY%:${serverStartPolicy}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_ADMIN_PORT_PREFIX%:${exposeAdminNodePortPrefix}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_NODE_PORT%:${adminNodePort}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_T3_CHANNEL_PREFIX%:${exposeAdminT3ChannelPrefix}:g" ${dcrOutput}
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${dcrOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${dcrOutput}
  sed -i -e "s:%INITIAL_MANAGED_SERVER_REPLICAS%:${initialManagedServerReplicas}:g" ${dcrOutput}
 
  # Remove any "...yaml-e" files left over from running sed
  rm -f ${domainOutputDir}/*.yaml-e
}

#
# Function to build docker image and create WebLogic domain home
#
function createDomainHome {
  cp ${domainPropertiesOutput} ./docker-images/OracleWebLogic/samples/12213-domain-home-in-image/properties/docker_build

  cd docker-images/OracleWebLogic/samples/12213-domain-home-in-image

  sed -i -e "s|myuser|${username}|g" properties/docker_build/domain_security.properties
  sed -i -e "s|mypassword1|${password}|g" properties/docker_build/domain_security.properties

  ./build.sh

  if [ "$?" != "0" ]; then
    fail "Create domain ${domainName} failed."
  fi

  cd -
  echo ""
  echo "Create domain ${domainName} successfully."
}

#
# Function to output to the console a summary of the work completed
#
function printSummary {

  # Get the IP address of the kubernetes cluster (into K8S_IP)
  getKubernetesClusterIP

  echo ""
  echo "Domain ${domainName} was created and will be started by the WebLogic Kubernetes Operator"
  echo ""
  if [ "${exposeAdminNodePort}" = true ]; then
    echo "Administration console access is available at http:${K8S_IP}:${adminNodePort}/console"
  fi
  if [ "${exposeAdminT3Channel}" = true ]; then
    echo "T3 access is available at t3:${K8S_IP}:${t3ChannelPort}"
  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-domain-inputs.yaml"
  echo "  ${domainPropertiesOutput}"
  echo "  ${dcrOutput}"
}

#
# Function to create the domain resource
#
function createDomainResource {
  pwd
  kubectl apply -f ${dcrOutput}
  DCR_AVAIL=`kubectl get domain -n ${namespace} | grep ${domainUID} | wc | awk ' { print $1; } '`
  if [ "${DCR_AVAIL}" != "1" ]; then
    fail "The domain resource ${domainUID} was not found"
  fi
}

#
# Perform the following sequence of steps to create a domain
#

# Setup the environment for running this script and perform initial validation checks
initialize

# Generate the properties and yaml files for creating the domain
createFiles

# Check that the domain secret exists and contains the required elements
validateDomainSecret

# Create the WebLogic domain
createDomainHome

if [ "${executeIt}" = true ]; then
  createDomainResource
fi

# Print a summary
printSummary

echo 
echo Completed

