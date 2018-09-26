#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates the Voyager operator and Ingress Controller
#
#  The following pre-requisites must be handled prior to running this script:
#    * The Kubernetes namespace must already be created
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
resourceDir=""
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

voyagerSecurityOutput="${resourceDir}/weblogic-sample-domain-voyager-operator-security.yaml"
voyagerOperatorOutput="${resourceDir}/weblogic-sample-domain-voyager-operator.yaml"

vnamespace=voyager
# only deploy Voyager Operator once
if test "$(kubectl get pod -n $vnamespace --ignore-not-found | grep voyager | wc -l)" == 0; then
  echo "Deploying Voyager Operator to namespace $vnamespace..."

  if test "$(kubectl get namespace $vnamespace --ignore-not-found | wc -l)" = 0; then
    kubectl create namespace $vnamespace
  fi
  kubectl apply -f $voyagerSecurityOutput
  kubectl apply -f $voyagerOperatorOutput
fi

echo "Wait until Voyager Operator is ready..."
maxwaitsecs=100
mstart=`date +%s`
while : ; do
  mnow=`date +%s`
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
maxwaitsecs=10
mstart=`date +%s`
while test "$(kubectl get apiservice | grep v1beta1.admission.voyager.appscode.com  | wc -l)" = 0; do
  sleep 2
  mnow=`date +%s`
  if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
    fail "The Voyager apiserver v1beta1.admission.voyager.appscode.com is not ready."
  fi
done
echo "The Voyager apiserver is ready."

echo "Checking Voyager CRDs..."
maxwaitsecs=10
mstart=`date +%s`
while  test "$(kubectl get crd | grep $VOYAGER_CERT_NAME | wc -l)" = 0; do
  sleep 2
  mnow=`date +%s`
  if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
    fail "The Voyager CRD $VOYAGER_CERT_NAME is not ready."
  fi
done
echo "The Voyager CRD $VOYAGER_CERT_NAME is ready."

maxwaitsecs=10
mstart=`date +%s`
while  test "$(kubectl get crd | grep $VOYAGER_ING_NAME | wc -l)" = 0; do
  sleep 2
  mnow=`date +%s`
  if test $((mnow - mstart)) -gt $((maxwaitsecs)); then
    fail "The Voyager CRD $VOYAGER_ING_NAME is not ready."
  fi
done
echo "The Voyager CRD $VOYAGER_ING_NAME is ready."
echo


