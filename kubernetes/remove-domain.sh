#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Description
#  This script automates the removal of a WebLogic domain from a Kubernetes cluster.  It does NOT delete any of the
#  files associated with that domain.

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  echo [ERROR] $1
  exit 1
}

#
# Function to delete a kubernetes object
# $1 object type
# $2 object name
function deleteK8sObj {
  echo Checking if object type $1 with name $2 exists
  K8SOBJ=`kubectl get $1 -n $namespace | grep $2 | wc | awk ' { print $1; }'`
  if [ "${K8SOBJ}" = "1" ]; then
    echo Deleting $2
    kubectl delete $1 $2 -n $namespace
  fi
}

#
# check that kubectl is available
#
if ! [ -x "$(command -v kubectl)" ]; then
  validationError "kubectl is not installed"
fi

#
# Parse the command line options
#
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
domainUID=none
namespace=none
while getopts "dhn:" opt; do
  case $opt in
    d) domainUID="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    h) echo ./revmove-domain.sh -d domainUID -n namespace [-h]
       echo
       echo -d Specifies the domainUID that uniquely identifies the domain to be removed
       echo -n Specified the namespace from which to remove that domain
       echo -h Print this help
       exit
    ;;
    \?) fail "Invalid or missing command line option"
    ;;
  esac
done

# check the namespace exists

# find all of the resources asssociated with that domain, either by annotations/labels or
# by the name of the resource, and remove them

domains=`kubectl get domains -n $namespace | grep $domainUID`  # there should be just the one specified
pods=`kubectl get pods -n $namespace | grep $domainUID`

for d in domains
do
  deleteK8sObj "domain" "$d"
done

# wait a little while to let the operator delete the pods
# .. loop and check for pods, until they are all gone, or for some reasonable amount of time
# remember that k8s will let them try to do a graceful shutdown first, so maybe wait 2 minutes?
# do not need to wait for ALL of them to disappear - remember the operator will not remove the load balancer pods
# of which there could be one per WebLogic cluster


# find any load balancers associated with this domain and remove them
load_balancers=`kubectl get pods -n $namespace`

# find services and remove them

# find ingresses and remove them

# find persistent volume claims and remove them

# find persistent volumes and remove them

# find config maps and remove them

# find secrets and remove them (generic, not docker-registry secrets)

# find jobs and remove them

# woot! 
