#!/bin/bash

# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Helm uninstall/install an operator that monitors DOMAIN_NAMESPACE.
#
# This script is not necessary if the operator is already running
# and monitoring DOMAIN_NAMESPACE.
#
# This script skips itself if it can find a record of its
# last run and the cksum matches the current cksum.

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -u

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}

OPER_NAME=${OPER_NAME:-sample-weblogic-operator}
OPER_NAMESPACE=${OPER_NAMESPACE:-${OPER_NAME}-ns}
OPER_SA=${OPER_SA:-${OPER_NAME}-sa}

OPER_IMAGE_TAG=${OPER_IMAGE_TAG:-test}
OPER_IMAGE_NAME=${OPER_IMAGE_NAME:-weblogic-kubernetes-operator}
OPER_IMAGE=${OPER_IMAGE_NAME}:${OPER_IMAGE_TAG}

mkdir -p $WORKDIR/test-out

#
# Do not re-install if operator is up and running and has same setting as last deploy
#

if [ -e $WORKDIR/test-out/operator-values.orig ]; then
  helm get values ${OPER_NAME} -n ${OPER_NAMESPACE} > $WORKDIR/test-out/operator-values.cur 2>&1
  helm list -n ${OPER_NAMESPACE} | awk '{ print $1 }' >> $WORKDIR/test-out/operator-values.cur
  for evar in DOMAIN_NAMESPACE OPER_NAMESPACE OPER_NAME OPER_IMAGE OPER_SA ; do
    echo "${evar}=${!evar}" >> $WORKDIR/test-out/operator-values.cur
  done
  if [ "$(cat $WORKDIR/test-out/operator-values.cur)" = "$(cat $WORKDIR/test-out/operator-values.orig)" ]; then
    echo "@@"
    echo "@@ Operator already running. Skipping."
    echo "@@"
    echo "@@ log command: kubectl logs -n $OPER_NAMESPACE -c weblogic-operator deployments/weblogic-operator"
    exit
  fi
fi

set +e

kubectl create namespace $DOMAIN_NAMESPACE
kubectl create namespace $OPER_NAMESPACE
kubectl create serviceaccount -n $OPER_NAMESPACE $OPER_SA

helm uninstall $OPER_NAME -n $OPER_NAMESPACE

set -eu
cd ${SRCDIR}

helm install $OPER_NAME kubernetes/charts/weblogic-operator \
  --namespace $OPER_NAMESPACE \
  --set       image=$OPER_IMAGE \
  --set       serviceAccount=$OPER_SA \
  --set       "domainNamespaces={$DOMAIN_NAMESPACE}" \
  --set       "javaLoggingLevel=INFO" \
  --set       "featureGates=CommonMounts=${DO_CM}" \
  --wait


kubectl get deployments -n $OPER_NAMESPACE

helm get values ${OPER_NAME} -n ${OPER_NAMESPACE} > $WORKDIR/test-out/operator-values.orig 2>&1
helm list -n ${OPER_NAMESPACE} | awk '{ print $1 }' >> $WORKDIR/test-out/operator-values.orig
for evar in DOMAIN_NAMESPACE OPER_NAMESPACE OPER_NAME OPER_IMAGE OPER_SA ; do
  echo "${evar}=${!evar}" >> $WORKDIR/test-out/operator-values.orig
done

echo "@@ log command: kubectl logs -n $OPER_NAMESPACE -c weblogic-operator deployments/weblogic-operator"

