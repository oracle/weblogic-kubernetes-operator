#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Functions that are shared between the create-weblogic-domain.sh and create-weblogic-operator.sh scripts
#

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  printError $*
  exit 1
}

#
# Function to note that a validate error has occurred
#
function validationError {
  printError $*
  validateErrors=true
}

# Function to print an error message
function printError {
  echo [ERROR] $*
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
    if [ -z "$val" ]; then
      validationError "The ${name} parameter in ${valuesInputFile} is missing, null or empty"
    fi
  done
}

#
# Function to validate that a list of input parameters have boolean values.
# It assumes that validateInputParamsSpecified will also be called for these params.
#
function validateBooleanInputParamsSpecified {
  validateInputParamsSpecified $*
  for p in $*; do
    local name=$p
    local val=${!name}
    if ! [ -z $val ]; then
      if [ "true" != "$val" ] && [ "false" != "$val" ]; then
        validationError "The value of $name must be true or false: $val"
      fi
    fi
  done
}

#
# Function to validate that a list of input parameters have integer values.
#
function validateIntegerInputParamsSpecified {
  validateInputParamsSpecified $*
  for p in $*; do
    local name=$p
    local val=${!name}
    if ! [ -z $val ]; then
      local intVal=""
      printf -v intVal '%d' "$val" 2>/dev/null
      if ! [ "${val}" == "${intVal}" ]; then
        validationError "The value of $name must be an integer: $val"
      fi
    fi
  done
}

