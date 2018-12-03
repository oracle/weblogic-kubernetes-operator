#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a Kubernetes secret for WebLogic domain admin credentials.
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
# source ${scriptDir}/../common/utility.sh
# source ${scriptDir}/../common/validate.sh

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  echo [ERROR] $*
  exit 1
}

# try to execute kubectl to see whether kubectl is available
function validateKubectlAvailable {
  if ! [ -x "$(command -v kubectl)" ]; then
    fail "kubectl is not installed"
  fi
}

function usage {
  echo usage: ${script} -u userName -p password [-d domainUID] [-n name] [-h]
  echo "  -u username, must be specified."
  echo "  -p password, must be specified."
  echo "  -n namespace, optional."
  echo "  -d domainUID, optional."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
domainUID=domain1
namespace=default
while getopts "hu:p:n:d:" opt; do
  case $opt in
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    d) domainUID="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done
secretName=$domainUID-weblogic-credentials

if [ -z ${username} ]; then
  echo "${script}: -u must be specified."
  missingRequiredOption="true"
fi

if [ -z ${password} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

#
# Function to validate the domain secret
#
function validateDomainSecret {
  # Verify the secret exists
  local SECRET=`kubectl get secret ${secretName} -n ${namespace} | grep ${secretName} | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    fail "The secret ${secretName} was not found in namespace ${namespace}"
  fi

  # Verify the secret contains a username
  SECRET=`kubectl get secret ${secretName} -n ${namespace} -o jsonpath='{.data}'| grep username: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    fail "The domain secret ${secretName} in namespace ${namespace} does contain a username"
  fi

  # Verify the secret contains a password
  SECRET=`kubectl get secret ${secretName} -n ${namespace} -o jsonpath='{.data}'| grep password: | wc | awk ' { print $1; }'`
  if [ "${SECRET}" != "1" ]; then
    fail "The domain secret ${secretName} in namespace ${namespace} does contain a password"
  fi
  echo "The secret ${secretName} has been successfully created in namespace ${namespace}"
}

#
# Perform the following sequence of steps to create a domain
#

result=`kubectl get secret ${secretName} -n ${namespace} --ignore-not-found=true | grep ${secretName} | wc | awk ' { print $1; }'`
if [ "${result:=Error}" != "0" ]; then
  fail "The secret ${secretName} already exists in namespace ${namespace}."
fi

kubectl -n $namespace create secret generic $secretName \
  --from-literal=username=$username \
  --from-literal=password=$password

kubectl label secret ${secretName} -n $namespace weblogic.domainUID=$domainUID weblogic.domainName=$domainUID

validateDomainSecret

echo 
echo Completed


