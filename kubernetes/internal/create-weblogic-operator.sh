#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This internal script is used to generate the yaml files for the operator.
# Also, if -g is not specified, this script then loads these yaml files into kubernetes.
#
# This requires generating certificates and keys, and validating
# whether secrets have been registered with kubectl.
#
# Also, the script needs to be unit tested in an environment where
# keytool, openssl, kubectl and kubernetes are not available.  This
# means that we need a way to mock this functionality.
#
# To accomplish this, the create split has been split into:
#  - this internal script that abstracts away generating certs, validating secrets, ...
#  - the public script that customers call.  it implements the abstracted behavior by calling keytool, ...
#  - a unit testing script that only the tests call.  it implements the abstracted behavior by mocking it out
#
# The caller of this script must define:
#   createScript shell variable that has the full pathname of the script calling this script
#   defaultOperatorInputsFile shell variable that has the full pathname to kubernetes/create-weblogic-operator-inputs.yaml
#   genOprCertScript shell variable that has the full pathname of the script to use to generate certificates
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
  echo usage: ${createScript} -o dir [-i file] [-g] [-h]
  echo "  -o Ouput directory for the generated yaml files, must be specified."
  echo "  -i Parameter input file, defaults to kubernetes/create-weblogic-operator-inputs.yaml"
  echo "  -g Only generate the files to create the operator, do not execute them"
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
valuesInputFile="${defaultOperatorInputsFile}"
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

if [ -z ${outputDir} ]; then
  echo "${script}: -o must be specified."
  usage 1
fi

#
# Function to setup the environment to run the create domain job
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateKubectlAvailable

  if ! [ -d ${outputDir} ]; then
    validationError "Unable to locate the directory ${outputDir}. \nThis is the name of the directory to store the generated yaml files in."
  fi

  oprInput="${scriptDir}/weblogic-operator-template.yaml"
  oprOutput="${oprOutputDir}/weblogic-operator.yaml"
  if [ ! -f ${oprInput} ]; then
    validationError "The template file ${oprInput} for generating the weblogic operator was not found"
  fi

  if [ ! -f ${genOprCertScript} ]; then
    validationError "The file ${genOprCertScript} for generating the weblogic operator certificates was not found"
  fi

  genSecPolicyScript="${scriptDir}/generate-security-policy.sh"
  if [ ! -f ${genSecPolicyScript} ]; then
    validationError "The file ${genSecPolicyScript} for generating the security policy was not found"
  fi

  # Validation checks for elk integration
 
  elasticsearchYaml="${scriptDir}/elasticsearch.yaml"
  if [ ! -f ${elasticsearchYaml} ]; then
    validationError "The file ${elasticsearchYaml} necessary for elk deployment was not found"
  fi

  kibanaYaml="${scriptDir}/kibana.yaml"
  if [ ! -f ${kibanaYaml} ]; then
    validationError "The file ${kibanaYaml} necessary for elk deployment was not found"
  fi

  failIfValidationErrors

  # Parse the common inputs file
  parseCommonInputs

  validateInputParamsSpecified version serviceAccount namespace targetNamespaces weblogicOperatorImage

  validateBooleanInputParamsSpecified elkIntegrationEnabled

  validateVersion

  validateServiceAccount

  validateNamespace

  validateTargetNamespaces

  validateRemoteDebugNodePort

  validateJavaLoggingLevel

  validateExternalRest
  
  validateImagePullPolicy

  validateImagePullSecretName

  initAndValidateOutputDir

  failIfValidationErrors
}

#
# Function to initialize and validate the output directory
# for the generated yaml files for this operator.
#
function initAndValidateOutputDir {
  oprOutputDir="${outputDir}/weblogic-operators/${namespace}"
  domainOutputDir="${outputDir}/weblogic-domains/${domainUid}"
  validateOutputDir \
    ${oprOutputDir} \
    ${valuesInputFile} \
    create-weblogic-operator-inputs.yaml \
    weblogic-operator.yaml \
    weblogic-operator-security.yaml
}