#
# Function to validate a kubernetes secret exists
# $1 - the name of the secret
# $2 - namespace
function validateSecretExists {
  # delegate to a function supplied by the caller so that while unit testing,
  # where kubectl and kubernetes are not available, we can stub out this check
  validateThatSecretExists $*
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
# Function to check if a value is lowercase
# $1 - name of object being checked
# $2 - value to check
function validateLowerCase {
  local lcVal=$(toLower $2)
  if [ "$lcVal" != "$2" ]; then
    validationError "The value of $1 must be lowercase: $2"
  fi
}

# 
# Function to lowercase a value and make it a legal DNS1123 name 
# $1 - value to convert to DNS legal name
function toDNS1123Legal { 
  local val=`echo $1 | tr "[:upper:]" "[:lower:]"` 
  val=${val//"_"/"-"} 
  echo "$val" 
}

# 
# Function to check if a value is lowercase and legal DNS name 
# $1 - name of object being checked 
# $2 - value to check 
function validateDNS1123LegalName { 
  local val=$(toDNS1123Legal $2) 
  if [ "$val" != "$2" ]; then 
    validationError "The value of $1 contains invalid charaters (uppercase letters or "_"): $2" 
  fi 
}

#
# Function to check if a value is lowercase and legal DNS name
# $1 - value to check
# $2 - name of object being checked
function validateDNS1123LegalName {
  local val=$(toDNS1123Legal $2)
  if [ "$val" != "$2" ]; then
    validationError "The value of $1 contains invalid charaters: $2"
  fi
}

#
# Function to validate the namespace
#
function validateNamespace {
  validateLowerCase "namespace" ${namespace}
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
# Function to validate that either the output dir does not exist,
# or that if it does, it does not contain any generated yaml files
# and does not contain an inputs file that differs from the one
# the script is using
# $1   - the output directory to validate
# $2   - the name of the input file the create script is using
# $3   - the name of the input file that is put into the output directory
# $4-n - the names of the generated yaml files
function validateOutputDir {
  local dir=$1
  shift
  if [ -e ${dir} ]; then
    # the output directory already exists
    if [ -d ${dir} ]; then
      # the output directory is a directory
      local in1=$1
      shift
      local in2=${1}
      shift
      internalValidateInputsFileDoesNotExistOrIsTheSame ${dir} ${in1} ${in2}
      internalValidateGeneratedYamlFilesDoNotExist ${dir} $@
    else
      validationError "${dir} exists but is not a directory."
    fi
  fi
}

#
# Internal function to validate that the inputs file does not exist in the
# outputs directory or is the same as the inputs file the script is using
# $1 - the output directory to validate
# $2 - the name of the input file the create script is using
# $3 - the name of the input file that is put into the output directory
function internalValidateInputsFileDoesNotExistOrIsTheSame {
  local dir=$1
  local in1=$2
  local in2=$3
  local f="${dir}/${in2}"
  if [ -e ${f} ]; then
    if [ -f ${f} ]; then
      local differences=`diff -q ${f} ${in1}`
      if ! [ -z "${differences}" ]; then
        validationError "${f} is different than ${in1}"
      fi
    else
      validationError "${f} exists and is not a file."
    fi
  fi
}

#
# Internal unction to validate that the generated yaml files do not exist
# in the outputs directory
# $1 - the output directory to validate
# $2-n - the names of the generated yaml files
function internalValidateGeneratedYamlFilesDoNotExist {
  local dir=$1
  shift
  for var in "$@"; do
    local f="${dir}/${var}"
    if [ -e ${f} ]; then
      validationError "${f} exists."
    fi
  done
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

VOYAGER_ING_NAME="ingresses.voyager.appscode.com"
#
# Usage:
# createVoyagerOperator voyagerSecurityYaml voyagerOperatorYaml
#
# If the voyager operator is already running, do nothing.
#
function createVoyagerOperator() {
  if [ "$#" != 2 ] ; then
    fail "requires 2 parameter: voyagerSecurityYaml voyagerOperatorYaml"
  fi
  
  local vnamespace=voyager
  # only deploy Voyager Operator once
  if test "$(kubectl get pod -n $vnamespace --ignore-not-found | grep voyager | wc -l)" == 0; then
    echo "Deploying Voyager Operator to namespace $vnamespace..."

    if test "$(kubectl get namespace $vnamespace --ignore-not-found | wc -l)" = 0; then
      kubectl create namespace $vnamespace
    fi
    kubectl apply -f $1
    kubectl apply -f $2
  fi

  echo "Wait until Voyager Operator is ready..."
  local maxwaitsecs=100
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    if test "$(kubectl -n $vnamespace get pod  --ignore-not-found | grep voyager-operator | awk ' { print $2; } ')" = "1/1"; then
      echo "The Voyager Operator is ready."
      break
    fi
    if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
      fail "The Voyager Operator is not ready."
    fi
    sleep 5
  done

  echo "Checking apiserver..."
  local maxwaitsecs=10
  local mstart=`date +%s`
  while test "$(kubectl get apiservice | grep v1beta1.admission.voyager.appscode.com  | wc -l)" = 0; do
    sleep 2
    local mnow=`date +%s`
    if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
      fail "The Voyager apiserver v1beta1.admission.voyager.appscode.com is not ready."
    fi
  done
  echo "The Voyager apiserver is ready."

  echo "Checking Voyager CRDs..."
  local maxwaitsecs=10
  local mstart=`date +%s`
  while  test "$(kubectl get crd | grep certificates.voyager.appscode.com | wc -l)" = 0; do
    sleep 2
    local mnow=`date +%s`
    if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
      fail "The Voyager CRD certificates.voyager.appscode.com is not ready."
    fi
  done
  echo "The Voyager CRD certificates.voyager.appscode.com is ready."

  local maxwaitsecs=10
  local mstart=`date +%s`
  while  test "$(kubectl get crd | grep $VOYAGER_ING_NAME | wc -l)" = 0; do
    sleep 2
    local mnow=`date +%s`
    if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
      fail "The Voyager CRD $VOYAGER_ING_NAME is not ready."
    fi
  done
  echo "The Voyager CRD $VOYAGER_ING_NAME is ready."
  echo
}

#
# delete voyager operator
#
function deleteVoyagerOperator {
  local vnamespace=voyager
  if test "$(kubectl get pod -n $vnamespace --ignore-not-found | grep voyager | wc -l)" == 0; then
    echo "Voyager operator has already been deleted."
    return
  fi

  echo "Deleting Voyager opreator resources"
  kubectl delete apiservice -l app=voyager
  # delete voyager operator
  kubectl delete deployment -l app=voyager --namespace $vnamespace
  kubectl delete service -l app=voyager --namespace $vnamespace
  kubectl delete secret -l app=voyager --namespace $vnamespace
  # delete RBAC objects
  kubectl delete serviceaccount -l app=voyager --namespace $vnamespace
  kubectl delete clusterrolebindings -l app=voyager
  kubectl delete clusterrole -l app=voyager
  kubectl delete rolebindings -l app=voyager --namespace $vnamespace
  kubectl delete role -l app=voyager --namespace $vnamespace

  echo "Wait until voyager operator pod stopped..."
  local maxwaitsecs=100
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    pods=($(kubectl get pods --all-namespaces -l app=voyager -o jsonpath='{range .items[*]}{.metadata.name} {end}'))
    total=${#pods[*]}
    if [ $total -eq 0 ] ; then
      echo "Voyager operator pod is stopped."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "Voyager operator pod is NOT stopped."
    fi
    sleep 5
  done
  echo
  #TODO purge CRDs
}

#
# Usage:
# createVoyagerIngress voyagerIngressYaml namespace domainUID
#
function createVoyagerIngress {
  if [ "$#" != 3 ] ; then
    fail "requires 1 parameter: voyagerIngressYaml namespace domainUID"
  fi

  # deploy Voyager Ingress resource
  kubectl apply -f $1

  local namespace=$2
  local domainUID=$3
  

  echo "Checking Voyager Ingress resource..."
  local maxwaitsecs=100
  local mstart=$(date +%s)
  while : ; do
    local mnow=$(date +%s)
    local vdep=$(kubectl get ingresses.voyager.appscode.com -n ${namespace} | grep ${domainUID}-voyager | wc | awk ' { print $1; } ')
    if [ "$vdep" = "1" ]; then
      echo "The Voyager Ingress resource ${domainUID}-voyager is created successfully."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The Voyager Ingress resource ${domainUID}-voyager was not created."
    fi
    sleep 2
  done

  echo "Wait until HAProxy pod is running..."
  local maxwaitsecs=100
  local mstart=$(date +%s)
  while : ; do
    local mnow=$(date +%s)
    local st=$(kubectl get pod -n ${namespace} | grep ^voyager-${domainUID}-voyager- | awk ' { print $3; } ')
    if [ "$st" = "Running" ]; then
      echo "The HAProxy pod for Voyager Ingress ${domainUID}-voyager is running."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The HAProxy pod for Voyager Ingress ${domainUID}-voyager was not created or running."
    fi
    sleep 5
  done

  echo "Checking Voyager service..."
  local maxwaitsecs=10
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    local vscv=`kubectl get service ${domainUID}-voyager-stats -n ${namespace} | grep ${domainUID}-voyager-stats | wc | awk ' { print $1; } '`
    if [ "$vscv" = "1" ]; then
      echo "The service ${domainUID}-voyager-stats is created successfully."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The service ${domainUID}-voyager-stats was not created."
    fi
    sleep 2
  done
  echo
}

#
# Usage:
# deleteVoyagerIngress voyagerIngressYaml namespace domainUID
#
function deleteVoyagerIngress {
  if [ "$#" != 3 ] ; then
    fail "requires 1 parameter: voyagerIngressYaml namespace domainUID"
  fi

  kubectl delete -f $1
  local namespace=$2
  local domainUID=$3

  echo "Wait until HAProxy pod stoped..."
  local maxwaitsecs=100
  local mstart=$(date +%s)
  while : ; do
    local mnow=$(date +%s)
    if [ $(kubectl get pod -n ${namespace} | grep "^voyager-${domainUID}-voyager-" | wc -l) = 0 ]; then
      echo "The HAProxy pod for Voyaer Ingress ${domainUID}-voyager is stopped."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The HAProxy pod for Voyaer Ingress ${domainUID}-voyager is NOT stopped."
    fi
    sleep 5
  done
  echo
}

