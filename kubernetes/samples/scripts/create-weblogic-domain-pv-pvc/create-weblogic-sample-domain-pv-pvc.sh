#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script generates the Kubernetes yaml files for a persistent volume and persistent volume claim
#  that can be used by a domain custom resource.
#
#  The creation inputs can be customized by editing create-weblogic-sample-domain-pv-pvc-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The Kubernetes namespace must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh
source ${scriptDir}/../common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file [-e] [-h]
  echo "  -o Ouput directory for the generated yaml files, must be specified."
  echo "  -e Also create the resources in the generated yaml files"
  echo "  -i Parameter input file, must be specified."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
executeIt=false
while getopts "ehi:o:" opt; do
  case $opt in
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    e) executeIt=true
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

if [ -z ${outputDir} ]; then
  echo "${script}: -o must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

#
# Function to initialize and validate the output directory
# for the generated yaml files for this domain.
#
function initAndValidateOutputDir {
  domainOutputDir="${outputDir}/weblogic-domains/${domainUID}"

  validateOutputDir \
    ${domainOutputDir} \
    ${valuesInputFile} \
    create-weblogic-sample-domain-pv-pvc-inputs.yaml \
    weblogic-domain-pv.yaml \
    weblogic-domain-pvc.yaml 
}

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/create-weblogic-domain-inputs.yaml)."
  else
    if [ ! -f ${valuesInputFile} ]; then
      validationError "Unable to locate the input parameters file ${valuesInputFile}"
    fi
  fi

  if [ -z "${outputDir}" ]; then
    validationError "You must use the -o option to specify the name of an existing directory to store the generated yaml files in."
  else
    if ! [ -d ${outputDir} ]; then
      validationError "Unable to locate the directory ${outputDir}. \nThis is the name of the directory to store the generated yaml files in."
    fi
  fi

  domainPVInput="${scriptDir}/weblogic-sample-domain-pv-template.yaml"
  if [ ! -f ${domainPVInput} ]; then
    validationError "The template file ${domainPVInput} for generating a persistent volume was not found"
  fi

  domainPVCInput="${scriptDir}/weblogic-sample-domain-pvc-template.yaml"
  if [ ! -f ${domainPVCInput} ]; then
    validationError "The template file ${domainPVCInput} for generating a persistent volume claim was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs
  validateInputParamsSpecified \
    domainName \
    domainUID \
    weblogicDomainStoragePath \
    weblogicDomainStorageSize \
    namespace \
    version

  export requiredInputsVersion="create-weblogic-sample-domain-pv-pvc-inputs-v1"
  validateDomainUid
  validateNamespace
  validateWeblogicDomainStorageType
  validateWeblogicDomainStorageReclaimPolicy
  initAndValidateOutputDir
  failIfValidationErrors
}


#
# Function to generate the yaml files for creating a domain
#
function createYamlFiles {

  # Create a directory for this domain's output files
  mkdir -p ${domainOutputDir}

  # Make sure the output directory has a copy of the inputs file.
  # The user can either pre-create the output directory, put the inputs
  # file there, and create the domain from it, or the user can put the
  # inputs file some place else and let this script create the output directory
  # (if needed) and copy the inputs file there.
  copyInputsFileToOutputDirectory ${valuesInputFile} "${domainOutputDir}/create-weblogic-sample-domain-pv-pvc-inputs.yaml"

  domainPVOutput="${domainOutputDir}/weblogic-sample-domain-pv.yaml"
  domainPVCOutput="${domainOutputDir}/weblogic-sample-domain-pvc.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  echo Generating ${domainPVOutput}

  cp ${domainPVInput} ${domainPVOutput}
  if [ "${weblogicDomainStorageType}" == "NFS" ]; then
    hostPathPrefix="${disabledPrefix}"
    nfsPrefix="${enabledPrefix}"
    sed -i -e "s:%WEBLOGIC_DOMAIN_STORAGE_NFS_SERVER%:${weblogicDomainStorageNFSServer}:g" ${domainPVOutput}
  else
    hostPathPrefix="${enabledPrefix}"
    nfsPrefix="${disabledPrefix}"
  fi

  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${domainPVOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${domainPVOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${domainPVOutput}
  sed -i -e "s:%WEBLOGIC_DOMAIN_STORAGE_PATH%:${weblogicDomainStoragePath}:g" ${domainPVOutput}
  sed -i -e "s:%WEBLOGIC_DOMAIN_STORAGE_RECLAIM_POLICY%:${weblogicDomainStorageReclaimPolicy}:g" ${domainPVOutput}
  sed -i -e "s:%WEBLOGIC_DOMAIN_STORAGE_SIZE%:${weblogicDomainStorageSize}:g" ${domainPVOutput}
  sed -i -e "s:%HOST_PATH_PREFIX%:${hostPathPrefix}:g" ${domainPVOutput}
  sed -i -e "s:%NFS_PREFIX%:${nfsPrefix}:g" ${domainPVOutput}

  # Generate the yaml to create the persistent volume claim
  echo Generating ${domainPVCOutput}

  cp ${domainPVCInput} ${domainPVCOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${domainPVCOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${domainPVCOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${domainPVCOutput}
  sed -i -e "s:%WEBLOGIC_DOMAIN_STORAGE_SIZE%:${weblogicDomainStorageSize}:g" ${domainPVCOutput}

  # Remove any "...yaml-e" files left over from running sed
  rm -f ${domainOutputDir}/*.yaml-e
}

#
# Function to create the domain's persistent volume
#
function createDomainPV {
  # Check if the persistent volume is already available
  persistentVolumeName="${domainUID}-weblogic-domain-pv"
  checkPvExists ${persistentVolumeName}
  if [ "${PV_EXISTS}" = "false" ]; then
    echo Creating the persistent volume ${persistentVolumeName}
    kubectl create -f ${domainPVOutput}
    checkPvState ${persistentVolumeName} Available
  fi
}

#
# Function to create the domain's persistent volume claim
# Must be called after createDomainPV since it relies on
# createDomainPV defining persistentVolumeName
#
function createDomainPVC {
  # Check if the persistent volume claim is already available
  persistentVolumeClaimName="${domainUID}-weblogic-domain-pvc"
  checkPvcExists ${persistentVolumeClaimName} ${namespace}
  if [ "${PVC_EXISTS}" = "false" ]; then
    echo Creating the persistent volume claim ${persistentVolumeClaimName}
    kubectl create -f ${domainPVCOutput}
    checkPvState ${persistentVolumeName} Bound
  fi
}

#
# Function to output to the console a summary of the work completed
#
function printSummary {
  echo "The following files were generated:"
  echo "  ${domainPVOutput}"
  echo "  ${domainPVCOutput}"
}

#
# Perform the following sequence of steps to create a domain
#

# Setup the environment for running this script and perform initial validation checks
initialize

# Generate the yaml files for creating the domain
createYamlFiles

# All done if the generate only option is true
if [ "${executeIt}" = true ]; then

  # Create the domain's persistent volume
  createDomainPV

  # Create the domain's persistent volume claim
  createDomainPVC
fi

# Output a job summary
printSummary

echo 
echo Completed


