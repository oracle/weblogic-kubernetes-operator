#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Description
#  This script automates the creation of a WebLogic domain within a Kubernetes cluster.
#
#  The domain creation inputs can be customized by editing create-weblogic-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#


# Initialize
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
internalDir="${scriptDir}/internal"
source ${internalDir}/utility.sh

#
# Parse the command line options
#
generateOnly=false
while getopts "ghi:o:" opt; do
  case $opt in
    g) generateOnly=true
    ;;
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    h) echo ${scriptDir}/create-weblogic-domain.sh [-g] [-i file] [-o dir] [-h]
       echo
       echo -g Only generate the files to create the domain, do not execute them
       echo -i Parameter input file, a modified copy of ${scriptDir}/create-weblogic-domain-inputs.yaml
       echo -o Ouput directory for the generated yaml files, defaults to the current directory of the shell executing this script.
       echo -h Help
       exit
    ;;
    \?) fail "Invalid or missing command line option"
    ;;
  esac
done

#
# Function to initialize and validate the output directory
# for the generated yaml files for this domain.
#
function initAndValidateOutputDir {
  domainOutputDir="${outputDir}/weblogic-domains/${domainUid}"
  validateOutputDir \
    ${domainOutputDir} \
    ${valuesInputFile} \
    create-weblogic-domain-inputs.yaml \
    weblogic-domain-persistent-volume.yaml \
    weblogic-domain-persistent-volume-claim.yaml \
    create-weblogic-domain-domain-job.yaml \
    domain-custom-resource.yaml \
    traefik.yaml
}

#
# Function to ensure the domain uid is lowercase
#
function validateDomainUid {
  validateLowerCase "domainUid" ${domainUid}
}

#
# Create an instance of clusterName to be used in cases where lowercase is required.
#
function validateClusterName {
  clusterNameLC=$(toLower $clusterName)
}

#
# Function to default the value of persistenceStorageClass.
# When the parameter is not specified in the input file, it will default to use the value domainUid
#
function validateStorageClass {
  if [ -z $persistenceStorageClass ]; then
    persistenceStorageClass=$domainUid
    echo Defaulting the input parameter persistenceStorageClass to be $domainUid
  else
    validateLowerCase "persistenceStorageClass" ${persistenceStorageClass}
  fi
}

#
# Function to validate the persistent volume claim name
#
function validatePersistentVolumeClaimName {
  validateLowerCase "persistenceVolumeClaimName" ${persistenceVolumeClaimName}

  if [[ ${persistenceVolumeClaimName} != ${domainUid}-* ]] ; then
    echo persistenceVolumeClaimName specified does not starts with \'${domainUid}-\', appending it
    persistenceVolumeClaimName=${domainUid}-${persistenceVolumeClaimName}
    echo persistenceVolumeClaimName is now ${persistenceVolumeClaimName}
  fi
}

#
# Function to validate the persistent volume name
#
function validatePersistenVolumeName {
  validateLowerCase "persistenceVolumeName" ${persistenceVolumeName}

  if [[ ${persistenceVolumeName} != ${domainUid}-* ]] ; then
    echo persistenceVolumeName specified does not starts with \'${domainUid}-\', appending it
    persistenceVolumeName=${domainUid}-${persistenceVolumeName}
    echo persistenceVolumeName is now ${persistenceVolumeName}
  fi
}

#
# Function to validate the secret name
#
function validateSecretName {
  validateLowerCase "secretName" ${secretName}
}

#
# Function to validate the load balancer value
#
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
# Function to validate the domain secret
#
function validateDomainSecret {
  # Verify the secret exists
  validateSecretExists ${secretName} ${namespace}
  failIfValidationErrors

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
  failIfValidationErrors
}

#
# Function to validate the image pull secret name
#
function validateImagePullSecretName {
  if [ ! -z ${imagePullSecretName} ]; then
    validateLowerCase "validateImagePullSecretName" ${imagePullSecretName}
    imagePullSecretPrefix=""
    if [ "${generateOnly}" = false ]; then
      validateImagePullSecret
    fi
  else
    # Set name blank when not specified, and comment out the yaml
    imagePullSecretName=""
    imagePullSecretPrefix="#"
  fi
}

#
# Function to validate the image pull secret exists
#
function validateImagePullSecret {

  # The kubernetes secret for pulling images from the docker store is optional.
  # If it was specified, make sure it exists.
  validateSecretExists ${imagePullSecretName} ${namespace}
  failIfValidationErrors
}

