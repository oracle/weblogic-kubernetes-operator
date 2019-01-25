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
  kubectl create -f ings/voyager-ings.yaml
}

function delIng() {
  echo "delete Ingress"
  kubectl delete -f ings/voyager-ings.yaml
}

function verify() {
  curl -v -H 'host: domain1.org' http://$hostname:30307/weblogic/
  curl -v -H 'host: domain2.org' http://$hostname:30307/weblogic/
  curl -v -H 'host: domain3.org' http://$hostname:30307/weblogic/
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
