#!/usr/bin/env bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a Kubernetes secret for Azure Storage to use Azure file share on AKS.
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#

script="${BASH_SOURCE[0]}"

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  echo [ERROR] $*
  exit 1
}

# Try to execute kubectl to see whether kubectl is available
function validateKubectlAvailable {
  if ! [ -x "$(command -v kubectl)" ]; then
    fail "kubectl is not installed"
  fi
}

function usage {
  echo usage: ${script} -c storageAccountName -k storageAccountKey [-s secretName] [-n namespace] [-h]
  echo "  -a storage account name, must be specified."
  echo "  -k storage account key, must be specified."
  echo "  -s secret name, optional. Use azure-secret if not specified."
  echo "  -n namespace, optional. Use the default namespace if not specified."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
secretName=azure-secret
namespace=default
while getopts "ha:k:s:n:" opt; do
  case $opt in
    a) storageAccountName="${OPTARG}"
    ;;
    k) storageAccountKey="${OPTARG}"
    ;;
    s) secretName="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${storageAccountName} ]; then
  echo "${script}: -e must be specified."
  missingRequiredOption="true"
fi

if [ -z ${storageAccountKey} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

# check and see if the secret already exists
result=`kubectl get secret ${secretName} -n ${namespace} --ignore-not-found=true | grep ${secretName} | wc | awk ' { print $1; }'`
if [ "${result:=Error}" != "0" ]; then
  fail "The secret ${secretName} already exists in namespace ${namespace}."
fi

# create the secret
kubectl -n $namespace create secret generic $secretName \
    --from-literal=azurestorageaccountname=$storageAccountName \
    --from-literal=azurestorageaccountkey=$storageAccountKey

# Verify the secret exists
SECRET=`kubectl get secret ${secretName} -n ${namespace} | grep ${secretName} | wc | awk ' { print $1; }'`
if [ "${SECRET}" != "1" ]; then
  fail "The secret ${secretName} was not found in namespace ${namespace}"
fi

echo "The secret ${secretName} has been successfully created in the ${namespace} namespace."
