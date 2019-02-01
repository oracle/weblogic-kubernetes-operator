#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

set -u

function createCon() {
  echo "install Treafik controller to namespace traefik"
  helm install stable/traefik \
    --name traefik-controller \
    --namespace traefik \
    --values $WLS_OPT_ROOT/kubernetes/samples/charts/traefik/values.yaml  \
    --set "kubernetes.namespaces={traefik,default,test1}" \
    --wait
}

function delCon() {
  echo "delete Traefik controller"
  helm delete --purge traefik-controller
  kubectl delete namespace traefik
  waitUntilNSTerm
}

function waitUntilNSTerm() {
  ready=false
  while test $ready != true; do
    if test "$(kubectl get ns traefik  --ignore-not-found | wc -l)" != 0; then
      echo "wait until namespace traefik termiated..."
      sleep 5
      continue
    fi
    ready=true
  done
}

function createIng() {
  echo "install Ingress for domains"
  helm install $WLS_OPT_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain1-ing-t \
    --namespace default \
    --set wlsDomain.domainUID=domain1 \
    --set traefik.hostname=domain1.org

 helm install $WLS_OPT_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain2-ing-t \
    --namespace test1 \
    --set wlsDomain.domainUID=domain2 \
    --set traefik.hostname=domain2.org

 helm install $WLS_OPT_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain3-ing-t \
    --namespace test1 \
    --set wlsDomain.domainUID=domain3 \
    --set traefik.hostname=domain3.org
}

function delIng() {
  echo "delete Ingress"
  helm delete --purge domain1-ing-t 
  helm delete --purge domain2-ing-t
  helm delete --purge domain3-ing-t
}

function verify() {
  curl -v -H 'host: domain1.org' http://$hostname:30305/weblogic/
  curl -v -H 'host: domain2.org' http://$hostname:30305/weblogic/
  curl -v -H 'host: domain3.org' http://$hostname:30305/weblogic/
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "Commands:"
  echo "  createCon: to create the Treafik controller"
  echo
  echo "  delCon: to delete the Treafik controller"
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