#
# Function to validate the server startup control value
#
function validateStartupControl {
  STARTUP_CONTROL_NONE="NONE"
  STARTUP_CONTROL_ALL="ALL"
  STARTUP_CONTROL_ADMIN="ADMIN"
  STARTUP_CONTROL_SPECIFIED="SPECIFIED"
  STARTUP_CONTROL_AUTO="AUTO"

  case ${startupControl} in
    ${STARTUP_CONTROL_NONE})
    ;;
    ${STARTUP_CONTROL_ALL})
    ;;
    ${STARTUP_CONTROL_ADMIN})
    ;;
    ${STARTUP_CONTROL_SPECIFIED})
    ;;
    ${STARTUP_CONTROL_AUTO})
    ;;
    *)
      validationError "Invalid valid for startupControl: ${startupControl}. Valid values are 'NONE', 'ALL', 'ADMIN', 'SPECIFIED', and 'AUTO'."
    ;;
  esac
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

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of ${scriptDir}/create-weblogic-domain-inputs.yaml)."
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

  domainPVInput="${internalDir}/weblogic-domain-persistent-volume-template.yaml"
  if [ ! -f ${domainPVInput} ]; then
    validationError "The template file ${domainPVInput} for generating a persistent volume was not found"
  fi

  domainPVCInput="${internalDir}/weblogic-domain-persistent-volume-claim-template.yaml"
  if [ ! -f ${domainPVCInput} ]; then
    validationError "The template file ${domainPVCInput} for generating a persistent volume claim was not found"
  fi

  jobInput="${internalDir}/create-weblogic-domain-job-template.yaml"
  if [ ! -f ${jobInput} ]; then
    validationError "The template file ${jobInput} for creating a WebLogic domain was not found"
  fi

  dcrInput="${internalDir}/domain-custom-resource-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain custom resource was not found"
  fi

  traefikSecurityInput="${internalDir}/traefik-security-template.yaml"
  if [ ! -f ${traefikSecurityInput} ]; then
    validationError "The file ${traefikSecurityInput} for generating the traefik RBAC was not found"
  fi

  traefikInput="${internalDir}/traefik-template.yaml"
  if [ ! -f ${traefikInput} ]; then
    validationError "The template file ${traefikInput} for generating the traefik deployment was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs
  validateInputParamsSpecified adminPort adminServerName createDomainScript domainName domainUid clusterName managedServerCount managedServerStartCount managedServerNameBase
  validateInputParamsSpecified managedServerPort persistencePath persistenceSize persistenceVolumeClaimName persistenceVolumeName
  validateInputParamsSpecified productionModeEnabled secretName t3ChannelPort exposeAdminT3Channel adminNodePort exposeAdminNodePort
  validateInputParamsSpecified namespace loadBalancer loadBalancerWebPort loadBalancerAdminPort loadBalancer javaOptions startupControl
  validateDomainUid
  validateClusterName
  validateStorageClass
  validatePersistenVolumeName
  validatePersistentVolumeClaimName
  validateSecretName
  validateImagePullSecretName
  validateLoadBalancer
<<<<<<< HEAD:kubernetes/create-weblogic-domain.sh
  initAndValidateOutputDir
=======
  validateStartupControl
>>>>>>> 382fa6326adb0bcc2c02f40cfe5410634c7213a5:kubernetes/create-domain-job.sh
  failIfValidationErrors
}


