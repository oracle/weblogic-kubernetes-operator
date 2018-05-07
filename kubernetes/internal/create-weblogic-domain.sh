#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
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
#
# This internal script is used to generate the yaml files for the domain.
# Also, if -g is not specified, this script then loads these yaml files into kubernetes.
#
# The script needs to be unit tested in an environment where
# kubectl and kubernetes are not available.  This
# means that we need a way to mock this functionality.
#
# To accomplish this, the create split has been split into:
#  - this internal script that abstracts away validating secrets, ...
#  - the public script that customers call.  it implements the abstracted behavior by calling kubectl, ...
#  - a unit testing script that only the tests call.  it implements the abstracted behavior by mocking it out
#
# The caller of this script must define:
#   createScript shell variable that has the full pathname of the script calling this script
#   validateKubectlAvailable shell function that validates whether kubectl is available
#   validateThatSecretExists shell function that validates whether a secret has been registered with kubernetes
#
# TBD - should this script only generate the yaml files?  Or should it also load then into kubernetes?
#
# On the one hand, currently the unit tests only test the generated yaml files (i.e. always specify -g
# to prevent this script from trying to load the yaml files into kubernetes.
#
# On the other hand, some day, we might want to try to unit test loading the files abstracting away
# more behavior so that it can be mocked out.

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/utility.sh

function usage {
  echo usage: ${createScript} -o dir -i file [-g] [-h]
  echo "  -o Ouput directory for the generated yaml files, must be specified."
  echo "  -i Parameter input file, must be specified."
  echo "  -g Only generate the files to create the domain, do not execute them"
  echo "  -h Help"
  exit $1
}

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
    create-weblogic-domain-inputs.yaml \
    weblogic-domain-pv.yaml \
    weblogic-domain-pvc.yaml \
    weblogic-domain-traefik-${clusterNameLC}.yaml \
    weblogic-domain-traefik-security-${clusterNameLC}.yaml \
    weblogic-domain-apache.yaml \
    weblogic-domain-apache-security.yaml \
    create-weblogic-domain-job.yaml \
    domain-custom-resource.yaml
}

#
# Function to validate the version of the inputs file
#
function validateVersion {
  local requiredVersion='create-weblogic-domain-inputs-v1'
  if [ "${version}" != "${requiredVersion}" ]; then
    validationError "Invalid version: \"${version}\".  Must be ${requiredVersion}."
  fi
}

#
# Function to ensure the domain uid is lowercase
#
function validateDomainUid {
  validateLowerCase "domainUID" ${domainUID}
}

#
# Function to ensure the namespace is lowercase
#
function validateNamespace {
  validateLowerCase "namespace" ${namespace}
}

#
# Create an instance of clusterName to be used in cases where lowercase is required.
#
function validateClusterName {
  clusterNameLC=$(toLower $clusterName)
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
# Function to validate the secret name
#
function validateWeblogicCredentialsSecretName {
  validateLowerCase "weblogicCredentialsSecretName" ${weblogicCredentialsSecretName}
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
# Function to validate the weblogic image pull secret exists
#
function validateWeblogicImagePullSecret {
  # The kubernetes secret for pulling images from the docker store is optional.
  # If it was specified, make sure it exists.
  validateSecretExists ${weblogicImagePullSecretName} ${namespace}
  failIfValidationErrors
}

#
# Function to validate the server startup control value
#
function validateStartupControl {
  validateInputParamsSpecified startupControl
  if [ ! -z "${startupControl}" ]; then
    case ${startupControl} in
      "NONE")
      ;;
      "ALL")
      ;;
      "ADMIN")
      ;;
      "SPECIFIED")
      ;;
      "AUTO")
      ;;
      *)
        validationError "Invalid value for startupControl: ${startupControl}. Valid values are 'NONE', 'ALL', 'ADMIN', 'SPECIFIED', and 'AUTO'."
      ;;
    esac
  fi
}

