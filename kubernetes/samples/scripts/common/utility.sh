#!/usr/bin/env bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Utility functions that are shared by multiple scripts
#

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  printError $*
  exit 1
}

# Function to print an error message
function printError {
  echo [ERROR] $*
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
      # javaOptions may contain tokens that are not allowed in export command
      # we need to handle it differently. 
      if ($2=="javaOptions") {
        printf("%s=%s\n", $2, $3);
      } else {
        printf("export %s=\"%s\"\n", $2, $3);
      }
    }
  }' > $2
}

#
# Function to remove a file if it exists 
#
function removeFileIfExists {
  if [ -f $1 ]; then
    rm $1
  fi
}

#
# Function to parse the common parameter inputs file
#
function parseCommonInputs {
  exportValuesFile="/tmp/export-values.sh"
  tmpFile="/tmp/javaoptions_tmp.dat"
  parseYaml ${valuesInputFile} ${exportValuesFile}

  if [ ! -f ${exportValuesFile} ]; then
    echo Unable to locate the parsed output of ${valuesInputFile}.
    fail 'The file ${exportValuesFile} could not be found.'
  fi

  # Define the environment variables that will be used to fill in template values
  echo Input parameters being used
  cat ${exportValuesFile}
  echo

  # javaOptions may contain tokens that are not allowed in export command
  # we need to handle it differently. 
  # we set the javaOptions variable that can be used later
  tmpStr=`grep "javaOptions" ${exportValuesFile}`
  javaOptions=${tmpStr//"javaOptions="/}

  # We exclude javaOptions from the exportValuesFile
  grep -v "javaOptions" ${exportValuesFile} > ${tmpFile}
  source ${tmpFile}
  rm ${exportValuesFile} ${tmpFile}
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
# Function to lowercase a value
# $1 - value to convert to lowercase
function toLower {
  local lc=`echo $1 | tr "[:upper:]" "[:lower:]"`
  echo "$lc"
}

#
# Function to lowercase a value and make it a legal DNS1123 name
# $1 - value to convert to lowercase
function toDNS1123Legal {
  local val=`echo $1 | tr "[:upper:]" "[:lower:]"`
  val=${val//"_"/"-"}
  echo "$val"
}

#
# Check the state of a persistent volume.
# $1 - name of volume
# $2 - expected state of volume
function checkPvState {

  echo "Checking if the persistent volume ${1:?} is ${2:?}"
  local pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
  attempts=0
  while [ ! "$pv_state" = "$2" ] && [ ! $attempts -eq 10 ]; do
    attempts=$((attempts + 1))
    sleep 1
    pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
  done
  if [ "$pv_state" != "$2" ]; then
    fail "The persistent volume state should be $2 but is $pv_state"
  fi
}

#
# Function to check if a persistent volume exists
# $1 - name of volume
function checkPvExists {

  echo "Checking if the persistent volume ${1} exists"
  PV_EXISTS=`kubectl get pv | grep ${1} | wc | awk ' { print $1; } '`
  if [ "${PV_EXISTS}" = "1" ]; then
    echo "The persistent volume ${1} already exists"
    PV_EXISTS="true"
  else
    echo "The persistent volume ${1} does not exist"
    PV_EXISTS="false"
  fi
}

#
# Function to check if a persistent volume claim exists
# $1 - name of persistent volume claim
# $2 - namespace
function checkPvcExists {
  echo "Checking if the persistent volume claim ${1} in namespace ${2} exists"
  PVC_EXISTS=`kubectl get pvc -n ${2} | grep ${1} | wc | awk ' { print $1; } '`
  if [ "${PVC_EXISTS}" = "1" ]; then
    echo "The persistent volume claim ${1} already exists in namespace ${2}"
    PVC_EXISTS="true"
  else
    echo "The persistent volume claim ${1} does not exist in namespace ${2}"
    PVC_EXISTS="false"
  fi
}

# Copy the inputs file from the command line into the output directory
# for the domain/operator unless the output directory already has an
# inputs file and the file is the same as the one from the commandline.
# $1 the inputs file from the command line
# $2 the file in the output directory that needs to be made the same as $1
function copyInputsFileToOutputDirectory {
  local from=$1
  local to=$2
  local doCopy="true"
  if [ -f "${to}" ]; then
    local difference=`diff ${from} ${to}`
    if [ -z "${difference}" ]; then
      # the output file already exists and is the same as the inputs file.
      # don't make a copy.
      doCopy="false"
    fi
  fi
  if [ "${doCopy}" = "true" ]; then
    cp ${from} ${to}
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
  # string on the colon to obtain the address. 
  local array=(${SVR_ADDR//:/ })
  K8S_IP="${array[1]/\/\//}"

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

  if [ "${domainHomeInImage}" == "true" ]; then
    if [ -z "${domainHomeImageBase}" ]; then
      fail "Please specify domainHomeImageBase in your input YAML"
    fi
  else
    if [ -z "${image}" ]; then
      fail "Please specify image in your input YAML"
    fi
  fi

  dcrOutput="${domainOutputDir}/domain.yaml"

  domainName=${domainUID}

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  exposeAnyChannelPrefix="${disabledPrefix}"
  if [ "${exposeAdminT3Channel}" = true ]; then
    exposeAdminT3ChannelPrefix="${enabledPrefix}"
    exposeAnyChannelPrefix="${enabledPrefix}"
    # set t3PublicAddress if not set
    if [ -z "${t3PublicAddress}" ]; then
      getKubernetesClusterIP
      t3PublicAddress="${K8S_IP}"
    fi
  else
    exposeAdminT3ChannelPrefix="${disabledPrefix}"
  fi

  if [ "${exposeAdminNodePort}" = true ]; then
    exposeAdminNodePortPrefix="${enabledPrefix}"
    exposeAnyChannelPrefix="${enabledPrefix}"
  else
    exposeAdminNodePortPrefix="${disabledPrefix}"
  fi

  # For some parameters, use the default value if not defined.
  if [ -z "${domainPVMountPath}" ]; then
    domainPVMountPath="/shared"
  fi

  if [ -z "${logHome}" ]; then
    logHome="${domainPVMountPath}/logs/${domainUID}"
  fi

  if [ -z "${persistentVolumeClaimName}" ]; then
    persistentVolumeClaimName="${domainUID}-weblogic-sample-pvc"
  fi

  if [ -z "${weblogicCredentialsSecretName}" ]; then
    weblogicCredentialsSecretName="${domainUID}-weblogic-credentials"
  fi

  if [ "${domainHomeInImage}" == "true" ]; then
    domainPropertiesOutput="${domainOutputDir}/domain.properties"
    domainHome="/u01/oracle/user_projects/domains/${domainName}"

    if [ -z $domainHomeImageBuildPath ]; then
      domainHomeImageBuildPath="./docker-images/OracleWebLogic/samples/12213-domain-home-in-image"
    fi

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

    if [ -z "${image}" ]; then
      # calculate the internal name to tag the generated image
      defaultImageName="`basename ${domainHomeImageBuildPath} | sed 's/^[0-9]*-//'`"
      baseTag=${domainHomeImageBase#*:}
      defaultImageName=${defaultImageName}:${baseTag:-"latest"}
      sed -i -e "s|%IMAGE_NAME%|${defaultImageName}|g" ${domainPropertiesOutput}
    else 
      sed -i -e "s|%IMAGE_NAME%|${image}|g" ${domainPropertiesOutput}
    fi
  else

    createJobOutput="${domainOutputDir}/create-domain-job.yaml"
    deleteJobOutput="${domainOutputDir}/delete-domain-job.yaml"

    if [ -z "${domainHome}" ]; then
      domainHome="${domainPVMountPath}/domains/${domainUID}"
    fi

    # Use the default value if not defined.
    if [ -z "${createDomainScriptsMountPath}" ]; then
      createDomainScriptsMountPath="/u01/weblogic"
    fi

    if [ -z "${createDomainScriptName}" ]; then
      createDomainScriptName="create-domain-job.sh"
    fi

    # Must escape the ':' value in image for sed to properly parse and replace
    image=$(echo ${image} | sed -e "s/\:/\\\:/g")

    # Generate the yaml to create the kubernetes job that will create the weblogic domain
    echo Generating ${createJobOutput}

    cp ${createJobInput} ${createJobOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${createJobOutput}
    sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${createJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE%:${image}:g" ${createJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE_PULL_POLICY%:${imagePullPolicy}:g" ${createJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${imagePullSecretName}:g" ${createJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${imagePullSecretPrefix}:g" ${createJobOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${createJobOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${createJobOutput}
    sed -i -e "s:%DOMAIN_HOME%:${domainHome}:g" ${createJobOutput}
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
    sed -i -e "s:%DOMAIN_ROOT_DIR%:${domainPVMountPath}:g" ${createJobOutput}
    sed -i -e "s:%CREATE_DOMAIN_SCRIPT_DIR%:${createDomainScriptsMountPath}:g" ${createJobOutput}
    sed -i -e "s:%CREATE_DOMAIN_SCRIPT%:${createDomainScriptName}:g" ${createJobOutput}

    # Generate the yaml to create the kubernetes job that will delete the weblogic domain_home folder
    echo Generating ${deleteJobOutput}

    cp ${deleteJobInput} ${deleteJobOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${deleteJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE%:${image}:g" ${deleteJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE_PULL_POLICY%:${imagePullPolicy}:g" ${deleteJobOutput}
    sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${deleteJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${imagePullSecretName}:g" ${deleteJobOutput}
    sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${imagePullSecretPrefix}:g" ${deleteJobOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${deleteJobOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${deleteJobOutput}
    sed -i -e "s:%DOMAIN_HOME%:${domainHome}:g" ${deleteJobOutput}
    sed -i -e "s:%DOMAIN_PVC_NAME%:${persistentVolumeClaimName}:g" ${deleteJobOutput}
    sed -i -e "s:%DOMAIN_ROOT_DIR%:${domainPVMountPath}:g" ${deleteJobOutput}
  fi

  if [ "${domainHomeInImage}" == "true" ]; then
    if [ "${logHomeOnPV}" == "true" ]; then
      logHomeOnPVPrefix="${enabledPrefix}"
    else
      logHomeOnPVPrefix="${disabledPrefix}"
    fi
  else
    logHomeOnPVPrefix="${enabledPrefix}"
    logHomeOnPV=true
  fi

  # Generate the yaml file for creating the domain resource
  echo Generating ${dcrOutput}

  cp ${dcrInput} ${dcrOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${dcrOutput}
  sed -i -e "s:%NAMESPACE%:$namespace:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_HOME%:${domainHome}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_HOME_IN_IMAGE%:${domainHomeInImage}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_POLICY%:${imagePullPolicy}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${imagePullSecretPrefix}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${imagePullSecretName}:g" ${dcrOutput}
  sed -i -e "s:%WEBLOGIC_CREDENTIALS_SECRET_NAME%:${weblogicCredentialsSecretName}:g" ${dcrOutput}
  sed -i -e "s:%INCLUDE_SERVER_OUT_IN_POD_LOG%:${includeServerOutInPodLog}:g" ${dcrOutput}
  sed -i -e "s:%LOG_HOME_ON_PV_PREFIX%:${logHomeOnPVPrefix}:g" ${dcrOutput}
  sed -i -e "s:%LOG_HOME_ENABLED%:${logHomeOnPV}:g" ${dcrOutput}
  sed -i -e "s:%LOG_HOME%:${logHome}:g" ${dcrOutput}
  sed -i -e "s:%SERVER_START_POLICY%:${serverStartPolicy}:g" ${dcrOutput}
  sed -i -e "s:%JAVA_OPTIONS%:${javaOptions}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_PVC_NAME%:${persistentVolumeClaimName}:g" ${dcrOutput}
  sed -i -e "s:%DOMAIN_ROOT_DIR%:${domainPVMountPath}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_ANY_CHANNEL_PREFIX%:${exposeAnyChannelPrefix}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_ADMIN_PORT_PREFIX%:${exposeAdminNodePortPrefix}:g" ${dcrOutput}
  sed -i -e "s:%ADMIN_NODE_PORT%:${adminNodePort}:g" ${dcrOutput}
  sed -i -e "s:%EXPOSE_T3_CHANNEL_PREFIX%:${exposeAdminT3ChannelPrefix}:g" ${dcrOutput}
  sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${dcrOutput}
  sed -i -e "s:%INITIAL_MANAGED_SERVER_REPLICAS%:${initialManagedServerReplicas}:g" ${dcrOutput}

  if [ "${domainHomeInImage}" == "true" ]; then
 
    # now we know which image to use, update the domain yaml file
    if [ -z $image ]; then
      sed -i -e "s|%WEBLOGIC_IMAGE%|${defaultImageName}|g" ${dcrOutput}
    else
      sed -i -e "s|%WEBLOGIC_IMAGE%|${image}|g" ${dcrOutput}
    fi
  else
    sed -i -e "s:%WEBLOGIC_IMAGE%:${image}:g" ${dcrOutput}
  fi

  # Remove any "...yaml-e" and "...properties-e" files left over from running sed
  rm -f ${domainOutputDir}/*.yaml-e
  rm -f ${domainOutputDir}/*.properties-e
}

#
# Function to create the domain recource
#
function createDomainResource {
  kubectl apply -f ${dcrOutput}

  attempts=0
  while [ "$DCR_AVAIL" != "1" ] && [ ! $attempts -eq 10 ]; do
    attempts=$((attempts + 1))
    sleep 1
    DCR_AVAIL=`kubectl get domain ${domainUID} -n ${namespace} | grep ${domainUID} | wc | awk ' { print $1; } '`
  done
  if [ "${DCR_AVAIL}" != "1" ]; then
    fail "The domain resource ${domainUID} was not found"
  fi
}

#
# Function to create a domain
# $1 - boolean value indicating the location of the domain home
#      true means domain home in image
#      false means domain home on PV
#
function createDomain {
  if [ "$#" != 1 ]; then
    fail "The function must be called with domainHomeInImage parameter."
  fi

  domainHomeInImage="${1}"
  if [ "true" != "${domainHomeInImage}" ] && [ "false" != "${domainHomeInImage}" ]; then
    fail "The value of domainHomeInImage must be true or false: ${domainHomeInImage}"
  fi

  # Setup the environment for running this script and perform initial validation checks
  initialize

  # Generate files for creating the domain
  createFiles

  # Check that the domain secret exists and contains the required elements
  validateDomainSecret

  # Validate the domain's persistent volume claim
  if [ "${doValidation}" == true ] && [ "${domainHomeInImage}" == false -o "${logHomeOnPV}" == true ]; then
    validateDomainPVC
  fi

  # Create the WebLogic domain home
  createDomainHome

  if [ "${executeIt}" = true ]; then
    createDomainResource
  fi

  # Print a summary
  printSummary
}