#
# Function to generate the yaml files for creating a domain
#
function createYamlFiles {

  # Create a directory for this domain's output files
  mkdir -p ${domainOutputDir}

  # Save a copy of the inputs yaml file there
  cp ${valuesInputFile} "${domainOutputDir}/create-weblogic-domain-inputs.yaml"

  domainPVOutput="${domainOutputDir}/weblogic-domain-persistent-volume.yaml"
  domainPVCOutput="${domainOutputDir}/weblogic-domain-persistent-volume-claim.yaml"
  jobOutput="${domainOutputDir}/create-weblogic-domain-domain-job.yaml"
  dcrOutput="${domainOutputDir}/domain-custom-resource.yaml"
  traefikSecurityOutput="${domainOutputDir}/traefik-security.yaml"
  traefikOutput="${domainOutputDir}/traefik.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  # Generate the yaml to create the persistent volume
  echo Generating ${domainPVOutput}

  cp ${domainPVInput} ${domainPVOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${domainPVOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${domainPVOutput}
  sed -i -e "s:%PERSISTENT_VOLUME%:${persistenceVolumeName}:g" ${domainPVOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_PATH%:${persistencePath}:g" ${domainPVOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_SIZE%:${persistenceSize}:g" ${domainPVOutput}
  sed -i -e "s:%STORAGE_CLASS_NAME%:${persistenceStorageClass}:g" ${domainPVOutput}

  # Generate the yaml to create the persistent volume claim
  echo Generating ${domainPVCOutput}

  cp ${domainPVCInput} ${domainPVCOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${domainPVCOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${domainPVCOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_CLAIM%:${persistenceVolumeClaimName}:g" ${domainPVCOutput}
  sed -i -e "s:%STORAGE_CLASS_NAME%:${persistenceStorageClass}:g" ${domainPVCOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_SIZE%:${persistenceSize}:g" ${domainPVCOutput}

  # Generate the yaml to create the kubernetes job that will create the weblogic domain
  echo Generating ${jobOutput}

  cp ${jobInput} ${jobOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${jobOutput}
  sed -i -e "s:%SECRET_NAME%:${secretName}:g" ${jobOutput}
  sed -i -e "s:%DOCKER_REGISTRY_SECRET%:${imagePullSecretName}:g" ${jobOutput}
  sed -i -e "s:%IMAGE_PULL_SECRET_PREFIX%:${imagePullSecretPrefix}:g" ${jobOutput}
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
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${dcrOutput}
  sed -i -e "s:%STARTUP_CONTROL%:${startupControl}:g" ${dcrOutput}

  # Traefik file
  cp ${traefikInput} ${traefikOutput}
  echo Generating ${traefikOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${traefikOutput}
  sed -i -e "s:%CLUSTER_NAME_LC%:${clusterNameLC}:g" ${traefikOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikOutput}
  sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${traefikOutput}
  sed -i -e "s:%LOAD_BALANCER_ADMIN_PORT%:$loadBalancerAdminPort:g" ${traefikOutput}

  # Traefik security file
  cp ${traefikSecurityInput} ${traefikSecurityOutput}
  echo Generating ${traefikSecurityOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikSecurityOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUid}:g" ${traefikSecurityOutput}
  sed -i -e "s:%CLUSTER_NAME_LC%:${clusterNameLC}:g" ${traefikSecurityOutput}

}

#
# Function to create the domain's persistent volume
#
function createDomainPV {

  # Check if the persistent volume is already available
  checkPvExists ${persistenceVolumeName}
  if [ "${PV_EXISTS}" = "false" ]; then
    echo Creating the persistent volume ${persistenceVolumeName}
    kubectl create -f ${domainPVOutput}
    checkPvState ${persistenceVolumeName} Available
  fi
}

#
# Function to create the domain's persistent volume claim
#
function createDomainPVC {
  # Check if the persistent volume claim is already available
  checkPvcExists ${persistenceVolumeClaimName} ${namespace}
  if [ "${PVC_EXISTS}" = "false" ]; then
    echo Creating the persistent volume claim ${persistenceVolumeClaimName}
    kubectl create -f ${domainPVCOutput}
    checkPvState ${persistenceVolumeName} Bound
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
  max=20
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

  traefikName="${domainUid}-${clusterNameLC}-traefik"

  echo Setting up traefik security
  kubectl apply -f ${traefikSecurityOutput}

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
  kubectl apply -f ${traefikOutput}

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
# Function to obtain the IP address of the kubernetes cluster.  This information
# is used to form the URL's for accessing services that were deployed.
#
function getKubernetesClusterIP {

  # Get name of the current context
  local CUR_CTX=`kubectl config current-context | awk ' { print $1; } '`

  # Get the name of the current cluster
  local CUR_CLUSTER_CMD="kubectl config view -o jsonpath='{.contexts[?(@.name == \"${CUR_CTX}\")].context.cluster}' | awk ' { print $1; } '"
  local CUR_CLUSTER=`eval ${CUR_CLUSTER_CMD}`

  # Get the server address for the current cluster
  local SVR_ADDR_CMD="kubectl config view -o jsonpath='{.clusters[?(@.name == \"${CUR_CLUSTER}\")].cluster.server}' | awk ' { print $1; } '"
  local SVR_ADDR=`eval ${SVR_ADDR_CMD}`

  # Server address is expected to be of the form http://address:port.  Delimit
  # string on the colon to obtain the address.  Leave the "//" on the resulting string.
  local array=(${SVR_ADDR//:/ })
  K8S_IP="${array[1]}"
}

#
# Function to output to the console a summary of the work completed
#
function outputJobSummary {

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
  if [ "${loadBalancer}" = "traefik" ]; then
    echo "The load balancer for cluster '${clusterName}' is available at http:${K8S_IP}:${loadBalancerWebPort}/ (add the application path to the URL)"
    echo "The load balancer dashboard for cluster '${clusterName}' is available at http:${K8S_IP}:${loadBalancerAdminPort}"
    echo ""
  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-weblogic-domain-inputs.yaml"
  echo "  ${domainPVOutput}"
  echo "  ${domainPVCOutput}"
  echo "  ${jobOutput}"
  echo "  ${dcrOutput}"
  if [ "${loadBalancer}" = "traefik" ]; then
    echo "  ${traefikSecurityOutput}"
    echo "  ${traefikOutput}"
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
  # Check that the domain secret exists and contains the required elements
  validateDomainSecret

  # Create the domain's persistent volume
  createDomainPV

  # Create the domain's persistent volume claim
  createDomainPVC

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