#
# Function to validate the cluster type value
#
function validateClusterType {
  validateInputParamsSpecified clusterType
  if [ ! -z "${clusterType}" ]; then
    case ${clusterType} in
      "CONFIGURED")
      ;;
      "DYNAMIC")
      ;;
      *)
        validationError "Invalid value for clusterType: ${clusterType}. Valid values are 'CONFIGURED' and 'DYNAMIC'."
      ;;
    esac
  fi
}

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateKubectlAvailable

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

  domainPVInput="${scriptDir}/weblogic-domain-pv-template.yaml"
  if [ ! -f ${domainPVInput} ]; then
    validationError "The template file ${domainPVInput} for generating a persistent volume was not found"
  fi

  domainPVCInput="${scriptDir}/weblogic-domain-pvc-template.yaml"
  if [ ! -f ${domainPVCInput} ]; then
    validationError "The template file ${domainPVCInput} for generating a persistent volume claim was not found"
  fi

  jobInput="${scriptDir}/create-weblogic-domain-job-template.yaml"
  if [ ! -f ${jobInput} ]; then
    validationError "The template file ${jobInput} for creating a WebLogic domain was not found"
  fi

  dcrInput="${scriptDir}/domain-custom-resource-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain custom resource was not found"
  fi

  traefikSecurityInput="${scriptDir}/weblogic-domain-traefik-security-template.yaml"
  if [ ! -f ${traefikSecurityInput} ]; then
    validationError "The file ${traefikSecurityInput} for generating the traefik RBAC was not found"
  fi

  traefikInput="${scriptDir}/weblogic-domain-traefik-template.yaml"
  if [ ! -f ${traefikInput} ]; then
    validationError "The template file ${traefikInput} for generating the traefik deployment was not found"
  fi

  apacheSecurityInput="${scriptDir}/weblogic-domain-apache-security-template.yaml"
  if [ ! -f ${apacheSecurityInput} ]; then
    validationError "The file ${apacheSecurityInput} for generating the apache-webtier RBAC was not found"
  fi

  apacheInput="${scriptDir}/weblogic-domain-apache-template.yaml"
  if [ ! -f ${apacheInput} ]; then
    validationError "The template file ${apacheInput} for generating the apache-webtier deployment was not found"
  fi
  
  voyagerInput="${scriptDir}/voyager-ingress-template.yaml"
  if [ ! -f ${voyagerInput} ]; then
    validationError "The template file ${voyagerInput} for generating the Voyager Ingress was not found"
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
    weblogicDomainStoragePath \
    weblogicDomainStorageSize \
    weblogicCredentialsSecretName \
    namespace \
    javaOptions \
    t3PublicAddress \
    version

  validateIntegerInputParamsSpecified \
    adminPort \
    configuredManagedServerCount \
    initialManagedServerReplicas \
    managedServerPort \
    t3ChannelPort \
    adminNodePort \
    loadBalancerWebPort \
    loadBalancerDashboardPort

  validateBooleanInputParamsSpecified \
    productionModeEnabled \
    exposeAdminT3Channel \
    exposeAdminNodePort

  validateVersion
  validateDomainUid
  validateNamespace
  validateClusterName
  validateWeblogicDomainStorageType
  validateWeblogicDomainStorageReclaimPolicy
  validateWeblogicCredentialsSecretName
  validateWeblogicImagePullSecretName
  validateLoadBalancer
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
  copyInputsFileToOutputDirectory ${valuesInputFile} "${domainOutputDir}/create-weblogic-domain-inputs.yaml"

  domainPVOutput="${domainOutputDir}/weblogic-domain-pv.yaml"
  domainPVCOutput="${domainOutputDir}/weblogic-domain-pvc.yaml"
  jobOutput="${domainOutputDir}/create-weblogic-domain-job.yaml"
  dcrOutput="${domainOutputDir}/domain-custom-resource.yaml"
  traefikSecurityOutput="${domainOutputDir}/weblogic-domain-traefik-security-${clusterNameLC}.yaml"
  traefikOutput="${domainOutputDir}/weblogic-domain-traefik-${clusterNameLC}.yaml"
  apacheOutput="${domainOutputDir}/weblogic-domain-apache.yaml"
  apacheSecurityOutput="${domainOutputDir}/weblogic-domain-apache-security.yaml"
  voyagerOutput="${domainOutputDir}/voyager-ingress.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  # Generate the yaml to create the persistent volume
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

  # Generate the yaml to create the kubernetes job that will create the weblogic domain
  echo Generating ${jobOutput}

  cp ${jobInput} ${jobOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${jobOutput}
  sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${jobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${weblogicImagePullSecretName}:g" ${jobOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${weblogicImagePullSecretPrefix}:g" ${jobOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${jobOutput}
  sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${jobOutput}
  sed -i -e "s:%PRODUCTION_MODE_ENABLED%:${productionModeEnabled}:g" ${jobOutput}
  sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${jobOutput}
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${jobOutput}
  sed -i -e "s:%CONFIGURED_MANAGED_SERVER_COUNT%:${configuredManagedServerCount}:g" ${jobOutput}
  sed -i -e "s:%MANAGED_SERVER_NAME_BASE%:${managedServerNameBase}:g" ${jobOutput}
  sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${jobOutput}
  sed -i -e "s:%T3_CHANNEL_PORT%:${t3ChannelPort}:g" ${jobOutput}
  sed -i -e "s:%T3_PUBLIC_ADDRESS%:${t3PublicAddress}:g" ${jobOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${jobOutput}
  sed -i -e "s:%CLUSTER_TYPE%:${clusterType}:g" ${jobOutput}

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
  sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${dcrOutput}
  sed -i -e "s:%INITIAL_MANAGED_SERVER_REPLICAS%:${initialManagedServerReplicas}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_T3_CHANNEL_PREFIX%:${exposeAdminT3ChannelPrefix}:g" ${dcrOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_ADMIN_PORT_PREFIX%:${exposeAdminNodePortPrefix}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_NODE_PORT%:${adminNodePort}:g" ${dcrOutput}
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${dcrOutput}
  sed -i -e "s:%STARTUP_CONTROL%:${startupControl}:g" ${dcrOutput}

  if [ "${loadBalancer}" = "TRAEFIK" ]; then
    # Traefik file
    cp ${traefikInput} ${traefikOutput}
    echo Generating ${traefikOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${traefikOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${traefikOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikOutput}
    sed -i -e "s:%CLUSTER_NAME_LC%:${clusterNameLC}:g" ${traefikOutput}
    sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${traefikOutput}
    sed -i -e "s:%LOAD_BALANCER_DASHBOARD_PORT%:$loadBalancerDashboardPort:g" ${traefikOutput}

    # Traefik security file
    cp ${traefikSecurityInput} ${traefikSecurityOutput}
    echo Generating ${traefikSecurityOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikSecurityOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${traefikSecurityOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${traefikSecurityOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikSecurityOutput}
    sed -i -e "s:%CLUSTER_NAME_LC%:${clusterNameLC}:g" ${traefikSecurityOutput}
  fi

  if [ "${loadBalancer}" = "APACHE" ]; then
    # Apache file
    cp ${apacheInput} ${apacheOutput}
    echo Generating ${apacheOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${apacheOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${apacheOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${apacheOutput}
    sed -i -e "s:%CLUSTER_NAME_LC%:${clusterNameLC}:g" ${apacheOutput}
    sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${apacheOutput}
    sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${apacheOutput}
    sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${apacheOutput}
    sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${apacheOutput}
    sed -i -e "s:%WEB_APP_PREPATH%:$loadBalancerAppPrepath:g" ${apacheOutput}

    if [ ! -z "${loadBalancerVolumePath}" ]; then
      sed -i -e "s:%LOAD_BALANCER_VOLUME_PATH%:${loadBalancerVolumePath}:g" ${apacheOutput}
      sed -i -e "s:# volumes:volumes:g" ${apacheOutput}
      sed -i -e "s:# - name:- name:g" ${apacheOutput}
      sed -i -e "s:#   hostPath:  hostPath:g" ${apacheOutput}
      sed -i -e "s:#     path:    path:g" ${apacheOutput}
      sed -i -e "s:# volumeMounts:volumeMounts:g" ${apacheOutput}
      sed -i -e "s:# - name:- name:g" ${apacheOutput}
      sed -i -e "s:#   mountPath:  mountPath:g" ${apacheOutput}
    fi
 
    # Apache security file
    cp ${apacheSecurityInput} ${apacheSecurityOutput}
    echo Generating ${apacheSecurityOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${apacheSecurityOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${apacheSecurityOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${apacheSecurityOutput}
  fi

  if [ "${loadBalancer}" = "VOYAGER" ]; then
    # Voyager Ingress file
    cp ${voyagerInput} ${voyagerOutput}
    echo Generating ${voyagerOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${voyagerOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${voyagerOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${voyagerOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${voyagerOutput}
    sed -i -e "s:%CLUSTER_NAME_LC%:${clusterNameLC}:g" ${voyagerOutput}
    sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${voyagerOutput}
    sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${voyagerOutput}
    sed -i -e "s:%LOAD_BALANCER_DASHBOARD_PORT%:$loadBalancerDashboardPort:g" ${voyagerOutput}
  fi

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
# Function to run the job that creates the domain
#
function createDomain {

  # There is no way to re-run a kubernetes job, so first delete any prior job
  JOB_NAME="${domainUID}-create-weblogic-domain-job"
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
# Deploy Voyager/HAProxy load balancer
#
function setupVoyagerLoadBalancer {
  # only deploy Voyager Ingress Controller the first time
  local vpod=`kubectl get pod -n voyager | grep voyager | wc -l`
  if [ "$vpod" == "0" ]; then
    kubectl create namespace voyager
    curl -fsSL https://raw.githubusercontent.com/appscode/voyager/6.0.0/hack/deploy/voyager.sh \
    | bash -s -- --provider=baremetal --namespace=voyager
  fi

  # verify Voyager controller pod is ready
  local ready=`kubectl -n voyager get pod | grep voyager-operator | awk ' { print $2; } '`
  if [ "${ready}" != "1/1" ] ; then
    fail "Voyager Ingress Controller is not ready"
  fi

  # deploy Voyager Ingress resource
  kubectl apply -f ${voyagerOutput}

  echo Checking Voyager Ingress resource
  local maxwaitsecs=100
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    local vdep=`kubectl get ingresses.voyager.appscode.com -n ${namespace} | grep ${domainUID}-voyager | wc | awk ' { print $1; } '`
    if [ "$vdep" = "1" ]; then
      echo "The Voyager Ingress resource ${domainUID}-voyager is created successfully."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The Voyager Ingress resource ${domainUID}-voyager was not created."
    fi
    sleep 5
  done

  echo Checking HAProxy pod is running
  local maxwaitsecs=100
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    local st=`kubectl get pod -n ${namespace} | grep ^voyager-${domainUID}-voyager- | awk ' { print $3; } '`
    if [ "$st" = "Running" ]; then
      echo "The HAProxy pod for Voyaer Ingress ${domainUID}-voyager is created successfully."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The HAProxy pod for Voyaer Ingress ${domainUID}-voyager  was not created or running."
    fi
    sleep 5
  done

  echo Checking Voyager service
  local maxwaitsecs=100
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    local vscv=`kubectl get service ${domainUID}-voyager-stats -n ${namespace} | grep ${domainUID}-voyager-stats | wc | awk ' { print $1; } '`
    if [ "$vscv" = "1" ]; then
      echo 'The service ${domainUID}-voyager-stats is created successfully.'
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The service ${domainUID}-voyager-stats was not created."
    fi
    sleep 5
  done
}

#
# Deploy traefik load balancer
#
function setupTraefikLoadBalancer {

  traefikName="${domainUID}-${clusterNameLC}-traefik"

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
# Deploy Apache load balancer
#
function setupApacheLoadBalancer {

  apacheName="${domainUID}-apache-webtier"

  echo Setting up apache security
  kubectl apply -f ${apacheSecurityOutput}

  echo Checking the cluster role ${apacheName} was created
  CLUSTERROLE=`kubectl get clusterroles | grep ${apacheName} | wc | awk ' { print $1; } '`
  if [ "$CLUSTERROLE" != "1" ]; then
    fail "The cluster role ${apacheName} was not created"
  fi

  echo Checking the cluster role binding ${apacheName} was created
  CLUSTERROLEBINDING=`kubectl get clusterrolebindings | grep ${apacheName} | wc | awk ' { print $1; } '`
  if [ "$CLUSTERROLEBINDING" != "1" ]; then
    fail "The cluster role binding ${apacheName} was not created"
  fi

  echo Deploying apache
  kubectl apply -f ${apacheOutput}

  echo Checking apache deployment
  SS=`kubectl get deployment -n ${namespace} | grep ${apacheName} | wc | awk ' { print $1; } '`
  if [ "$SS" != "1" ]; then
    fail "The deployment ${apacheName} was not created"
  fi

  echo Checking the apache service account
  SA=`kubectl get serviceaccount ${apacheName} -n ${namespace} | grep ${apacheName} | wc | awk ' { print $1; } '`
  if [ "$SA" != "1" ]; then
    fail "The service account ${apacheName} was not created"
  fi

  echo Checking apache service
  TSVC=`kubectl get services -n ${namespace} | grep ${apacheName} | wc | awk ' { print $1; } '`
  if [ "$TSVC" != "1" ]; then
    fail "The service ${apacheServiceName} was not created"
  fi
}

#
# Function to create the domain custom resource
#
function createDomainCustomResource {
  echo Creating the domain custom resource using ${dcrOutput}
  kubectl apply -f ${dcrOutput}

  echo Checking the domain custom resource was created
  DCR_AVAIL=`kubectl get domain -n ${namespace} | grep ${domainUID} | wc | awk ' { print $1; } '`
  if [ "${DCR_AVAIL}" != "1" ]; then
    fail "The domain custom resource ${domainUID} was not found"
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
  if [ "${loadBalancer}" = "TRAEFIK" ] || [ "${loadBalancer}" = "VOYAGER" ]; then
    echo "The load balancer for cluster '${clusterName}' is available at http:${K8S_IP}:${loadBalancerWebPort}/ (add the application path to the URL)"
    echo "The load balancer dashboard for cluster '${clusterName}' is available at http:${K8S_IP}:${loadBalancerDashboardPort}"
    echo ""
  elif [ "${loadBalancer}" = "APACHE" ]; then
    echo "The apache load balancer for '${domainUID}' is available at http:${K8S_IP}:${loadBalancerWebPort}/ (add the application path to the URL)"

  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-weblogic-domain-inputs.yaml"
  echo "  ${domainPVOutput}"
  echo "  ${domainPVCOutput}"
  echo "  ${jobOutput}"
  echo "  ${dcrOutput}"
  if [ "${loadBalancer}" = "TRAEFIK" ]; then
    echo "  ${traefikSecurityOutput}"
    echo "  ${traefikOutput}"
  elif [ "${loadBalancer}" = "APACHE" ]; then
    echo "  ${apacheSecurityOutput}"
    echo "  ${apacheOutput}"
  elif [ "${loadBalancer}" = "VOYAGER" ]; then
    echo "  ${voyagerOutput}"
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
  if [ "${loadBalancer}" = "TRAEFIK" ]; then
    setupTraefikLoadBalancer
  elif [ "${loadBalancer}" = "APACHE" ]; then
    setupApacheLoadBalancer
  elif [ "${loadBalancer}" = "VOYAGER" ]; then
    setupVoyagerLoadBalancer
  fi

  # Create the domain custom resource
  createDomainCustomResource

  # Output a job summary
  outputJobSummary
fi

echo 
echo Completed


