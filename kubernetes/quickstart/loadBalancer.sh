#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export PRJ_ROOT=../../

function createOpt() {
  echo "install Treafik operator to namespace traefik"
  helm install stable/traefik \
    --name traefik-operator \
    --namespace traefik \
    --values $PRJ_ROOT/kubernetes/samples/charts/traefik/values.yaml  \
    --wait
}

function delOpt() {
  echo "delete Traefik operator"
  helm delete --purge traefik-operator
  kubectl delete namespace traefik
}

function createIngress() {
  echo "install Ingress for domains"
  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain1-ingress \
    --set wlsDomain.namespace=default \
    --set wlsDomain.domainUID=domain1 \
    --set traefik.hostname=domain1.org

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain2-ingress \
    --set wlsDomain.namespace=test1 \
    --set wlsDomain.domainUID=domain2 \
    --set traefik.hostname=domain2.org

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain3-ingress \
    --set wlsDomain.namespace=test1 \
    --set wlsDomain.domainUID=domain3 \
    --set traefik.hostname=domain3.org
}

function delIngress() {
  echo "delete Ingress"
  helm delete --purge domain1-ingress
  helm delete --purge domain2-ingress
  helm delete --purge domain3-ingress
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "Commands:"
  echo "  createOpt: to create the LB operator"
  echo
  echo "  delOpt: to delete the LB operator"
  echo
  echo "  createIngress: to create Ingress of domains"
  echo
  echo "  delIngress: to delete Ingress of domains"
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