#
# Function to validate the image pull secret name
#
function validateImagePullSecretName {
  if [ ! -z "${weblogicOperatorImagePullSecretName}" ]; then
    validateLowerCase weblogicOperatorImagePullSecretName ${weblogicOperatorImagePullSecretName}
    imagePullSecretPrefix=""
    validateImagePullSecret
  else
    # Set name blank when not specified, and comment out the yaml
    weblogicOperatorImagePullSecretName=""
    imagePullSecretPrefix="#"
  fi
}

#
# Function to validate the image pull secret exists
#
function validateImagePullSecret {
  # The kubernetes secret for pulling images from the docker store is optional.
  # If it was specified, make sure it exists.
  validateSecretExists ${weblogicOperatorImagePullSecretName} ${namespace}
  failIfValidationErrors
}

#
# Function to validate that the image pull policy has been properly configured
#
function validateImagePullPolicy {

  # Validate that imagePullPolicy was specified
  validateInputParamsSpecified weblogicOperatorImagePullPolicy

  # And validate that it's one of the allowed logging levels
  if [ ! -z "${weblogicOperatorImagePullPolicy}" ]; then
    IF_NOT_PRESENT="IfNotPresent"
    ALWAYS="Always"
    NEVER="Never"
    if [ $weblogicOperatorImagePullPolicy != $IF_NOT_PRESENT  ] && \
       [ $weblogicOperatorImagePullPolicy != $ALWAYS          ] && \
       [ $weblogicOperatorImagePullPolicy != $NEVER           ]; then
      validationError "Invalid weblogicOperatorImagePullPolicy: \"${weblogicOperatorImagePullPolicy}\". Valid values are $IF_NOT_PRESENT, $ALWAYS and $NEVER."
    fi
  fi
}

#
# Function to validate the version of the inputs file
#
function validateVersion {
  local requiredVersion='create-weblogic-operator-inputs-v1'
  if [ "${version}" != "${requiredVersion}" ]; then
    validationError "Invalid version: \"${version}\".  Must be ${requiredVersion}."
  fi
}

#
# Function to validate the service account is lowercase
#
function validateServiceAccount {
  validateLowerCase "serviceAccount" ${serviceAccount}
}

#
# Function to validate the target namespaces
#
function validateTargetNamespaces {
  validateLowerCase "targetNamespaces" ${targetNamespaces}
}

#
# Function to validate that the remote debug node port has been properly configured
#
function validateRemoteDebugNodePort {

  # Validate that remoteDebugNodePortEnabled  was specified
  validateBooleanInputParamsSpecified remoteDebugNodePortEnabled 

  if [ "${remoteDebugNodePortEnabled}" = true ]; then
    # Validate that the required sub options were specified
    validateIntegerInputParamsSpecified externalDebugHttpPort internalDebugHttpPort
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
    EXT_REST_OPT_NONE="NONE"
    EXT_REST_OPT_SELF_SIGNED="SELF_SIGNED_CERT"
    EXT_REST_OPT_CUSTOM="CUSTOM_CERT"
    case ${externalRestOption} in
      ${EXT_REST_OPT_NONE})
        echo The WebLogic Operator REST interface will not be externally exposed
      ;;
      ${EXT_REST_OPT_SELF_SIGNED})
        echo The WebLogic operator REST interface is externally exposed using a generated self-signed certificate that contains the customer-provided list of subject alternative names.
        validateInputParamsSpecified externalSans
        validateIntegerInputParamsSpecified externalRestHttpsPort
      ;;
      ${EXT_REST_OPT_CUSTOM})
        echo The WebLogic operator REST interface is externally exposed using a customer-provided certificate and private key pair.
        validateInputParamsSpecified externalOperatorCert externalOperatorKey
        validateIntegerInputParamsSpecified externalRestHttpsPort
      ;;
      *)
        validationError "Invalid externalRestOption: \"${externalRestOption}\".  Valid values are $EXT_REST_OPT_NONE, $EXT_REST_OPT_SELF_SIGNED and $EXT_REST_OPT_CUSTOM."
      ;;
    esac
  fi
}

