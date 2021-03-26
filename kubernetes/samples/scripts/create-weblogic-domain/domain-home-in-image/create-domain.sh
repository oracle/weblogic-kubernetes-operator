#!/usr/bin/env bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home in docker image, and generates the domain resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The WebLogic Image Tool (WIT) requires that JAVA_HOME is set to a java JDK version 8 or greater
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#    * If logHomeOnPV is enabled, the kubernetes persistent volume must already be created
#    * If logHomeOnPV is enabled, the kubernetes persistent volume claim must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../../common/utility.sh
source ${scriptDir}/../../common/wdt-utility.sh
source ${scriptDir}/../../common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file -u username -p password [-s] [-k] [-e] [-v] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Output directory for the generated properties and YAML files, must be specified."
  echo "  -u Username used in building the image for WebLogic domain in image."
  echo "  -p Password used in building the image for WebLogic domain in image."
  echo "  -e Also create the resources in the generated YAML files, optional."
  echo "  -v Validate the existence of persistentVolumeClaim, optional."
  echo "  -k Keep what has been previously from cloned https://github.com/oracle/docker-images.git, optional. "
  echo "     If not specified, this script will always remove existing project directory and clone again."
  echo "  -s Skip the domain image build, optional. "
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
doValidation=false
executeIt=false
cloneIt=true
skipImageBuild=false
while getopts "evhksi:o:u:p:" opt; do
  case $opt in
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    v) doValidation=true
    ;;
    e) executeIt=true
    ;;
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    k) cloneIt=false;
    ;;
    s) skipImageBuild=true;
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
function initOutputDir {
  domainOutputDir="${outputDir}/weblogic-domains/${domainUID}"
  # Create a directory for this domain's output files
  mkdir -p ${domainOutputDir}

  removeFileIfExists ${domainOutputDir}/${valuesInputFile}
  removeFileIfExists ${domainOutputDir}/create-domain-inputs.yaml
  removeFileIfExists ${domainOutputDir}/domain.properties
  removeFileIfExists ${domainOutputDir}/domain.yaml
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

  dcrInput="${scriptDir}/../../common/domain-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain resource was not found"
  fi

  failIfValidationErrors

  validateCommonInputs

  validateBooleanInputParamsSpecified logHomeOnPV
  failIfValidationErrors

  initOutputDir

  export WDT_DIR=${toolsDir:-"/tmp/dhii-sample/tools"}
  export WIT_DIR="${WDT_DIR}"

  export WDT_VERSION=${wdtVersion:-LATEST}
  export WIT_VERSION=${witVersion:-LATEST}

  install_wit_if_needed || exit 1
}

#
# Function to build docker image and create WebLogic domain home
# Image build is skipped when -s option is specified OR image is not available
# e.g. If -s option is specified script will skip the image build only when 
# image is available else build the image
# If -s option is NOT specified script will ALWAYS build the image
function createDomainHome {

  if [ "${skipImageBuild}" = false ] || [ -z "$(docker images $image | grep -v TAG)" ]; then
    echo "WIT_DIR is ${WIT_DIR}"

    domainPropertiesOutput="${domainOutputDir}/domain.properties"
    domainHome="/u01/oracle/user_projects/domains/${domainName}"

    echo "dumping output of ${domainPropertiesOutput}"
    cat ${domainPropertiesOutput}

    echo "Invoking WebLogic Image Tool to create a WebLogic domain at '${domainHome}' from image '${domainHomeImageBase}' and tagging the resulting image as '${BUILD_IMAGE_TAG}'."

    $WIT_DIR/imagetool/bin/imagetool.sh update \
      --fromImage "$domainHomeImageBase" \
      --tag "${BUILD_IMAGE_TAG}" \
      --wdtModel ${scriptDir}/wdt_model_dynamic.yaml \
      --wdtVariables "${domainPropertiesOutput}" \
      --wdtOperation CREATE \
      --wdtVersion ${WDT_VERSION} \
      --wdtDomainHome $domainHome --chown=oracle:root

  if [ "$?" != "0" ]; then
    fail "Create domain ${domainName} failed."
  fi

  # clean up the generated domain.properties file
  rm ${domainPropertiesOutput}

  echo ""
  echo "Create domain ${domainName} successfully."

 else 
  echo ""
  echo "Skipping domain image build "
 fi 

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
    echo "Administration console access is available at http://${K8S_IP}:${adminNodePort}/console"
  fi
  if [ "${exposeAdminT3Channel}" = true ]; then
    echo "T3 access is available at t3://${K8S_IP}:${t3ChannelPort}"
  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-domain-inputs.yaml"
  echo "  ${dcrOutput}"
  echo ""
  echo "Completed"
}

# Perform the sequence of steps to create a domain
createDomain true

