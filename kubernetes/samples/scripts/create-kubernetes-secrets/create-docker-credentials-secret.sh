#!/usr/bin/env bash
# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a Kubernetes secret for container registry credentials for use with the WLS Operator on AKS.
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#

script="${BASH_SOURCE[0]}"

#
# Function to exit and print an error message
# $1 - text of message
fail() {
  echo [ERROR] $*
  exit 1
}

# Try to execute ${KUBERNETES_CLI:-kubectl} to see whether ${KUBERNETES_CLI:-kubectl} is available
validateKubernetesCLIAvailable() {
  if ! [ -x "$(command -v ${KUBERNETES_CLI:-kubectl})" ]; then
    fail "${KUBERNETES_CLI:-kubectl} is not installed"
  fi
}

usage() {
  echo usage: ${script} -e email -p password -u username [-s secretName] [-d imageRepo] [-n namespace] [-h]
  echo "  -e email, must be specified."
  echo "  -p password, must be specified."
  echo "  -u username, must be specified."
  echo "  -s secret name, optional, uses regcred if not specified."
  echo "  -d image repo, optional, uses container-registry.oracle.com if not specified."
  echo "  -n namespace, optional, uses the default namespace if not specified."
  echo "  -h Help."
  exit $1
}

#
# Parse the command line options
#
secretName=regcred
namespace=default
imageRepo=container-registry.oracle.com
while getopts "he:p:u:n:d:s:d:" opt; do
  case $opt in
    e) email="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    u) username="${OPTARG}"
    ;;
    s) secretName="${OPTARG}"
    ;;
    d) imageRepo="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${email} ]; then
  echo "${script}: -e must be specified."
  missingRequiredOption="true"
fi

if [ -z ${password} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ -z ${username} ]; then
  echo "${script}: -u must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

# check and see if the secret already exists
result=`${KUBERNETES_CLI:-kubectl} get secret ${secretName} -n ${namespace} --ignore-not-found=true | grep ${secretName} | wc | awk ' { print $1; }'`
if [ "${result:=Error}" != "0" ]; then
  fail "The secret ${secretName} already exists in namespace ${namespace}."
fi

# create the secret
${KUBERNETES_CLI:-kubectl} -n $namespace create secret docker-registry $secretName \
  --docker-email=$email \
  --docker-password=$password \
  --docker-server=$imageRepo \
  --docker-username=$username 

# Verify the secret exists
SECRET=`${KUBERNETES_CLI:-kubectl} get secret ${secretName} -n ${namespace} | grep ${secretName} | wc | awk ' { print $1; }'`
if [ "${SECRET}" != "1" ]; then
  fail "The secret ${secretName} was not found in namespace ${namespace}"
fi

echo "The secret ${secretName} has been successfully created in the ${namespace} namespace."
