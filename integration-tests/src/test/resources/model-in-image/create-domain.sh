#!/usr/bin/env bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home in docker image, and generates the domain resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The WDT sample requires that JAVA_HOME is set to a java JDK version 1.8 or greater
#    * The Kubernetes namespace must already be created
#    * The Kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#    * If logHomeOnPV is enabled, the Kubernetes persistent volume must already be created
#    * If logHomeOnPV is enabled, the Kubernetes persistent volume claim must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../scripts/common/utility.sh
source ${scriptDir}/../scripts/common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file -u username -p password [-k] [-e] [-v] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Output directory for the generated properties and YAML files, must be specified."
  echo "  -u Username used in building the Docker image for WebLogic domain in image."
  echo "  -p Password used in building the Docker image for WebLogic domain in image."
  echo "  -e Also create the resources in the generated YAML files, optional."
  echo "  -v Validate the existence of persistentVolumeClaim, optional."
  echo "  -k Keep what has been previously from cloned https://github.com/oracle/docker-images.git, optional. "
  echo "     If not specified, this script will always remove existing project directory and clone again."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
doValidation=false
executeIt=false
downloadTools=true
#downloadTools=false
while getopts "evhki:o:u:p:" opt; do
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
    k) downloadTools=false;
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
  miiWorkDir="${domainOutputDir}/miiWorkDir"
  modelDir="${miiWorkDir}/models"
  # Create a directory for this domain's output files and model files
  mkdir -p ${modelDir}

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

  dcrInput="${scriptDir}/../scripts/common/domain-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain resource was not found"
  fi

  failIfValidationErrors

  validateCommonInputs

  validateBooleanInputParamsSpecified logHomeOnPV
  failIfValidationErrors

  initOutputDir

  if [ "${downloadTools}" = true ] ; then
    downloadTool oracle/weblogic-deploy-tooling ${miiWorkDir}/weblogic-deploy-tooling.zip
    downloadTool oracle/weblogic-image-tool ${miiWorkDir}/weblogic-image-tool.zip
  fi
}

#
# Function to get the dependency tools
#
function downloadTool {
  toolLocation=$1
  downloadTo=$2
  downloadlink=$(curl -sL https://github.com/$1/releases/latest |
    grep "/$1/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
  echo "@@ Downloading latest '$toolLocation' to '$downloadTo' from 'https://github.com$downloadlink'."
  curl -L  https://github.com$downloadlink -o $downloadTo
}


#
# Function to build model in image
#
function createDomainHome {

  buildApp
  cd ${scriptDir}
  cp add_os_utils ${miiWorkDir}
  cp ${wdtModelFile} ${modelDir}/model.yaml
  #if [ "$?" != "0" ]; then
   # fail "Copy of model file ${wdtModelFile} failed."
  #fi
  cp ${wdtModelPropertiesFile} ${modelDir}/model.properties
  #if [ "$?" != "0" ]; then
  #  fail "Copy of model properties file ${wdtModelPropertiesFile} failed."
  #fi

  cd ${miiWorkDir}
  echo @@ Info: Setting up imagetool and populating its caches

  mkdir -p cache
  unzip -o weblogic-image-tool.zip

  IMGTOOL_BIN=${miiWorkDir}/imagetool/bin/imagetool.sh

  # The image tool uses the WLSIMG_CACHEDIR and WLSIMG_BLDIR env vars:
  WLSIMG_CACHEDIR=${WORKDIR}/cache
  WLSIMG_BLDDIR=${WORKDIR}

  imagetool/bin/imagetool.sh cache deleteEntry --key wdt_latest
  imagetool/bin/imagetool.sh cache addInstaller \
    --type wdt --version latest --path weblogic-deploy-tooling.zip

  echo "Info: Starting model image build for '${image}'"


  #
  # Run the image tool to create the image. It will implicitly use the latest WDT binaries
  # in the local image tool cache marked with key 'wdt_latest' (see 'cache' commands above). To
  # use a different version of WDT, specify '--wdtVersion'.
  #

  set -x
  imagetool/bin/imagetool.sh update \
    --tag ${image} \
    --fromImage ${domainHomeImageBase} \
    --wdtModel models/model.yaml \
    --wdtVariables models/model.properties \
    --wdtArchive models/archive.zip \
    --wdtModelOnly \
    --wdtDomainType ${wdtDomainType} \
    --additionalBuildCommands add_os_utils

  if [ "$?" != "0" ]; then
    fail "Create model in image failed."
  fi

  # clean up the generated domain.properties file
  rm ${domainPropertiesOutput}

  echo ""
  echo "Create domain ${domainName} successfully."
}

#
# Creating model archive
#

function buildApp {
    cp -r ${scriptDir}/sample_app ${miiWorkDir}
    cd ${miiWorkDir}/sample_app/wlsdeploy/applications
    rm -f sample_app.ear
    jar cvfM sample_app.ear *

    cd ../..
    rm -f ${modelDir}/archive.zip
    pwd
    zip ../models/archive.zip wlsdeploy/applications/sample_app.ear wlsdeploy/config/amimemappings.properties

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

