#!/bin/bash

# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
#   DOMAIN_NAMESPACE:  sample-domain1-ns
# 
# This script also tracks an release checksum in the
# WORKDIR/test-out directory so it can skip itself
# if one already occurred.
#

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -eu
set -o pipefail

source $TESTDIR/test-env.sh

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

TRAEFIK_NAME=${TRAEFIK_NAME:-traefik-operator}
TRAEFIK_NAMESPACE=${TRAEFIK_NAMESPACE:-traefik-operator-ns}
TRAEFIK_HTTP_NODEPORT=${TRAEFIK_HTTP_NODEPORT:-30305}
TRAEFIK_HTTPS_NODEPORT=${TRAEFIK_HTTPS_NODEPORT:-30433}

DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}

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
for evar in DOMAIN_NAMESPACE TRAEFIK_NAMESPACE TRAEFIK_NAME TRAEFIK_HTTP_NODEPORT TRAEFIK_HTTPS_NODEPORT; do
  echo "${evar}=${!evar}" >> $WORKDIR/test-out/traefik-values.cur
done
if [ $res -eq 0 ] \
   && [ -e "$WORKDIR/test-out/traefik-values.orig" ] \
   && [ "$(cat $WORKDIR/test-out/traefik-values.cur)" = "$(cat $WORKDIR/test-out/traefik-values.orig)" ]; then
  echo "@@"
  echo "@@ Traefik already installed. Skipping uninstall/install. Current values:"
  cat $WORKDIR/test-out/traefik-values.orig
  echo "@@"
else
  set +e
  echo "@@"
  echo "@@ Uninstalling old install (if any) using 'helm uninstall $TRAEFIK_NAME -n $TRAEFIK_NAMESPACE'"
  echo "@@"
  helm uninstall $TRAEFIK_NAME -n $TRAEFIK_NAMESPACE
  echo "@@ Creating traefik namespace '$TRAEFIK_NAMESPACE' and domain namepace '$DOMAIN_NAMESPACE'"
  kubectl create namespace $TRAEFIK_NAMESPACE
  kubectl create namespace $DOMAIN_NAMESPACE
  set -e

  cd ${SRCDIR}

  echo "@@ Installing traefik"

  # you only need to add the repo once, but we do it every time for simplicity
  helm repo add traefik https://containous.github.io/traefik-helm-chart
  helm repo update

  set -x

  helm install ${TRAEFIK_NAME} traefik/traefik \
    --namespace $TRAEFIK_NAMESPACE \
    --set "kubernetes.namespaces={$TRAEFIK_NAMESPACE,$DOMAIN_NAMESPACE}" \
    --set "ports.web.nodePort=${TRAEFIK_HTTP_NODEPORT}" \
    --set "ports.websecure.nodePort=${TRAEFIK_HTTPS_NODEPORT}" 

  set +x

  # Save Traefik settings (we will check this if this script is run again)
  helm get values ${TRAEFIK_NAME} -n ${TRAEFIK_NAMESPACE} > $WORKDIR/test-out/traefik-values.orig 2>&1
  for evar in DOMAIN_NAMESPACE TRAEFIK_NAMESPACE TRAEFIK_NAME TRAEFIK_HTTP_NODEPORT TRAEFIK_HTTPS_NODEPORT; do
    echo "${evar}=${!evar}" >> $WORKDIR/test-out/traefik-values.orig
  done
fi

cat<<EOF

    HTTP node port:
      kubectl get svc $TRAEFIK_NAME --namespace $TRAEFIK_NAMESPACE -o=jsonpath='{.spec.ports[?(@.name=="web")].nodePort}'
      =$(kubectl get svc $TRAEFIK_NAME --namespace $TRAEFIK_NAMESPACE -o=jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')

    HTTPS node port:
      kubectl get svc $TRAEFIK_NAME --namespace $TRAEFIK_NAMESPACE -o=jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}'
      =$(kubectl get svc $TRAEFIK_NAME --namespace $TRAEFIK_NAMESPACE -o=jsonpath='{.spec.ports[?(@.name=="websecure")].nodePort}')
EOF
