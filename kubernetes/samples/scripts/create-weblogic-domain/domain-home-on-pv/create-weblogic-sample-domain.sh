#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home on an existing PV/PVC, and generates the domain custom resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-weblogic-sample-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#    * The kubernetes persisitent volume must already be created
#    * The kubernetes persisitent volume claim must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../../common/utility.sh
source ${scriptDir}/../../common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file [-e] [-v] [-h]
  echo "  -o Ouput directory for the generated yaml files, must be specified."
  echo "  -i Parameter input file, must be specified."
  echo "  -e Also create the resources in the generated yaml files"
  echo "  -v Validate the existence of persistentVolumeClaim, optional."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
doValidation=false
executeIt=false
while getopts "evhi:o:" opt; do
  case $opt in
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    v) doValidation=true
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
    create-weblogic-sample-domain-inputs.yaml \
    create-weblogic-sample-domain-job.yaml \
    delete-weblogic-sample-domain-job.yaml \
    domain-custom-resource.yaml
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
# Function to validate the weblogic image pull secret name
#
function validateWeblogicImagePullSecretName {
  if [ ! -z ${weblogicImagePullSecretName} ]; then
    validateLowerCase weblogicImagePullSecretName ${weblogicImagePullSecretName}
    weblogicImagePullSecretPrefix=""
    if [ "${generateOnly}" = false ]; then
      validateWeblogicImagePullSecret
    fi
  else
    # Set name blank when not specified, and comment out the yaml
    weblogicImagePullSecretName=""
    weblogicImagePullSecretPrefix="#"
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
# Function to validate the weblogic image pull secret exists
#
function validateWeblogicImagePullSecret {
  # The kubernetes secret for pulling images from the docker store is optional.
  # If it was specified, make sure it exists.
  validateSecretExists ${weblogicImagePullSecretName} ${namespace}
  failIfValidationErrors
}

#
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

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateKubectlAvailable

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/samples/create-weblogic-sample-domain-inputs.yaml)."
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

  createJobInput="${scriptDir}/create-weblogic-sample-domain-job-template.yaml"
  if [ ! -f ${createJobInput} ]; then
    validationError "The template file ${createJobInput} for creating a WebLogic domain was not found"
  fi

  deleteJobInput="${scriptDir}/delete-weblogic-sample-domain-job-template.yaml"
  if [ ! -f ${deleteJobInput} ]; then
    validationError "The template file ${deleteJobInput} for deleting a WebLogic domain_home folder was not found"
  fi

  dcrInput="${scriptDir}/domain-custom-resource-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain custom resource was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs
  validateInputParamsSpecified \
    adminServerName \
    domainName \
    domainUID \
    clusterName \
    managedServerNameBase \
    weblogicCredentialsSecretName \
    namespace \
    t3PublicAddress \
    version \
    persistentVolumeClaimName \
    podDomainRootDir

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
    exposeAdminNodePort

  export requiredInputsVersion="create-weblogic-sample-domain-inputs-v1"
  validateVersion 

  validateDomainUid
  validateNamespace
  validateAdminServerName
  validateManagedServerNameBase
  validateClusterName
  validateWeblogicCredentialsSecretName
  validateWeblogicImagePullSecretName
  initAndValidateOutputDir
  validateStartupControl
  validateClusterType
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
  copyInputsFileToOutputDirectory ${valuesInputFile} "${domainOutputDir}/create-weblogic-sample-domain-inputs.yaml"

  createJobOutput="${domainOutputDir}/create-weblogic-sample-domain-job.yaml"
  deleteJobOutput="${domainOutputDir}/delete-weblogic-sample-domain-job.yaml"
  dcrOutput="${domainOutputDir}/domain-custom-resource.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  # For backward compatability, default to "store/oracle/weblogic:12.2.1.3" if not defined in
  # create-weblogic-sample-domain-inputs.yaml
  if [ -z "${weblogicImage}" ]; then
    weblogicImage="store/oracle/weblogic:12.2.1.3"
  fi
  # Must escape the ':' value in weblogicImage for sed to properly parse and replace
  weblogicImage=$(echo ${weblogicImage} | sed -e "s/\:/\\\:/g")

  # Generate the yaml to create the kubernetes job that will create the weblogic domain
  echo Generating ${createJobOutput}

  cp ${createJobInput} ${createJobOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${createJobOutput}
  sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${createJobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE%:${weblogicImage}:g" ${createJobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${weblogicImagePullSecretName}:g" ${createJobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${weblogicImagePullSecretPrefix}:g" ${createJobOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${createJobOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${createJobOutput}
  sed -i -e "s:%PRODUCTION_MODE_ENABLED%:${productionModeEnabled}:g" ${createJobOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${createJobOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME_SVC%:${adminServerNameSVC}:g" ${createJobOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${createJobOutput}
  sed -i -e "s:%CONFIGURED_MANAGED_SERVER_COUNT%:${configuredManagedServerCount}:g" ${createJobOutput}
  sed -i -e "s:%MANAGED_SERVER_NAME_BASE%:${managedServerNameBase}:g" ${createJobOutput}
  sed -i -e "s:%MANAGED_SERVER_NAME_BASE_SVC%:${managedServerNameBaseSVC}:g" ${createJobOutput}
  sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${createJobOutput}
  sed -i -e "s:%T3_CHANNEL_PORT%:${t3ChannelPort}:g" ${createJobOutput}
  sed -i -e "s:%T3_PUBLIC_ADDRESS%:${t3PublicAddress}:g" ${createJobOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${createJobOutput}
  sed -i -e "s:%CLUSTER_TYPE%:${clusterType}:g" ${createJobOutput}
  sed -i -e "s:%DOMAIN_PVC_NAME%:${persistentVolumeClaimName}:g" ${createJobOutput}
  sed -i -e "s:%DOMAIN_ROOT_DIR%:${podDomainRootDir}:g" ${createJobOutput}

  # Generate the yaml to create the kubernetes job that will delete the weblogic domain_home folder
  echo Generating ${deleteJobOutput}

  cp ${deleteJobInput} ${deleteJobOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${deleteJobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE%:${weblogicImage}:g" ${deleteJobOutput}
  sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${deleteJobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${weblogicImagePullSecretName}:g" ${deleteJobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${weblogicImagePullSecretPrefix}:g" ${deleteJobOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${deleteJobOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${deleteJobOutput}
  sed -i -e "s:%DOMAIN_PVC_NAME%:${persistentVolumeClaimName}:g" ${deleteJobOutput}
  sed -i -e "s:%DOMAIN_ROOT_DIR%:${podDomainRootDir}:g" ${deleteJobOutput}

  # Generate the yaml to create the domain custom resource
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

  cp ${dcrInput} ${dcrOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE%:${weblogicImage}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${weblogicImagePullSecretName}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${dcrOutput}
  sed -i -e "s:%INITIAL_MANAGED_SERVER_REPLICAS%:${initialManagedServerReplicas}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_T3_CHANNEL_PREFIX%:${exposeAdminT3ChannelPrefix}:g" ${dcrOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_ADMIN_PORT_PREFIX%:${exposeAdminNodePortPrefix}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_NODE_PORT%:${adminNodePort}:g" ${dcrOutput}
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${dcrOutput}
  sed -i -e "s:%STARTUP_CONTROL%:${startupControl}:g" ${dcrOutput}

  # Remove any "...yaml-e" files left over from running sed
  rm -f ${domainOutputDir}/*.yaml-e
}

#
# Function to run the job that creates the domain
#
function createDomainHome {

  # There is no way to re-run a kubernetes job, so first delete any prior job
  JOB_NAME="${domainUID}-create-weblogic-sample-domain-job"
  deleteK8sObj job $JOB_NAME ${createJobOutput}

  echo Creating the domain by creating the job ${createJobOutput}
  kubectl create -f ${createJobOutput}

  echo "Waiting for the job to complete..."
  JOB_STATUS="0"
  max=20
  count=0
  while [ "$JOB_STATUS" != "Completed" -a $count -lt $max ] ; do
    sleep 30
    count=`expr $count + 1`
    JOB_STATUS=`kubectl get pods --show-all -n ${namespace} | grep ${JOB_NAME} | awk ' { print $3; } '`
    JOB_INFO=`kubectl get pods --show-all -n ${namespace} | grep ${JOB_NAME} | awk ' { print "pod", $1, "status is", $3; } '`
    echo "status on iteration $count of $max"
    echo "$JOB_INFO"

    # Terminate the retry loop when a fatal error has already occurred.  Search for "ERROR:" in the job log file
    if [ "$JOB_STATUS" != "Completed" ]; then
      JOB_ERRORS=`kubectl logs jobs/$JOB_NAME -n ${namespace} | grep "ERROR:" `
      ERR_COUNT=`echo $JOB_ERRORS | grep "ERROR:" | wc | awk ' {print $1; }'`
      if [ "$ERR_COUNT" != "0" ]; then
        echo A failure was detected in the log file for job $JOB_NAME
        echo $JOB_ERRORS
        echo Check the log output for additional information
        fail "Exiting due to failure"
      fi
    fi
  done

  # Confirm the job pod is status completed
  JOB_POD=`kubectl get pods --show-all -n ${namespace} | grep ${JOB_NAME} | awk ' { print $1; } '`
  if [ "$JOB_STATUS" != "Completed" ]; then
    echo The create domain job is not showing status completed after waiting 300 seconds
    echo Check the log output for errors
    kubectl logs jobs/$JOB_NAME -n ${namespace}
    fail "Exiting due to failure"
  fi

  # Check for successful completion in log file
  JOB_STS=`kubectl logs $JOB_POD -n ${namespace} | grep "Successfully Completed" | awk ' { print $1; } '`
  if [ "${JOB_STS}" != "Successfully" ]; then
    echo The log file for the create domain job does not contain a successful completion status
    echo Check the log output for errors
    kubectl logs $JOB_POD -n ${namespace}
    fail "Exiting due to failure"
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
    echo "Administration console access is available at http:${K8S_IP}:${adminNodePort}/console"
  fi
  if [ "${exposeAdminT3Channel}" = true ]; then
    echo "T3 access is available at t3:${K8S_IP}:${t3ChannelPort}"
  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-weblogic-sample-domain-inputs.yaml"
  echo "  ${domainPVCOutput}"
  echo "  ${createJobOutput}"
  echo "  ${dcrOutput}"
}

#
# Function to create the domain's persistent volume
#
function createDomainResource {
  kubectl apply -f ${dcrOutput}
}

#
# Perform the following sequence of steps to create a domain
#

# Setup the environment for running this script and perform initial validation checks
initialize

# Generate the yaml files for creating the domain
createYamlFiles

# Check that the domain secret exists and contains the required elements
validateDomainSecret

# Validate the domain's persistent volume claim
if [ "$doValidation" == true ]; then
  validateDomainPVC
fi

# Create the WebLogic domain
createDomainHome

if [ "${executeIt}" = true ]; then
  createDomainResource
fi

# Print a summary
printSummary

echo 
echo Completed


