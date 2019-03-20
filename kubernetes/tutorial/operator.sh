#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

set -u

source waitUntil.sh
function pullImages() {
  echo "pull docker images"
  docker pull $WLS_OPERATOR_IMAGE 
  docker pull traefik:1.7.6
  docker pull appscode/voyager:7.4.0 
  docker pull $WLS_BASE_IMAGE 
}

function delImages() {
  docker rmi domain1-image
  docker rmi domain2-image
  docker rmi $WLS_BASE_IMAGE
  docker rmi traefik:1.7.6
  docker rmi appscode/voyager:7.4.0
  docker rmi $WLS_OPERATOR_IMAGE 
}

function create() {
  echo "create namespace test1 to run wls domains"
  kubectl create namespace test1

  echo "install WebLogic operator to namespace weblogic-operator1"
  kubectl create namespace weblogic-operator1
  kubectl create serviceaccount -n weblogic-operator1 sample-weblogic-operator-sa

  helm install $WLS_OPT_ROOT/kubernetes/charts/weblogic-operator \
    --name sample-weblogic-operator \
    --namespace weblogic-operator1 \
    --set serviceAccount=sample-weblogic-operator-sa \
    --set image=$WLS_OPERATOR_IMAGE \
    --set "domainNamespaces={default,test1}" \
    --wait
 
  if [ $? != 0 ]; then
    echo "fail to install WebLogic operator"
    return 1 
  fi
  waitUntilCRDReady 
}

# wait until domain CRD is ready
function waitUntilCRDReady() {
  expected_out=1
  okMsg="domain CRD is ready"
  failMsg="fail to create domain CRD"

  waitUntil checkCRDReadyCmd "$expected_out" "$okMsg" "$failMsg"
}

function checkCRDReadyCmd() {
  kubectl get crd domains.weblogic.oracle  -oyaml  --ignore-not-found | grep 'version: v2' | wc -l
}

function delete() {
  echo "delete operators"
  helm delete --purge sample-weblogic-operator
  kubectl delete crd domains.weblogic.oracle
  kubectl delete namespace weblogic-operator1
  kubectl delete namespace test1
  waitUntilNSTerm weblogic-operator1 
  waitUntilNSTerm test1
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "Commands:"
  echo "  pullImages: to pull required docker images"
  echo
  echo "  delImages: to delete docker images"
  echo
  echo "  create: to create the wls operator"
  echo
  echo "  delete: to delete the wls operator"
  echo
  exit 1
}

function main() {
  if [ "$#" != 1 ] ; then
    usage
  fi
  $1
}

main $@