#
# Function to create certificates
#
function createCertificates {

dirname "${script}"
  genOprCertScriptDir=`dirname "${genOprCertScript}"`
  generatedCertDir="${genOprCertScriptDir}/weblogic-operator-cert"
  generatedCertFile="${generatedCertDir}/weblogic-operator.cert.pem"
  generatedKeyFile="${generatedCertDir}/weblogic-operator.key.pem"

  # Always generate a self-signed cert for the internal operator REST port
  internal_host="internal-weblogic-operator-svc"
  internal_sans="DNS:${internal_host},DNS:${internal_host}.${namespace},DNS:${internal_host}.${namespace}.svc,DNS:${internal_host}.${namespace}.svc.cluster.local"
  echo "Generating a self-signed certificate for the operator's internal https port with the subject alternative names ${internal_sans}"
  ${genOprCertScript} ${internal_sans}
  if [ $? -ne 0 ]; then
    fail "Problem generating the internal weblogic operator REST certificate and private key with the subject alternative names ${internal_sans}"
  fi

  # copy the cert and key into internal_cert_data and internal_key_data then remove them
  internal_cert_data=`base64 -i $generatedCertFile | tr -d '\n'`
  internal_key_data=`base64 -i $generatedKeyFile | tr -d '\n'`
  rm -rf $generatedCertDir

  if [ "${externalRestOption}" = "${EXT_REST_OPT_SELF_SIGNED}" ]; then
    # EXT_REST_OPT_SELF_SIGNED was specified.  Generate a self signed cert and use it.
    echo "Generating a self-signed certificate for the operator's external ssl port with the subject alternative names ${externalSans}"
    ${genOprCertScript} ${externalSans}
    if [ $? -ne 0 ]; then
      fail "Problem generating the external weblogic operator REST certificate and private key with the subject alternative names ${externalSans}"
    fi
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

  # Create a directory for this operator's output files
  mkdir -p ${oprOutputDir}

  # Make sure the output directory has a copy of the inputs file.
  # The user can either pre-create the output directory, put the inputs
  # file there, and create the operator from it, or the user can put the
  # inputs file some place else and let this script create the output directory
  # (if needed) and copy the inputs file there.
  copyInputsFileToOutputDirectory ${valuesInputFile} "${oprOutputDir}/create-weblogic-operator-inputs.yaml"

  # Generate the yaml to create the WebLogic operator
  oprOutput="${oprOutputDir}/weblogic-operator.yaml"
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
  sed -i -e "s|%IMAGE%|$weblogicOperatorImage|g" ${oprOutput}
  sed -i -e "s|%IMAGE_PULL_POLICY%|$weblogicOperatorImagePullPolicy|g" ${oprOutput}
  sed -i -e "s|%DOCKER_REGISTRY_SECRET%|${weblogicOperatorImagePullSecretName}|g" ${oprOutput}
  sed -i -e "s|%IMAGE_PULL_SECRET_PREFIX%|${imagePullSecretPrefix}|g" ${oprOutput}
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

  # Create the weblogic-operator-security.yaml file
  oprSecurityFile="${oprOutputDir}/weblogic-operator-security.yaml"
  roleName="weblogic-operator-namespace-role"
  roleBinding="weblogic-operator-rolebinding"
  clusterRole="weblogic-operator-cluster-role"
  clusterRoleBinding="${namespace}-operator-rolebinding"

  echo Running the weblogic operator security customization script
  ${genSecPolicyScript} ${serviceAccount} ${namespace} "${targetNamespaces}" -o ${oprSecurityFile}

  # Remove any "...yaml-e" files left over from running sed
  rm -f ${oprOutputDir}/*.yaml-e
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
# Function to setup the operator's security
#
function setupSecurity {

  echo Applying the generated file ${oprSecurityFile}
  kubectl apply -f ${oprSecurityFile}

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


#
# Deploy elk
#
function deployELK {

  echo 'Deploy ELK...'
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
  LABEL=`kubectl get deploy weblogic-operator -n ${namespace} -o jsonpath='{.spec.template.metadata.labels.app}'`
  if [ "$LABEL" != "weblogic-operator" ]; then
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
  echo "  ${oprOutputDir}/create-weblogic-operator-inputs.yaml"
  echo "  ${oprOutput}"
  echo "  ${oprSecurityFile}"
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
  
  # Setup security
  setupSecurity

  if [ "${elkIntegrationEnabled}" = true ]; then
     # Deploy elk
     # must run before logstash container creation
     deployELK
  fi

  # Deploy the WebLogic operator
  deployOperator

  # Output a job summary
  outputJobSummary
fi
echo ""
echo Completed
