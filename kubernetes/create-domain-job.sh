#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Description
#  This script automates the creation of a WebLogic domain within a Kubernetes cluster.
#
#  The domain creation inputs can be customized by editing create-domain-job-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  echo [ERROR] $1
  exit 1
}

#
# Function to default the value of persistenceStorageClass.
# When the parameter is not specified in the input file, it will default to use the value domainUid
#
function validateStorageClass {
  if [ -z $persistenceStorageClass ]; then
    persistenceStorageClass=$domainUid
    echo Defaulting the input parameter persistenceStorageClass to be $domainUid
  fi
}

#
# Parse the command line options
#
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
valuesInputFile="${scriptDir}/create-domain-job-inputs.yaml"
generateOnly=false
while getopts "ghi:" opt; do
  case $opt in
    g) generateOnly=true
    ;;
    i) valuesInputFile="${OPTARG}"
    ;;
    h) echo ./create-domain-job.sh [-g] [-i file] [-h]
       echo
       echo -g Only generate the files to create the domain, do not execute them
       echo -i Parameter input file, defaults to create-domain-job-inputs.yaml
       echo -h Help
       exit
    ;;
    \?) fail "Invalid or missing command line option"
    ;;
  esac
done

#
# Function to note that a validate error has occurred
#
function validationError {
  echo "[ERROR] $1"
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
    if [ -z $val ]; then
      validationError "The ${name} parameter in ${valuesInputFile} is missing, null or empty"
    fi
  done
}

#
# Function to validate the load balancer value
function validateLoadBalancer {
  LOAD_BALANCER_TRAEFIK="traefik"
  LOAD_BALANCER_NONE="none"

  case ${loadBalancer} in
    ${LOAD_BALANCER_TRAEFIK})
    ;;
    ${LOAD_BALANCER_NONE})
    ;;
    *)
      validationError "Invalid valid for loadBalancer: ${loadBalancer}. Valid values are traefik and none."
    ;;
  esac
}

