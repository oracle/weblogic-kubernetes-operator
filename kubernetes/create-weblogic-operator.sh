#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  echo [ERROR] $1
  exit 1
}

#
# Parse the command line options
#
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
valuesInputFile="${scriptDir}/create-operator-inputs.yaml"
generateOnly=false
while getopts "ghi:" opt; do
  case $opt in
    g) generateOnly=true
    ;;
    i) valuesInputFile="${OPTARG}"
    ;;
    h) echo ./create-weblogic-operator.sh [-g] [-i file] [-h]
       echo
       echo -g Only generate the files to create the operator, do not execute them
       echo -i Parameter input file, defaults to kubernetes/create-operator-inputs.yaml
       echo -h Help
       exit
    ;;
    \?) fail "Invalid or missing command line option"
    ;;
  esac
done

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
# $1 - Output filename
#
function parseCommonInputs {
  exportValuesFile="/tmp/export-values.sh"
  parseYaml ${valuesInputFile} ${exportValuesFile}

  if [ ! -f ${exportValuesFile} ]; then
    echo Unable to locate the parsed output of ${valuesInputFile}.
    fail 'The file ${exportValuesFile} could not be found.'
  fi

  # Define the environment variables that will be used to fill in template values
  echo Input parameters being used to create the WebLogic operator
  cat ${exportValuesFile}
  echo
  source ${exportValuesFile}
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
    validationError "Unable to locate the file ${valuesInputFile}. \nThis file contains the input parameters required to create the operator."
  fi

  oprInput="${scriptDir}/internal/weblogic-operator-template.yaml"
  oprOutput="${scriptDir}/weblogic-operator.yaml"
  if [ ! -f ${oprInput} ]; then
    validationError "The template file ${oprInput} for generating the weblogic operator was not found"
  fi

  genOprCertScript="${scriptDir}/internal/generate-weblogic-operator-cert.sh"
  if [ ! -f ${genOprCertScript} ]; then
    validationError "The file ${genOprCertScript} for generating the weblogic operator certificates was not found"
  fi

  genSecPolicyScript="${scriptDir}/internal/generate-security-policy.sh"
  if [ ! -f ${genSecPolicyScript} ]; then
    validationError "The file ${genSecPolicyScript} for generating the security policy was not found"
  fi

  # Validation checks for elk integration
 
 elasticsearchYaml="${scriptDir}/internal/elasticsearch.yaml"
  if [ ! -f ${elasticsearchYaml} ]; then
    validationError "The file ${elasticsearchYaml} necessary for elk deployment was not found"
  fi

  kibanaYaml="${scriptDir}/internal/kibana.yaml"
  if [ ! -f ${kibanaYaml} ]; then
    validationError "The file ${kibanaYaml} necessary for elk deployment was not found"
  fi

  elkpvTemplateYaml="${scriptDir}/internal/elk-pv-template.yaml"
  elkpvYaml="${scriptDir}/elk-pv-deployment.yaml"
  if [ ! -f ${elkpvTemplateYaml} ]; then
    validationError "The template file ${elkpvTemplateYaml} for generating elk deployment was not found"
  fi

  elkpvcTemplateYaml="${scriptDir}/internal/elk-pvc-template.yaml"
  elkpvcYaml="${scriptDir}/elk-pvc-deployment.yaml"     
  if [ ! -f ${elkpvcTemplateYaml} ]; then
    validationError "The template file ${elkpvcTemplateYaml} for generating elk deployment was not found"
  fi
	
  logstashTemplateYaml="${scriptDir}/internal/logstash-template.yaml"
  logstashYaml="${scriptDir}/logstash-deployment.yaml"     
  if [ ! -f ${logstashTemplateYaml} ]; then
    validationError "The template file ${logstashTemplateYaml} for generating elk deployment was not found"
  fi

  failIfValidationErrors

  # Parse the common inputs file
  parseCommonInputs

  validateInputParamsSpecified serviceAccount namespace targetNamespaces image imagePullPolicy elkIntegrationEnabled

  validateRemoteDebugNodePort

  validateJavaLoggingLevel

  validateExternalRest

  validateImagePullSecretName

  failIfValidationErrors
}

