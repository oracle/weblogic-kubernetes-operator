#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script generates the Kubernetes yaml files for a persistent volume and persistent volume claim
#  that can be used by a domain custom resource.
#
#  The creation inputs can be customized by editing create-pv-pvc-inputs.yaml
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
  echo usage: ${script} -i file -o dir [-e] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Output directory for the generated yaml files, must be specified."
  echo "  -e Also create the Kubernetes objects using the generated yaml files"
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
function initOutputDir {
  pvOutputDir="$outputDir/pv-pvcs"

  if [ -z ${domainUID} ]; then
    pvOutput="${pvOutputDir}/${baseName}-pv.yaml"
    pvcOutput="${pvOutputDir}/${baseName}-pvc.yaml"
    persistentVolumeName=${baseName}-pv
    persistentVolumeClaimName=${baseName}-pvc
  else
    pvOutput="${pvOutputDir}/${domainUID}-${baseName}-pv.yaml"
    pvcOutput="${pvOutputDir}/${domainUID}-${baseName}-pvc.yaml"
    persistentVolumeName=${domainUID}-${baseName}-pv
    persistentVolumeClaimName=${domainUID}-${baseName}-pvc
  fi
  removeFileIfExists ${pvOutputDir}/{valuesInputFile}
  removeFileIfExists ${pvOutputDir}/{pvOutput}
  removeFileIfExists ${pvOutputDir}/{pvcOutput}
  removeFileIfExists ${pvOutputDir}/create-pv-pvc-inputs.yaml
}

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml)."
  else
    if [ ! -f ${valuesInputFile} ]; then
      validationError "Unable to locate the input parameters file ${valuesInputFile}"
    fi
  fi

  if [ -z "${outputDir}" ]; then
    validationError "You must use the -o option to specify the name of an existing directory to store the generated yaml files in."
  fi

  domainPVInput="${scriptDir}/pv-template.yaml"
  if [ ! -f ${domainPVInput} ]; then
    validationError "The template file ${domainPVInput} for generating a persistent volume was not found"
  fi

  domainPVCInput="${scriptDir}/pvc-template.yaml"
  if [ ! -f ${domainPVCInput} ]; then
    validationError "The template file ${domainPVCInput} for generating a persistent volume claim was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs
  validateInputParamsSpecified \
    weblogicDomainStoragePath \
    weblogicDomainStorageSize \
    baseName \
    namespace \
    version

  export requiredInputsVersion="create-weblogic-sample-domain-pv-pvc-inputs-v1"
  validateDomainUid
  validateNamespace
  validateWeblogicDomainStorageType
  validateWeblogicDomainStorageReclaimPolicy
  initOutputDir
  failIfValidationErrors
}


#
# Function to generate the yaml files for creating a domain
#
function createYamlFiles {

  # Create a directory for this domain's output files
  mkdir -p ${pvOutputDir}

  # Make sure the output directory has a copy of the inputs file.
  # The user can either pre-create the output directory, put the inputs
  # file there, and create the domain from it, or the user can put the
  # inputs file some place else and let this script create the output directory
  # (if needed) and copy the inputs file there.
  copyInputsFileToOutputDirectory ${valuesInputFile} "${pvOutputDir}/create-pv-pvc-inputs.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  echo Generating ${pvOutput}

  cp ${domainPVInput} ${pvOutput}
  if [ "${weblogicDomainStorageType}" == "NFS" ]; then
    hostPathPrefix="${disabledPrefix}"
    nfsPrefix="${enabledPrefix}"
    sed -i -e "s:%SAMPLE_STORAGE_NFS_SERVER%:${weblogicDomainStorageNFSServer}:g" ${pvOutput}
  else
    hostPathPrefix="${enabledPrefix}"
    nfsPrefix="${disabledPrefix}"
  fi

  sed -i -e "s:%NAMESPACE%:$namespace:g" ${pvOutput}
  if [ -z ${domainUID} ]; then
    domainUIDLabelPrefix="${disabledPrefix}"
    separator=""
  else
    domainUIDLabelPrefix="${enabledPrefix}"
    separator="-"
  fi
  sed -i -e "s:%DOMAIN_UID%:$domainUID:g" ${pvOutput}
  sed -i -e "s:%SEPARATOR%:$separator:g" ${pvOutput}
  sed -i -e "s:%DOMAIN_UID_LABEL_PREFIX%:${domainUIDLabelPrefix}:g" ${pvOutput}

  sed -i -e "s:%BASE_NAME%:$baseName:g" ${pvOutput}
  sed -i -e "s:%SAMPLE_STORAGE_PATH%:${weblogicDomainStoragePath}:g" ${pvOutput}
  sed -i -e "s:%SAMPLE_STORAGE_RECLAIM_POLICY%:${weblogicDomainStorageReclaimPolicy}:g" ${pvOutput}
  sed -i -e "s:%SAMPLE_STORAGE_SIZE%:${weblogicDomainStorageSize}:g" ${pvOutput}
  sed -i -e "s:%HOST_PATH_PREFIX%:${hostPathPrefix}:g" ${pvOutput}
  sed -i -e "s:%NFS_PREFIX%:${nfsPrefix}:g" ${pvOutput}

  # Generate the yaml to create the persistent volume claim
  echo Generating ${pvcOutput}

  cp ${domainPVCInput} ${pvcOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${pvcOutput}
  sed -i -e "s:%BASE_NAME%:${baseName}:g" ${pvcOutput}

  sed -i -e "s:%DOMAIN_UID%:$domainUID:g" ${pvcOutput}
  sed -i -e "s:%SEPARATOR%:$separator:g" ${pvcOutput}
  sed -i -e "s:%DOMAIN_UID_LABEL_PREFIX%:${domainUIDLabelPrefix}:g" ${pvcOutput}

  sed -i -e "s:%SAMPLE_STORAGE_SIZE%:${weblogicDomainStorageSize}:g" ${pvcOutput}

  # Remove any "...yaml-e" files left over from running sed
  rm -f ${pvOutputDir}/*.yaml-e
}

#
# Function to create the domain's persistent volume
#
function createDomainPV {
  # Check if the persistent volume is already available
  checkPvExists ${persistentVolumeName}
  if [ "${PV_EXISTS}" = "false" ]; then
    echo Creating the persistent volume ${persistentVolumeName}
    kubectl create -f ${pvOutput}
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
  checkPvcExists ${persistentVolumeClaimName} ${namespace}
  if [ "${PVC_EXISTS}" = "false" ]; then
    echo Creating the persistent volume claim ${persistentVolumeClaimName}
    kubectl create -f ${pvcOutput}
    checkPvState ${persistentVolumeName} Bound
  fi
}

#
# Function to output to the console a summary of the work completed
#
function printSummary {
  echo "The following files were generated:"
  echo "  ${pvOutput}"
  echo "  ${pvcOutput}"
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