#
# Function to valid the domain secret
#
function validateDomainSecret {
  # Verify the secret exists
  SECRET=`kubectl get secret ${secretName} -n ${namespace} | grep ${secretName} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${secretName} was not found in namespace ${namespace}"
  fi

  # Verify the secret contains a username
  SECRET=`kubectl get secret ${secretName} -n ${namespace} -o jsonpath='{.data}'| grep username: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${secretName} in namespace ${namespace} does contain a username"
  fi

  # Verify the secret contains a password
  SECRET=`kubectl get secret ${secretName} -n ${namespace} -o jsonpath='{.data}'| grep password: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The domain secret ${secretName} in namespace ${namespace} does contain a password"
  fi
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
  echo Input parameters being used to create the WebLogic domain
  cat ${exportValuesFile}
  echo
  source ${exportValuesFile}

  if [[ ${persistenceVolumeName} != ${domainUid}-* ]] ; then
    echo persistenceVolumeName specified does not starts with \'${domainUid}-\', appending it 
    persistenceVolumeName=${domainUid}-${persistenceVolumeName}
    echo persistenceVolumeName is now ${persistenceVolumeName}
  fi

  if [[ ${persistenceVolumeClaimName} != ${domainUid}-* ]] ; then
    echo persistenceVolumeClaimName specified does not starts with \'${domainUid}-\', appending it 
    persistenceVolumeClaimName=${domainUid}-${persistenceVolumeClaimName}
    echo persistenceVolumeClaimName is now ${persistenceVolumeClaimName}
  fi
}

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  if ! [ -x "$(command -v kubectl)" ]; then
    validationError "kubectl is not installed"
  fi

  if [ ! -f ${valuesInputFile} ]; then
    validationError "Unable to locate the domain input parameters file ${valuesInputFile}"
    validateErrors=true
  fi

  pvInput="${scriptDir}/internal/persistent-volume-template.yaml"
  pvOutput="${scriptDir}/persistent-volume.yaml"
  if [ ! -f ${pvInput} ]; then
    validationError "The template file ${pvInput} for generating a persistent volume was not found"
  fi

  pvcInput="${scriptDir}/internal/persistent-volume-claim-template.yaml"
  pvcOutput="${scriptDir}/persistent-volume-claim.yaml"
  if [ ! -f ${pvcInput} ]; then
    validationError "The template file ${pvcInput} for generating a persistent volume claim was not found"
  fi

  jobInput="${scriptDir}/internal/domain-job-template.yaml"
  jobOutput="${scriptDir}/domain-job.yaml"
  if [ ! -f ${jobInput} ]; then
    validationError "The template file ${jobInput} for creating a WebLogic domain was not found"
  fi

  dcrInput="${scriptDir}/internal/domain-custom-resource-template.yaml"
  dcrOutput="${scriptDir}/domain-custom-resource.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain custom resource was not found"
  fi

  traefikRBACInput="${scriptDir}/internal/traefik-rbac-template.yaml"
  traefikRBACOutput="${scriptDir}/traefik-rbac.yaml"
  if [ ! -f ${traefikRBACInput} ]; then
    validationError "The file ${traefikRBACInput} for generating the traefik RBAC was not found"
  fi

  traefikDeployInput="${scriptDir}/internal/traefik-deployment-template.yaml"
  traefikDeployOutput="${scriptDir}/traefik-deployment.yaml"
  if [ ! -f ${traefikDeployInput} ]; then
    validationError "The template file ${traefikDeployInput} for generating the traefik deployment was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs
  validateInputParamsSpecified adminPort adminServerName createDomainScript domainName domainUid clusterName managedServerCount managedServerStartCount managedServerNameBase
  validateInputParamsSpecified managedServerPort persistencePath persistenceSize persistenceVolumeClaimName persistenceVolumeName
  validateInputParamsSpecified productionModeEnabled secretsMountPath secretName t3ChannelPort exposeAdminT3Channel adminNodePort exposeAdminNodePort
  validateInputParamsSpecified namespace loadBalancer loadBalancerWebPort loadBalancerAdminPort loadBalancer
  validateStorageClass
  validateLoadBalancer
  validateDomainSecret
  failIfValidationErrors
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
# Function to generate the yaml files for creating a domain
#
function createYamlFiles {
  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  # Generate the yaml to create the persistent volume
  echo Generating ${pvOutput}

  cp ${pvInput} ${pvOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${pvOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${pvOutput}
  sed -i -e "s:%PERSISTENT_VOLUME%:${persistenceVolumeName}:g" ${pvOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_PATH%:${persistencePath}:g" ${pvOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_SIZE%:${persistenceSize}:g" ${pvOutput}
  sed -i -e "s:%STORAGE_CLASS_NAME%:${persistenceStorageClass}:g" ${pvOutput}

  # Generate the yaml to create the persistent volume claim
  echo Generating ${pvcOutput}

  cp ${pvcInput} ${pvcOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${pvcOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${pvcOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_CLAIM%:${persistenceVolumeClaimName}:g" ${pvcOutput}
  sed -i -e "s:%STORAGE_CLASS_NAME%:${persistenceStorageClass}:g" ${pvcOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_SIZE%:${persistenceSize}:g" ${pvcOutput}

  # Generate the yaml to create the kubernetes job that will create the weblogic domain
  echo Generating ${jobOutput}

  cp ${jobInput} ${jobOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${jobOutput}
  sed -i -e "s:%SECRET_NAME%:${secretName}:g" ${jobOutput}
  sed -i -e "s:%SECRETS_MOUNT_PATH%:${secretsMountPath}:g" ${jobOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_CLAIM%:${persistenceVolumeClaimName}:g" ${jobOutput}
  sed -i -e "s:%CREATE_DOMAIN_SCRIPT%:${createDomainScript}:g" ${jobOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${jobOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${jobOutput}
  sed -i -e "s:%PRODUCTION_MODE_ENABLED%:${productionModeEnabled}:g" ${jobOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${jobOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${jobOutput}
  sed -i -e "s:%NUMBER_OF_MS%:${managedServerCount}:g" ${jobOutput}
  sed -i -e "s:%MANAGED_SERVER_NAME_BASE%:${managedServerNameBase}:g" ${jobOutput}
  sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${jobOutput}
  sed -i -e "s:%T3_CHANNEL_PORT%:${t3ChannelPort}:g" ${jobOutput}
  sed -i -e "s:%T3_PUBLIC_ADDRESS%:${t3PublicAddress}:g" ${jobOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${jobOutput}

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
  sed -i -e "s:%SECRET_NAME%:${secretName}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${dcrOutput}
  sed -i -e "s:%MANAGED_SERVER_START_COUNT%:${managedServerStartCount}:g" ${dcrOutput}
  sed -i -e "s:%NUMBER_OF_MS%:${managedServerCount}:g" ${dcrOutput}
  sed -i -e "s:%EXPORT_T3_CHANNEL_PREFIX%:${exposeAdminT3ChannelPrefix}:g" ${dcrOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_ADMIN_PORT_PREFIX%:${exposeAdminNodePortPrefix}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_NODE_PORT%:${adminNodePort}:g" ${dcrOutput}

  # Traefik deployment file
  cp ${traefikDeployInput} ${traefikDeployOutput}
  echo Generating ${traefikDeployOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikDeployOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${traefikDeployOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikDeployOutput}
  sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${traefikDeployOutput}
  sed -i -e "s:%LOAD_BALANCER_ADMIN_PORT%:$loadBalancerAdminPort:g" ${traefikDeployOutput}

  # Traefik RBAC file
  cp ${traefikRBACInput} ${traefikRBACOutput}
  echo Generating ${traefikRBACOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikRBACOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${traefikRBACOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikRBACOutput}

}

#
# Function to create the persistent volume
#
function createPV {

  # Check if the persistent volume is already available
  skipPvCreate=false
  echo Checking if the persistent volume ${persistenceVolumeName} already exists
  PV_AVAILABLE=`kubectl get pv | grep ${persistenceVolumeName} | wc | awk ' { print $1; } '`
  if [ "${PV_AVAILABLE}" = "1" ]; then
    echo The persistent volume ${persistenceVolumeName} already exists and will not be re-created
    skipPvCreate=true
  fi

  if [ "${skipPvCreate}" = false ]; then
    echo Creating the persistent volume ${persistenceVolumeName}
    kubectl create -f ${pvOutput}

    echo Checking if the persistent volume ${persistenceVolumeName} is Available
    PV_AVAILABLE=`kubectl get pv | grep ${persistenceVolumeName} | awk ' { print $5; } '`
    if [ "${PV_AVAILABLE}" != "Available" ]; then
      fail 'The persistent volume ${persistenceVolumeName} does not have a state of Available'
    fi
  fi
}

#
# Function to create the persistent volume claim
#
function createPVC {
  # Check if the persistent volume claim is already available
  skipPvcCreate=false
  echo Checking if the persistent volume claim ${persistenceVolumeClaimName} already exists
  PVC_AVAILABLE=`kubectl get pvc -n ${namespace} | grep ${persistenceVolumeClaimName} | wc | awk ' { print $1; } '`
  if [ "${PVC_AVAILABLE}" = "1" ]; then
    echo The persistent volume claim ${persistenceVolumeClaimName} already exists and will not be re-created
    skipPvcCreate=true
  fi

  if [ "${skipPvcCreate}" = false ]; then
    echo Creating the persistent volume claim ${persistenceVolumeClaimName}
    kubectl create -f ${pvcOutput}

    echo Checking the persistent volume ${persistenceVolumeClaimName} is Bound
    PV_BOUND=`kubectl get pv | grep ${persistenceVolumeName} | awk ' { print $5; } '`
    if [ "${PV_BOUND}" != "Bound" ]; then
      fail "The persistent volume ${persistenceVolumeName} does not have a state of Bound"
    fi
  fi
}

#
# Function to run the job that creates the domain
#
function createDomain {

  # There is no way to re-run a kubernetes job, so first delete any prior job
  JOB_NAME="domain-${domainUid}-job"
  deleteK8sObj job $JOB_NAME ${jobOutput}

  echo Creating the domain by creating the job ${jobOutput}
  kubectl create -f ${jobOutput}

  echo "Waiting for the job to complete..."
  JOB_STATUS="0"
  max=10
  count=0
  while [ "$JOB_STATUS" != "Completed" -a $count -lt $max ] ; do
    sleep 30
    count=`expr $count + 1`
    JOB_STATUS=`kubectl get pods --show-all -n ${namespace} | grep "domain-${domainUid}" | awk ' { print $3; } '`
    JOB_INFO=`kubectl get pods --show-all -n ${namespace} | grep "domain-${domainUid}" | awk ' { print "pod", $1, "status is", $3; } '`
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
  JOB_POD=`kubectl get pods --show-all -n ${namespace} | grep "domain-${domainUid}" | awk ' { print $1; } '`
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
# Deploy traefik load balancer
#
function setupTraefikLoadBalancer {

  traefikName="${domainUid}-${clusterName}-traefik"

  echo Setting up traefik rbac
  kubectl apply -f ${traefikRBACOutput}

  echo Checking the cluster role ${traefikName} was created
  CLUSTERROLE=`kubectl get clusterroles | grep ${traefikName} | wc | awk ' { print $1; } '`
  if [ "$CLUSTERROLE" != "1" ]; then
    fail "The cluster role ${traefikName} was not created"
  fi

  echo Checking the cluster role binding ${traefikName} was created
  CLUSTERROLEBINDING=`kubectl get clusterrolebindings | grep ${traefikName} | wc | awk ' { print $1; } '`
  if [ "$CLUSTERROLEBINDING" != "1" ]; then
    fail "The cluster role binding ${traefikName} was not created"
  fi

  echo Deploying traefik
  kubectl apply -f ${traefikDeployOutput}

  echo Checking traefik deployment
  DEPLOY=`kubectl get deployment -n ${namespace} | grep ${traefikName} | wc | awk ' { print $1; } '`
  if [ "$DEPLOY" != "1" ]; then
    fail "The deployment ${traefikName} was not created"
  fi

  echo Checking the traefik service account
  SA=`kubectl get serviceaccount ${traefikName} -n ${namespace} | grep ${traefikName} | wc | awk ' { print $1; } '`
  if [ "$SA" != "1" ]; then
    fail "The service account ${traefikName} was not created"
  fi

  echo Checking traefik service
  TSVC=`kubectl get service ${traefikName} -n ${namespace} | grep ${traefikName} | wc | awk ' { print $1; } '`
  if [ "$TSVC" != "1" ]; then
    fail "The service ${traefikName} was not created"
  fi
}

#
# Function to create the domain custom resource
#
function createDomainCustomResource {
  echo Creating the domain custom resource using ${dcrOutput}
  kubectl apply -f ${dcrOutput}

  echo Checking the domain custom resource was created
  DCR_AVAIL=`kubectl get domain -n ${namespace} | grep ${domainUid} | wc | awk ' { print $1; } '`
  if [ "${DCR_AVAIL}" != "1" ]; then
    fail "The domain custom resource ${domainUid} was not found"
  fi
}

#
# Function to output to the console a summary of the work completed
#
function outputJobSummary {
  echo ""
  echo "Domain ${domainName} was created and will be started by the WebLogic Kubernetes Operator"
  echo ""
  echo "The following files were generated:"
  echo "  ${pvOutput}"
  echo "  ${pvcOutput}"
  echo "  ${jobOutput}"
  echo "  ${dcrOutput}"
  if [ "${loadBalancer}" = "traefik" ]; then
    echo "  ${traefikRBACOutput}"
    echo "  ${traefikDeployOutput}"
  fi
}

#
# Perform the following sequence of steps to create a domain
#

# Setup the environment for running this script and perform initial validation checks
initialize

# Generate the yaml files for creating the domain
createYamlFiles

# All done if the generate only option is true
if [ "${generateOnly}" = false ]; then
  # Create the persistent volume
  createPV

  # Create the persistent volume claim
  createPVC

  # Create the WebLogic domain
  createDomain

  # Setup load balancer
  if [ "${loadBalancer}" = "traefik" ]; then
    setupTraefikLoadBalancer
  fi

  # Create the domain custom resource
  createDomainCustomResource

  # Output a job summary
  outputJobSummary
fi

echo 
echo Completed


