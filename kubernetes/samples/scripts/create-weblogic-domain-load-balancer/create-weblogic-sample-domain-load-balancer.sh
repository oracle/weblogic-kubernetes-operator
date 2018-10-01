#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates load balancer resources for the sample domain
#
#  The creation inputs can be customized by editing create-weblogic-sample-domain-load-balancer-inputs.yaml
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
  echo "  -i Parameter input file, must be specified."
  echo "  -e Also create the resources in the generated yaml files"
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

  if [ ! -z "${loadBalancer}" ]; then
    case ${loadBalancer} in
      "TRAEFIK")
        fileList="weblogic-sample-domain-traefik.yaml \
                  weblogic-sample-domain-traefik-security.yaml "
      ;;
      "APACHE")
        fileList="weblogic-sample-domain-apache.yaml \
                  weblogic-sample-domain-apache-security.yaml "
      ;;
      "VOYAGER")
        fileList="weblogic-sample-domain-voyager.yaml \
                  weblogic-sample-domain-voyager-operator.yaml \
                  weblogic-sample-domain-voyager-operator-security.yaml"
      ;;
      "NONE")
      ;;
      *)
        validationError "Invalid value for loadBalancer: ${loadBalancer}. Valid values are APACHE, TRAEFIK, VOYAGER and NONE."
      ;;
    esac
  fi
  validateOutputDir \
    ${domainOutputDir} \
    ${valuesInputFile} \
    create-weblogic-sample-domain-load-balancer-inputs.yaml \
    ${fileList}
}

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/create-weblogic-sample-domain-load-balander-inputs.yaml)."
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

  traefikSecurityInput="${scriptDir}/weblogic-sample-domain-traefik-security-template.yaml"
  if [ ! -f ${traefikSecurityInput} ]; then
    validationError "The file ${traefikSecurityInput} for generating the traefik RBAC was not found"
  fi

  traefikInput="${scriptDir}/weblogic-sample-domain-traefik-template.yaml"
  if [ ! -f ${traefikInput} ]; then
    validationError "The template file ${traefikInput} for generating the traefik deployment was not found"
  fi

  apacheSecurityInput="${scriptDir}/weblogic-sample-domain-apache-security-template.yaml"
  if [ ! -f ${apacheSecurityInput} ]; then
    validationError "The file ${apacheSecurityInput} for generating the apache-webtier RBAC was not found"
  fi

  apacheInput="${scriptDir}/weblogic-sample-domain-apache-template.yaml"
  if [ ! -f ${apacheInput} ]; then
    validationError "The template file ${apacheInput} for generating the apache-webtier deployment was not found"
  fi
  
  voyagerOperatorInput="${scriptDir}/weblogic-sample-domain-voyager-operator.yaml"
  if [ ! -f ${voyagerOperatorInput} ]; then
    validationError "The file ${voyagerOperatorInput} for Voyager Operator was not found"
  fi

  voyagerSecurityInput="${scriptDir}/weblogic-sample-domain-voyager-operator-security.yaml"
  if [ ! -f ${voyagerSecurityInput} ]; then
    validationError "The file ${voyagerSecurityInput} for generating the Voyager RBAC was not found"
  fi

  voyagerIngressInput="${scriptDir}/weblogic-sample-domain-voyager-ingress-template.yaml"
  if [ ! -f ${voyagerIngressInput} ]; then
    validationError "The template file ${voyagerIngressInput} for generating the Voyager Ingress was not found"
  fi

  failIfValidationErrors

  # Parse the commonn inputs file
  parseCommonInputs
  validateInputParamsSpecified \
    domainName \
    adminServerName \
    domainUID \
    clusterName \
    namespace \
    version

  validateIntegerInputParamsSpecified \
    managedServerPort \
    loadBalancerWebPort \
    loadBalancerDashboardPort

  export requiredInputsVersion="create-weblogic-sample-domain-load-balancer-inputs-v1"
  validateVersion 
  validateDomainUid
  validateNamespace
  validateClusterName
  validateLoadBalancer
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
  copyInputsFileToOutputDirectory ${valuesInputFile} "${domainOutputDir}/create-weblogic-sample-domain-load-balancer-inputs.yaml"

  traefikSecurityOutput="${domainOutputDir}/weblogic-sample-domain-traefik-security.yaml"
  traefikOutput="${domainOutputDir}/weblogic-sample-domain-traefik.yaml"
  apacheOutput="${domainOutputDir}/weblogic-sample-domain-apache.yaml"
  apacheSecurityOutput="${domainOutputDir}/weblogic-sample-domain-apache-security.yaml"
  voyagerSecurityOutput="${domainOutputDir}/weblogic-sample-domain-voyager-operator-security.yaml"
  voyagerOperatorOutput="${domainOutputDir}/weblogic-sample-domain-voyager-operator.yaml"
  voyagerIngressOutput="${domainOutputDir}/weblogic-sample-domain-voyager-ingress.yaml"

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  if [ "${loadBalancer}" = "TRAEFIK" ]; then
    # Traefik file
    cp ${traefikInput} ${traefikOutput}
    echo Generating ${traefikOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${traefikOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${traefikOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikOutput}
    sed -i -e "s:%CLUSTER_NAME_SVC%:${clusterNameSVC}:g" ${traefikOutput}
    sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${traefikOutput}
    sed -i -e "s:%LOAD_BALANCER_DASHBOARD_PORT%:$loadBalancerDashboardPort:g" ${traefikOutput}

    # Traefik security file
    cp ${traefikSecurityInput} ${traefikSecurityOutput}
    echo Generating ${traefikSecurityOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${traefikSecurityOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${traefikSecurityOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${traefikSecurityOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${traefikSecurityOutput}
    sed -i -e "s:%CLUSTER_NAME_SVC%:${clusterNameSVC}:g" ${traefikSecurityOutput}
  fi

  if [ "${loadBalancer}" = "APACHE" ]; then
    # Apache file
    cp ${apacheInput} ${apacheOutput}
 
    echo Generating ${apacheOutput}

    if [ "${loadBalancerExposeAdminPort}" = "true" ]; then
      enableLoadBalancerExposeAdminPortPrefix="${enabledPrefix}"
    else
      enableLoadBalancerExposeAdminPortPrefix="${disabledPrefix}"
    fi

    enableLoadBalancerVolumePathPrefix="${disabledPrefix}"
    apacheConfigFileName="custom_mod_wl_apache.conf"
    if [ ! -z "${loadBalancerVolumePath}" ]; then
      if [ ! -d ${loadBalancerVolumePath} ]; then
        echo -e "\nERROR - The specified loadBalancerVolumePath $loadBalancerVolumePath does not exist! \n"
        fail "Exiting due to a validation error"
      elif [ ! -f ${loadBalancerVolumePath}/${apacheConfigFileName} ]; then
        echo -e "\nERROR - The required file ${apacheConfigFileName} does not exist under the specified loadBalancerVolumePath $loadBalancerVolumePath! \n"
        fail "Exiting due to a validation error"
      else
        enableLoadBalancerVolumePathPrefix="${enabledPrefix}"
        sed -i -e "s:%LOAD_BALANCER_VOLUME_PATH%:${loadBalancerVolumePath}:g" ${apacheOutput}

      fi
    fi

    sed -i -e "s:%ENABLE_LOAD_BALANCER_EXPOSE_ADMIN_PORT%:${enableLoadBalancerExposeAdminPortPrefix}:g" ${apacheOutput}
    sed -i -e "s:%ENABLE_LOAD_BALANCER_VOLUME_PATH%:${enableLoadBalancerVolumePathPrefix}:g" ${apacheOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${apacheOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${apacheOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${apacheOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${apacheOutput}
    sed -i -e "s:%CLUSTER_NAME_SVC%:${clusterNameSVC}:g" ${apacheOutput}
    sed -i -e "s:%ADMIN_SERVER_NAME%:${adminServerName}:g" ${apacheOutput}
    sed -i -e "s:%ADMIN_PORT%:${adminPort}:g" ${apacheOutput}
    sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${apacheOutput}
    sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${apacheOutput}
    sed -i -e "s:%WEB_APP_PREPATH%:$loadBalancerAppPrepath:g" ${apacheOutput}
 
    # Apache security file
    cp ${apacheSecurityInput} ${apacheSecurityOutput}
    echo Generating ${apacheSecurityOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${apacheSecurityOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${apacheSecurityOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${apacheSecurityOutput}
  fi

  if [ "${loadBalancer}" = "VOYAGER" ]; then
    # Voyager Operator Security yaml file
    cp ${voyagerSecurityInput} ${voyagerSecurityOutput}
    # Voyager Operator yaml file
    cp ${voyagerOperatorInput} ${voyagerOperatorOutput}
    # Voyager Ingress file
    cp ${voyagerIngressInput} ${voyagerIngressOutput}
    echo Generating ${voyagerIngressOutput}
    sed -i -e "s:%NAMESPACE%:$namespace:g" ${voyagerIngressOutput}
    sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${voyagerIngressOutput}
    sed -i -e "s:%DOMAIN_NAME%:${domainName}:g" ${voyagerIngressOutput}
    sed -i -e "s:%CLUSTER_NAME%:${clusterName}:g" ${voyagerIngressOutput}
    sed -i -e "s:%CLUSTER_NAME_SVC%:${clusterNameSVC}:g" ${voyagerIngressOutput}
    sed -i -e "s:%MANAGED_SERVER_PORT%:${managedServerPort}:g" ${voyagerIngressOutput}
    sed -i -e "s:%LOAD_BALANCER_WEB_PORT%:$loadBalancerWebPort:g" ${voyagerIngressOutput}
    sed -i -e "s:%LOAD_BALANCER_DASHBOARD_PORT%:$loadBalancerDashboardPort:g" ${voyagerIngressOutput}
  fi

  # Remove any "...yaml-e" files left over from running sed
  rm -f ${domainOutputDir}/*.yaml-e
}

#
# Deploy Voyager/HAProxy load balancer
#
function startupVoyagerLoadBalancer {
  createVoyagerOperator ${voyagerSecurityOutput} ${voyagerOperatorOutput}
  createVoyagerIngress ${voyagerIngressOutput} ${namespace} ${domainUID}
}

#
# Deploy traefik load balancer
#
function startupTraefikLoadBalancer {

  traefikName="${domainUID}-${clusterNameSVC}-traefik"

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
function startupApacheLoadBalancer {

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
  if [ "${loadBalancer}" = "TRAEFIK" ] || [ "${loadBalancer}" = "VOYAGER" ]; then
    echo "The load balancer for cluster '${clusterName}' is available at http:${K8S_IP}:${loadBalancerWebPort}/ (add the application path to the URL)"
    echo "The load balancer dashboard for cluster '${clusterName}' is available at http:${K8S_IP}:${loadBalancerDashboardPort}"
    echo ""
  elif [ "${loadBalancer}" = "APACHE" ]; then
    echo "The apache load balancer for '${domainUID}' is available at http:${K8S_IP}:${loadBalancerWebPort}/ (add the application path to the URL)"

  fi
  echo "The following files were generated:"
  echo "  ${domainOutputDir}/create-weblogic-sample-domain-load-balander-inputs.yaml"
  echo "  ${domainPVOutput}"
  echo "  ${domainPVCOutput}"
  echo "  ${createJobOutput}"
  echo "  ${dcrOutput}"
  if [ "${loadBalancer}" = "TRAEFIK" ]; then
    echo "  ${traefikSecurityOutput}"
    echo "  ${traefikOutput}"
  elif [ "${loadBalancer}" = "APACHE" ]; then
    echo "  ${apacheSecurityOutput}"
    echo "  ${apacheOutput}"
  elif [ "${loadBalancer}" = "VOYAGER" ]; then
    echo "  ${voyagerOperatorOutput}"
    echo "  ${voyagerSecurityOutput}"
    echo "  ${voyagerIngressOutput}"
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
if [ "${executeIt}" = true ]; then
  # Setup load balancer
  if [ "${loadBalancer}" = "TRAEFIK" ]; then
    startupTraefikLoadBalancer
  elif [ "${loadBalancer}" = "APACHE" ]; then
    startupApacheLoadBalancer
  elif [ "${loadBalancer}" = "VOYAGER" ]; then
    startupVoyagerLoadBalancer
  fi
fi

# Output a job summary
printSummary

echo 
echo Completed