#
# Function to validate that the remote debug node port has been properly configured
#
function validateRemoteDebugNodePort {

  # Validate that remoteDebugNodePortEnabled  was specified
  validateInputParamsSpecified remoteDebugNodePortEnabled 

  if [ "${remoteDebugNodePortEnabled}" = true ]; then
    # Validate that the required sub options were specified
    validateInputParamsSpecified externalDebugHttpPort internalDebugHttpPort
  fi
}

#
# Function to validate the image pull secret name
#
function validateImagePullSecretName {
  IMAGE_PULL_SECRET_EXISTS=false
  if [ ! -z ${imagePullSecretName} ]; then
    IMAGE_PULL_SECRET_EXISTS=true
    imagePullSecretPrefix=""
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
  if [ "${IMAGE_PULL_SECRET_EXISTS}" = true ]; then
    validateSecretExists ${imagePullSecretName} ${namespace}
    failIfValidationErrors
  fi
}

#
# Function to validate a kubernetes secret exists
# $1 - the name of the secret
# $2 - namespace
function validateSecretExists {
  # Verify the secret exists
  echo "Checking to see if the secret ${1} exists in namespace ${2}"
  local SECRET=`kubectl get secret ${1} -n ${2} | grep ${1} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    validationError "The registry secret ${1} was not found in namespace ${2}"
  fi
}

#
# Function to validate that the java logging level has been properly configured
#
function validateJavaLoggingLevel {

  # Validate that javaLoggingLevel was specified
  validateInputParamsSpecified javaLoggingLevel

  # And validate that it's one of the allowed logging levels
  if [ ! -z "${javaLoggingLevel}" ]; then
    SEVERE="SEVERE"
    WARNING="WARNING"
    INFO="INFO"
    CONFIG="CONFIG"
    FINE="FINE"
    FINER="FINER"
    FINEST="FINEST"
    if [ $javaLoggingLevel != $SEVERE  ] && \
       [ $javaLoggingLevel != $WARNING ] && \
       [ $javaLoggingLevel != $INFO    ] && \
       [ $javaLoggingLevel != $CONFIG  ] && \
       [ $javaLoggingLevel != $FINE    ] && \
       [ $javaLoggingLevel != $FINER   ] && \
       [ $javaLoggingLevel != $FINEST  ]; then
      validationError "Invalid javaLoggingLevel: \"${javaLoggingLevel}\". Valid values are $SEVERE, $WARNING, $INFO, $CONFIG, $FINE, $FINER and $FINEST."
    fi
  fi
}

#
# Function to validate that external REST has been properly configured
#
function validateExternalRest {

  # Validate that externalRestOption was specified
  validateInputParamsSpecified externalRestOption

  if [ ! -z ${externalRestOption} ]; then
    # Validate the specified externalRestOption value and any sub options that it requires
    EXT_REST_OPT_NONE="none"
    EXT_REST_OPT_SELF_SIGNED="self-signed-cert"
    EXT_REST_OPT_CUSTOM="custom-cert"
    case ${externalRestOption} in
      ${EXT_REST_OPT_NONE})
        echo The WebLogic Operator REST interface will not be externally exposed
      ;;
      ${EXT_REST_OPT_SELF_SIGNED})
        echo The WebLogic operator REST interface is externally exposed using a generated self-signed certificate that contains the customer-provided list of subject alternative names.
        validateInputParamsSpecified externalSans externalRestHttpsPort
      ;;
      ${EXT_REST_OPT_CUSTOM})
        echo The WebLogic operator REST interface is externally exposed using a customer-provided certificate and private key pair.
        validateInputParamsSpecified externalOperatorCert externalOperatorKey externalRestHttpsPort
      ;;
      *)
        validationError "Invalid externalRestoption: \"${externalRestOption}\".  Valid values are $EXT_REST_OPT_NONE, $EXT_REST_OPT_SELF_SIGNED and $EXT_REST_OPT_CUSTOM."
      ;;
    esac
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
    if [ -f $3 ]; then
      kubectl delete -f $3
    else
      Unable to delete $2 because the file $3 was not found
    fi
  fi
}

#
# Function to create certificates
#
function createCertificates {

  generatedCertDir="${scriptDir}/internal/weblogic-operator-cert"
  generatedCertFile="${generatedCertDir}/weblogic-operator.cert.pem"
  generatedKeyFile="${generatedCertDir}/weblogic-operator.key.pem"

  # Always generate a self-signed cert for the internal operator REST port
  internal_host="internal-weblogic-operator-service"
  internal_sans="DNS:${internal_host},DNS:${internal_host}.${namespace},DNS:${internal_host}.${namespace}.svc,DNS:${internal_host}.${namespace}.svc.cluster.local"
  echo "Generating a self-signed certificate for the operator's internal https port with the subject alternative names ${internal_sans}"
  ${genOprCertScript} ${internal_sans}

  # copy the cert and key into internal_cert_data and internal_key_data then remove them
  internal_cert_data=`base64 -i $generatedCertFile | tr -d '\n'`
  internal_key_data=`base64 -i $generatedKeyFile | tr -d '\n'`
  rm -rf $generatedCertDir

  if [ "${externalRestOption}" = "${EXT_REST_OPT_SELF_SIGNED}" ]; then
    # EXT_REST_OPT_SELF_SIGNED was specified.  Generate a self signed cert and use it.
    echo "Generating a self-signed certificate for the operator's external ssl port with the subject alternative names ${externalSans}"
    ${genOprCertScript} ${externalSans}
    # copy the generated cert and key into external_cert_data and external_key_data then remove them
    external_cert_data=`base64 -i $generatedCertFile | tr -d '\n'`
    external_key_data=`base64 -i $generatedKeyFile | tr -d '\n'`
    rm -rf $generatedCertDir
  elif [ "${externalRestOption}" = "${EXT_REST_OPT_CUSTOM}" ]; then
    # EXT_REST_OPT_CUSTOM was specified.  Use the provided cert and key.
    external_cert_data="${externalOperatorCert}"
    external_key_data="${externalOperatorKey}"
  elif [ "${externalRestOption}" = "${EXT_REST_OPT_NONE}" ]; then
    # EXT_REST_OPT_NONE was specified.  Don't use any cert and key.
    external_cert_data="\"\""
    external_key_data="\"\""
  fi
}

#
# Function to generate the yaml files for creating a domain
#
function createYamlFiles {
  # Generate the yaml to create the WebLogic operator
  echo Generating ${oprOutput}

  enabledPrefix=""     # uncomment the feature
  disabledPrefix="# "  # comment out the feature

  # only create the external operator service if either the external Operator REST
  # api or the remote debugging port is enabled.
  if [ "${externalRestOption}" != "${EXT_REST_OPT_NONE}" ] || [ "${remoteDebugNodePortEnabled}" = true ]; then
    externalOperatorServicePrefix="${enabledPrefix}"
  else
    externalOperatorServicePrefix="${disabledPrefix}"
  fi

  if [ "${remoteDebugNodePortEnabled}" = true ]; then
    remoteDebugNodePortPrefix="${enabledPrefix}"
  else
    remoteDebugNodePortPrefix="${disabledPrefix}"
  fi

  if [ "${elkIntegrationEnabled}" = true ]; then
    elkIntegrationPrefix="${enabledPrefix}"
  else
    elkIntegrationPrefix="${disabledPrefix}"
  fi

  if [ "${externalRestOption}" = "${EXT_REST_OPT_NONE}" ]; then
    externalRestNodePortPrefix="${disabledPrefix}"
  else
    externalRestNodePortPrefix="${enabledPrefix}"
  fi

  cp ${oprInput} ${oprOutput}
  sed -i -e "s|%NAMESPACE%|$namespace|g" ${oprOutput}
  sed -i -e "s|%TARGET_NAMESPACES%|$targetNamespaces|g" ${oprOutput}
  sed -i -e "s|%ACCOUNT_NAME%|$serviceAccount|g" ${oprOutput}
  sed -i -e "s|%IMAGE%|$image|g" ${oprOutput}
  sed -i -e "s|%IMAGE_PULL_POLICY%|$imagePullPolicy|g" ${oprOutput}
  sed -i -e "s:%DOCKER_STORE_REGISTRY_SECRET%:${imagePullSecretName}:g" ${oprOutput}
  sed -i -e "s:%IMAGE_PULL_SECRET_PREFIX%:${imagePullSecretPrefix}:g" ${oprOutput}
  sed -i -e "s|%EXTERNAL_OPERATOR_SERVICE_PREFIX%|$externalOperatorServicePrefix|g" ${oprOutput}
  sed -i -e "s|%EXTERNAL_REST_HTTPS_PORT%|$externalRestHttpsPort|g" ${oprOutput}
  sed -i -e "s|%EXTERNAL_DEBUG_HTTP_PORT%|$externalDebugHttpPort|g" ${oprOutput}
  sed -i -e "s|%INTERNAL_DEBUG_HTTP_PORT%|$internalDebugHttpPort|g" ${oprOutput}
  sed -i -e "s|%REMOTE_DEBUG_NODE_PORT_PREFIX%|$remoteDebugNodePortPrefix|g" ${oprOutput}
  sed -i -e "s|%EXTERNAL_REST_NODE_PORT_PREFIX%|$externalRestNodePortPrefix|g" ${oprOutput}
  sed -i -e "s|%JAVA_LOGGING_LEVEL%|$javaLoggingLevel|g" ${oprOutput}
  sed -i -e "s|%ELK_INTEGRATION_PREFIX%|$elkIntegrationPrefix|g" ${oprOutput}
  sed -i -e "s|%EXTERNAL_CERT_DATA%|$external_cert_data|g" ${oprOutput}
  sed -i -e "s|%EXTERNAL_KEY_DATA%|$external_key_data|g" ${oprOutput}
  sed -i -e "s|%INTERNAL_CERT_DATA%|$internal_cert_data|g" ${oprOutput}
  sed -i -e "s|%INTERNAL_KEY_DATA%|$internal_key_data|g" ${oprOutput}

  # Create the rbac.yaml file
  rbacFile="${scriptDir}/rbac.yaml"
  roleName="weblogic-operator-namespace-role"
  roleBinding="weblogic-operator-rolebinding"
  clusterRole="weblogic-operator-cluster-role"
  clusterRoleBinding="${namespace}-operator-rolebinding"

  echo Running the rbac customization script
  ${genSecPolicyScript} ${serviceAccount} ${namespace} "${targetNamespaces}" -o ${rbacFile}

  # Create the ELK related files if the option is enabled
  ELK_PV="elk-pv-${namespace}"
  ELK_PVC="elk-pvc"
  if [ "${elkIntegrationEnabled}" = true ]; then
    cp ${elkpvTemplateYaml} ${elkpvYaml}
    sed -i -e "s|%NAMESPACE%|$namespace|g" ${elkpvYaml}
    sed -i -e "s|%PERSISTENTVOLUME%|$elkPersistentVolume|g" ${elkpvYaml}

    cp ${elkpvcTemplateYaml} ${elkpvcYaml}
    sed -i -e "s|%NAMESPACE%|$namespace|g" ${elkpvcYaml}

    cp ${logstashTemplateYaml} ${logstashYaml}
    sed -i -e "s|%NAMESPACE%|$namespace|g" ${logstashYaml}
  fi

}

#
# Function to create the namespace of the operator
# $1 - name of namespace
function createNamespace {
  NS_NAME="$1"

  echo Checking to see if the namespace ${NS_NAME} already exists
  nsExists=false
  NS=`kubectl get namespace ${NS_NAME} | grep ${NS_NAME} | wc | awk ' { print $1; }'`
  if [ "$NS" = "1" ]; then
    echo The namespace ${NS_NAME} already exists
    nsExists=true
  fi

  if [ "${nsExists}" = false ]; then
    echo Creating the namespace ${NS_NAME}
    kubectl create namespace ${NS_NAME}

    echo Checking if the namespace was successfully created
    NS=`kubectl get namespace ${NS_NAME} | grep ${NS_NAME} | wc | awk ' { print $1; }'`
    if [ "$NS" != "1" ]; then
      fail "The namespace ${NS_NAME} was not successfully created"
    fi
  fi
}

#
# Function to create the target namespaces (if they don't already exist)
#
function createTargetNamespaces {

  # Loop through the comma separated list of target namespaces
  for i in ${targetNamespaces//,/ }
  do
    echo Checking the target namespace $i
    createNamespace $i
  done
}

#
# Function to create the service account
#
function createServiceAccount {

  echo Checking to see if the service account ${serviceAccount} already exists
  saExists=false
  SA=`kubectl get serviceaccount ${serviceAccount} -n ${namespace} | grep ${serviceAccount} | wc | awk ' { print $1; }'`
  if [ "$SA" = "1" ]; then
    echo The service account ${serviceAccount} already exists
    saExists=true
  fi

  if [ "${saExists}" = false ]; then
    echo Creating the service account ${serviceAccount}
    kubectl create serviceaccount ${serviceAccount} -n ${namespace}

    echo Checking if the service account was successfully created
    SA=`kubectl get serviceaccount ${serviceAccount} -n ${namespace} | grep ${serviceAccount} | wc | awk ' { print $1; } '`
    if [ "$SA" != "1" ]; then
        fail "The service account ${serviceAccount} was not succesfully created"
    fi
  fi
}

#
# Function to setup the rbac
#
function setup_rbac {

  echo Applying the generated file ${rbacFile}
  kubectl apply -f ${rbacFile}

  echo Checking the cluster role ${roleName} was created
  ROLE=`kubectl get clusterroles -n ${namespace} | grep ${roleName} | wc | awk ' { print $1; } '`
  if [ "$ROLE" != "1" ]; then
      fail "The cluster role ${roleName} was not created"
  fi

  echo Checking role binding ${roleBinding} was created for each target namespace
  # Loop through the comma separated list of target namespaces
  for i in ${targetNamespaces//,/ }
  do
    echo Checking role binding ${roleBinding} for namespace ${i}
    ROLEBINDING=`kubectl get rolebindings -n ${i} | grep ${roleBinding} | wc | awk ' { print $1; } '`
    if [ "$ROLEBINDING" != "1" ]; then
        fail "The role binding ${roleBinding} was not created for namespace ${i}"
    fi
  done

  echo Checking the cluster role ${clusterRole} was created
  CLUSTERROLE=`kubectl get clusterroles | grep ${clusterRole} | wc | awk ' { print $1; } '`
  if [ "$CLUSTERROLE" != "2" ]; then
      fail "The cluster role ${clusterRole} was not created"
  fi

  echo Checking the cluster role bindings ${clusterRoleBinding} were created
  CLUSTERROLEBINDING=`kubectl get clusterrolebindings | grep ${clusterRoleBinding} | wc | awk ' { print $1; } '`
  if [ "$CLUSTERROLEBINDING" != "4" ]; then
    fail "The cluster role binding ${clusterRoleBinding} was not created"
  fi

}

function check_pv {

    echo "Checking if the persistent volume ${1:?} is ${2:?}"
    pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
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
# Create ELK persistent valume and claim
#
function create_elk_persistent_volume {

  # Check if the persistent volume claim is already available
  skipPvCreate=false
  echo Checking if the persistent volume ${ELK_PV} already exists
  PV_AVAILABLE=`kubectl get pv | grep ${ELK_PV} | wc | awk ' { print $1; } '`
  if [ "${PV_AVAILABLE}" = "1" ]; then
    echo The persistent volume ${ELK_PV} already exists and will not be re-created
    skipPvCreate=true
  fi

  if [ "${skipPvCreate}" = false ]; then
    echo Creating the persistent volume ${ELK_PV}
    kubectl apply -f ${elkpvYaml}
    check_pv ${ELK_PV} Available
  fi

  # Check if the persistent volume claim is already available
  skipPvcCreate=false
  echo Checking if the persistent volume claim ${ELK_PVC} already exists
  PVC_AVAILABLE=`kubectl get pvc -n ${namespace} | grep ${ELK_PVC} | wc | awk ' { print $1; } '`
  if [ "${PVC_AVAILABLE}" = "1" ]; then
    echo The persistent volume claim ${ELK_PVC} already exists and will not be re-created
    skipPvcCreate=true
  fi

  if [ "${skipPvcCreate}" = false ]; then
    echo Creating the persistent volume claim ${ELK_PVC}
    kubectl apply -f ${elkpvcYaml}
  fi

   check_pv ${ELK_PV} Bound
}


#
# Deploy elk
#
function deploy_elk {

  echo 'Deploy ELK...'
  kubectl apply -f ${logstashYaml}
  kubectl apply -f ${elasticsearchYaml}
  kubectl apply -f ${kibanaYaml}
}

function verify_elk_integration {

  echo 'verify ELK integration (nyi)'
}

#
# Function to deploy the WebLogic operator
#
function deployOperator {

  echo Applying the file ${oprOutput}
  kubectl apply -f ${oprOutput}

  echo Waiting for operator deployment to be ready...
  AVAILABLE="0"
  max=10
  count=1
  while [ "$AVAILABLE" != "1" -a $count -lt $max ] ; do
    sleep 30
    AVAILABLE=`kubectl get deploy weblogic-operator -n ${namespace} -o jsonpath='{.status.availableReplicas}'`
    echo "status is $AVAILABLE, iteration $count of $max"
    count=`expr $count + 1`
  done

  if [ "$AVAILABLE" != "1" ]; then
    kubectl get deploy weblogic-operator -n ${namespace}
    kubectl describe deploy weblogic-operator -n ${namespace}
    kubectl describe pods -n ${namespace}
    fail "The WebLogic operator deployment is not available, after waiting 300 seconds"
  fi

  echo Checking the operator labels
  LABEL=`kubectl describe deploy weblogic-operator -n ${namespace} | grep "^Labels:" | awk ' { print $2; } '`
  if [ "$LABEL" != "app=weblogic-operator" ]; then
    fail "The weblogic-operator deployment should have the label app=weblogic-operator"
  fi

  echo "Checking the operator pods"
  REPLICA_SET=`kubectl describe deploy weblogic-operator -n ${namespace} | grep NewReplicaSet: | awk ' { print $2; }'`
  if [ "$REPLICA_SET" = "<none>" ]; then  
     # Look differently if replica set was not found.
     REPLICA_SET=`kubectl describe deploy weblogic-operator -n ${namespace} | grep OldReplicaSets: | awk ' { print $2; }'`
  fi 
 
  POD_TEMPLATE=`kubectl describe rs ${REPLICA_SET} -n ${namespace} | grep ^Name: | awk ' { print $2; } '`
  PODS=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | wc | awk ' { print $1; } '`
  POD=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | awk ' { print $1; } '`

  if [ "$PODS" != "1" ]; then
    fail "There should be one operator pod running"
  fi

  echo Checking the operator Pod status
  POD_STATUS=`kubectl describe pod $POD -n ${namespace} | grep "^Status:" | awk ' { print $2; } '`
  if [ "$POD_STATUS" != "Running" ]; then
    fail "The operator pod status should be Running"
  fi

}

#
# Function to output to the console a summary of the work completed
#
function outputJobSummary {
  echo ""
  echo "The Oracle WebLogic Server Kubernetes Operator is deployed, the following namespaces are being managed: ${targetNamespaces}"
  echo ""
  echo "The following files were generated:"
  echo "  ${oprOutput}"
  echo "  ${rbacFile}"
  if [ "${elkIntegrationEnabled}" = true ]; then
     echo "  ${elkpvYaml}"
     echo "  ${elkpvcYaml}"
     echo "  ${logstashYaml}"
  fi
}

#
# Perform the following sequence of steps to create the WebLogic operator
#

# Setup the environment for running this script and perform initial validation checks
initialize

# Create certificates
createCertificates

# Generate the yaml files for creating the operator
createYamlFiles

# All done if the generate only option is true
if [ "${generateOnly}" = false ]; then

  # Create the operator namespace
  createNamespace ${namespace}

  # Create the target namespaces
  createTargetNamespaces

  # Create the service account
  createServiceAccount
  
  if [ "${elkIntegrationEnabled}" = true ]; then
     # Deploy elk persistent volume and claim
     create_elk_persistent_volume
  fi     

  # Setup rbac
  setup_rbac

  # Deploy the WebLogic operator
  deployOperator

  if [ "${elkIntegrationEnabled}" = true ]; then
     # Deploy elk
     deploy_elk
  fi

  # Output a job summary
  outputJobSummary
fi
echo ""
echo Completed
