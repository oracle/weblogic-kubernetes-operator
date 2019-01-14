#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export WLS_BASE_IMAGE=store/oracle/weblogic:19.1.0.0
export PRJ_ROOT=../../

function pullImages() {
  echo "pull docker images"
  docker pull oracle/weblogic-kubernetes-operator:2.0-rc1
  docker tag oracle/weblogic-kubernetes-operator:2.0-rc1 weblogic-kubernetes-operator:2.0
  docker pull traefik:1.7.4
  docker pull appscode/voyager:7.4.0 
  # TODO: until we has a public site for the image
  docker pull wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0
  docker tag wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0 $WLS_BASE_IMAGE
}

function delImages() {
  docker rmi domain1-image
  docker rmi domain2-image
  docker rmi wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0
  docker rmi $WLS_BASE_IMAGE
  docker rmi traefik:1.7.4
  docker rmi appscode/voyager:7.4.0
  docker rmi oracle/weblogic-kubernetes-operator:2.0-rc1
  docker rmi weblogic-kubernetes-operator:2.0
}

function create() {
  echo "create namespace test1 to run wls domains"
  kubectl create namespace test1

  echo "install WebLogic operator to namespace weblogic-operator1"
  kubectl create namespace weblogic-operator1
  kubectl create serviceaccount -n weblogic-operator1 sample-weblogic-operator-sa

  helm install $PRJ_ROOT/kubernetes/charts/weblogic-operator \
    --name sample-weblogic-operator \
    --namespace weblogic-operator1 \
    --set serviceAccount=sample-weblogic-operator-sa \
    --set "domainNamespaces={default,test1}" \
    --wait
  
  waitUntilCRDReady 
}

# wait until domain CRD is ready
function waitUntilCRDReady() {
  ready=false
  while test $ready != true; do
    if test "$(kubectl get crd  domains.weblogic.oracle  -oyaml | grep 'version: v2' | wc -l)" != '1'; then
      echo "waint until domain CRD is ready"
      sleep 5
      continue
    fi
    ready=true
  done
}

function delete() {
  echo "delete operators"
  helm delete --purge sample-weblogic-operator
  kubectl delete namespace weblogic-operator1

  kubectl delete namespace test1
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
