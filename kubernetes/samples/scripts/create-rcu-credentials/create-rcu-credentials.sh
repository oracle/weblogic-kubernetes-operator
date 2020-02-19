#!/usr/bin/env bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a Kubernetes secret for RCU credentials.
#
#  The following pre-requisites must be handled prior to running this script:
#    * The kubernetes namespace must already be created
#
# Secret name determination
#  1) secretName - if specified
#  2) domain1-rcu-credentials - if secretName and domainUID are both not specified. This is the default out-of-the-box.
#  3) <domainUID>-rcu-credentials - if secretName is not specified, and domainUID is specified.
#  4) rcu-credentials - if secretName is not specified, and domainUID is specified as "".
#
# The generated secret will be labeled with 
#       weblogic.domainUID=$domainUID 
# and
#       weblogic.domainName=$domainUID 
# Where the $domainUID is the value of the -d command line option, unless the value supplied is an empty String ""
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
  echo usage: ${script} -u username -p password -a sysuser -q syspassword [-d domainUID] [-n namespace] [-s secretName] [-h]
  echo "  -u username for schema owner (regular user), must be specified."
  echo "  -p password for schema owner (regular user), must be specified."
  echo "  -a username for SYSDBA user, must be specified."
  echo "  -q password for SYSDBA user, must be specified."
  echo "  -d domainUID, optional. The default value is domain1. If specified, the secret will be labeled with the domainUID unless the given value is an empty string."
  echo "  -n namespace, optional. Use the default namespace if not specified"
  echo "  -s secretName, optional. If not specified, the secret name will be determined based on the domainUID value"
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
domainUID=domain1
namespace=default
while getopts "hu:p:n:d:s:q:a:" opt; do
  case $opt in
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    a) sys_username="${OPTARG}"
    ;;
    q) sys_password="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    d) domainUID="${OPTARG}"
    ;;
    s) secretName="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z $secretName ]; then
  if [ -z $domainUID ]; then
    secretName=rcu-credentials
  else 
    secretName=$domainUID-rcu-credentials
  fi
fi

if [ -z ${username} ]; then
  echo "${script}: -u must be specified."
  missingRequiredOption="true"
fi

if [ -z ${password} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ -z ${sys_username} ]; then
  echo "${script}: -s must be specified."
  missingRequiredOption="true"
fi

if [ -z ${sys_password} ]; then
  echo "${script}: -q must be specified."
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
  --from-literal=username=$username \
  --from-literal=password=$password \
  --from-literal=sys_username=$sys_username \
  --from-literal=sys_password=$sys_password

# label the secret with domainUID if needed
if [ ! -z $domainUID ]; then
  kubectl label secret ${secretName} -n $namespace weblogic.domainUID=$domainUID weblogic.domainName=$domainUID
fi

# Verify the secret exists
SECRET=`kubectl get secret ${secretName} -n ${namespace} | grep ${secretName} | wc | awk ' { print $1; }'`
if [ "${SECRET}" != "1" ]; then
  fail "The secret ${secretName} was not found in namespace ${namespace}"
fi

echo "The secret ${secretName} has been successfully created in the ${namespace} namespace."
