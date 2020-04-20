#!/bin/bash

# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Summary:
#
# This script helm uninstalls and installs Traefik TRAEFIK_NAME
# at node-port 30305 in namespace TRAEFIK_NAMESPACE and ensures
# it monitors namespace DOMAIN_NAMESPACE. It uses this operator
# release's sample helm chart in 'kubernetes/samples/charts/traefik'.
#
# Defaults: 
#   WORKDIR:           /tmp/$USER/model-in-image-sample-work-dir
#   TRAEFIK_NAME:      traefik-operator
#   TRAEFIK_NAMESPACE: ${TRAEFIK_NAME}-ns
#   DOMAIN_UID:        sample-domain1
#   DOMAIN_NAMESPACE:  ${DOMAIN_UID}-ns
# 
# This script also tracks an release checksum in the
# WORKDIR/test-out directory so it can skip itself
# if one already occurred.
#

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -eu
set -o pipefail

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

TRAEFIK_NAME=${TRAEFIK_NAME:-traefik-operator}
TRAEFIK_NAMESPACE=${TRAEFIK_NAMESPACE:-${TRAEFIK_NAME}-ns}

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

mkdir -p $WORKDIR/test-out

function kubehost() {
  kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;'
}

#
# Helm uninstall then install traefik
# Skip if it's up and running and it has the same external ports and namespace values
#

set +e
helm get values ${TRAEFIK_NAME} -n ${TRAEFIK_NAMESPACE} > $WORKDIR/test-out/traefik-values.cur 2>&1
res=$?
set -e
echo ${DOMAIN_NAMESPACE} >> $WORKDIR/test-out/traefik-values.cur
if [ $res -eq 0 ] \
   && [ -e "$WORKDIR/test-out/traefik-values.orig" ] \
   && [ "$(cat $WORKDIR/test-out/traefik-values.cur)" = "$(cat $WORKDIR/test-out/traefik-values.orig)" ]; then
  echo "@@"
  echo "@@ Traefik already installed. Skipping uninstall/install."
  echo "@@"
else
  set +e
  helm uninstall $TRAEFIK_NAME -n $TRAEFIK_NAMESPACE
  kubectl create namespace $TRAEFIK_NAMESPACE
  kubectl create namespace $DOMAIN_NAMESPACE
  set -e

  cd ${SRCDIR}

  # you only need to add the repo once, but we do it every time for simplicity
  helm repo add stable https://kubernetes-charts.storage.googleapis.com/

  helm install ${TRAEFIK_NAME} stable/traefik \
    --namespace $TRAEFIK_NAMESPACE \
    --values kubernetes/samples/charts/traefik/values.yaml \
    --set "kubernetes.namespaces={$TRAEFIK_NAMESPACE,$DOMAIN_NAMESPACE}" \
    --wait

  # Save Traefik settings (we will check this if this script is run again)
  helm get values ${TRAEFIK_NAME} -n ${TRAEFIK_NAMESPACE} > $WORKDIR/test-out/traefik-values.orig 2>&1
  echo ${DOMAIN_NAMESPACE} >> $WORKDIR/test-out/traefik-values.orig
fi
