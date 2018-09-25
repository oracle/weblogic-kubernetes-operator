#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script deletes all Kubernetes resources for the Voyager load balancer 
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

function usage {
  echo usage: ${script} -p dir [-h]
  echo "  -p Directory of the Voyager related yamlp files, must be specified."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
while getopts "hp:" opt; do
  case $opt in
    p) resourceDir="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${resourceDir} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

VOYAGER_ING_NAME="ingresses.voyager.appscode.com"
VOYAGER_CERT_NAME="certificates.voyager.appscode.com"

vnamespace=voyager
if test "$(kubectl get pod -n $vnamespace --ignore-not-found | grep voyager | wc -l)" == 0; then
  echo "Voyager operator has already been deleted."
  exit
fi

# purge CRDs
# kubectl delete crd $VOYAGER_ING_NAME --ignore-not-found
# kubectl delete crd $VOYAGER_CERT_NAME --ignore-not-found

echo "Deleting Voyager opreator resources"

kubectl delete apiservice -l app=voyager
# delete voyager operator
kubectl delete deployment -l app=voyager --namespace $vnamespace --ignore-not-found
kubectl delete service -l app=voyager --namespace $vnamespace --ignore-not-found
kubectl delete secret -l app=voyager --namespace $vnamespace --ignore-not-found
# delete RBAC objects
kubectl delete serviceaccount -l app=voyager --namespace $vnamespace --ignore-not-found
kubectl delete clusterrolebindings -l app=voyager --ignore-not-found
kubectl delete clusterrole -l app=voyager --ignore-not-found
kubectl delete rolebindings -l app=voyager --namespace $vnamespace --ignore-not-found
kubectl delete role -l app=voyager --namespace $vnamespace --ignore-not-found

echo "Wait until voyager operator pod stopped..."
maxwaitsecs=100
mstart=`date +%s`
while : ; do
  mnow=`date +%s`
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


