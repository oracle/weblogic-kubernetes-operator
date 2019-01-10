#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export PRJ_ROOT=../../

function createCon() {
  echo "install Voyager controller to namespace voyager"
  ../samples/charts/util/setup.sh create voyager
}

function delCon() {
  echo "delete Voyager controller"
  ../samples/charts/util/setup.sh delete voyager
}

function createIng() {
  echo "install Ingress for domains"
  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain1-ing-v \
    --namespace default \
    --set wlsDomain.domainUID=domain1 \
    --set type=VOYAGER \
    --set voyager.webPort=30307 \
    --set voyager.statsPort=30309


  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain2-ing-v \
    --namespace test1 \
    --set wlsDomain.domainUID=domain2 \
    --set type=VOYAGER \
    --set voyager.webPort=30311 \
    --set voyager.statsPort=30313

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain3-ing-v \
    --namespace test1 \
    --set wlsDomain.domainUID=domain3 \
    --set type=VOYAGER \
    --set voyager.webPort=30315 \
    --set voyager.statsPort=30317
}

function delIng() {
  echo "delete Ingress"
  helm delete --purge domain1-ing-v
  helm delete --purge domain2-ing-v
  helm delete --purge domain3-ing-v
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "Commands:"
  echo "  createCon: to create the Voyager controller"
  echo
  echo "  delCon: to delete the Voyager controller"
  echo
  echo "  createIng: to create Ingress of domains"
  echo
  echo "  delIng: to delete Ingress of domains"
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
