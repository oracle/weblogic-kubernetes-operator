#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This is an example of how to setup a WebLogic Kubernetes Cluster for model-in-image. This
# script can to be called once an model-in-image image is prepared via "./build.sh".
#
# Expects the following env vars to already be set:
#
#   WORKDIR - working directory for the sample with at least 10g of space
#
# Optional:
#
#   DOMAIN_UID                - defaults to 'domain1'
#   DOMAIN_NAMESPACE          - defaults to 'sample-${DOMAIN_UID}-ns'
#   WDTCONFIGMAPDIR           - defaults to './wdtconfigmap' (a directory populated by build_model.sh)
#   MODEL_IMAGE_NAME          - defaults to 'model-in-image'
#   MODEL_IMAGE_TAG           - defaults to 'v1'
#   DOMAIN_RESOURCE_TEMPLATE  - use this file for a domain resource template instead
#                               of k8s-domain.yaml.template
#   WDT_DOMAIN_TYPE           - WLS (default), RestrictedJRF, or JRF
#   RCUDB_NAMESPACE           - default (default)
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

DOMAIN_UID=${DOMAIN_UID:-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-${DOMAIN_UID}-ns}
WDTCONFIGMAPDIR=${WDTCONFIGMAPDIR:-$WORKDIR/wdtconfigmap}
WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}
RCUDB_NAMESPACE=${RCUDB_NAMESPACE:-default}

case "${WDT_DOMAIN_TYPE}" in
  WLS|JRF|RestrictedJRF) ;;
  *) echo "Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1
esac

echo "@@ Info: Deleting weblogic domain '${DOMAIN_UID}' if it already exists"
( set -x
kubectl -n ${DOMAIN_NAMESPACE} delete domain ${DOMAIN_UID} --ignore-not-found
)
while [ 1 -eq 1 ]; do
  cur_pods=$(kubectl -n ${DOMAIN_NAMESPACE} get pods -l weblogic.domainUID=${DOMAIN_UID} -o=jsonpath='{.items[*].metadata.name}')
  [ "$cur_pods" = "" ] && break
  echo "@@ Info: Waiting for old domain's pods '$cur_pods' to exit, seconds=${SECONDS}."
  sleep 5
done

echo "@@ Info: Creating weblogic domain secret"
$SCRIPTDIR/create_secret.sh -s sample-${DOMAIN_UID}-weblogic-credentials \
  -l username=weblogic \
  -l password=welcome1


echo "@@ Info: Creating rcu access secret (referenced by model yaml macros if domain type is JRF)"
$SCRIPTDIR/create_secret.sh -s sample-${DOMAIN_UID}-rcu-access \
  -l rcu_prefix=FMW1 \
  -l rcu_schema_password=Oradoc_db1 \
  -l rcu_admin_password=Oradoc_db1 \
  -l rcu_db_conn_string=oracle-db.${RCUDB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s

# TODO: Tom
# This is for the configuration.model.runtimeEncryptionSecret name literal is password

$SCRIPTDIR/create_secret.sh -s sample-${DOMAIN_UID}-model-encryption-secret \
  -l password=weblogic

#kubectl -n sample-domain1-ns delete secret domain1-model-encryption-secret --ignore-not-found
#
#kubectl -n sample-domain1-ns \
#  create secret generic domain1-model-encryption-secret \
#  --from-literal=password=weblogic
#
#kubectl -n sample-domain1-ns \
#  label secret domain1-model-encryption-secret \
#  weblogic.domainUID=domain1

echo "@@ Info: Creating OPSS wallet password secret (ignored unless domain type is JRF)"

$SCRIPTDIR/create_secret.sh -s sample-${DOMAIN_UID}-opss-wallet-password-secret \
  -l walletPassword=welcome1


echo "@@ Info: Creating sample wdt configmap (optional)"
$SCRIPTDIR/create_configmap.sh -c ${DOMAIN_UID}-wdt-config-map -f ${WDTCONFIGMAPDIR}


echo "@@ Info: Creating domain resource yaml '$WORKDIR/k8s-domain.yaml'."
$SCRIPTDIR/create_domain.sh


echo "@@ Info: Applying domain resource yaml '$WORKDIR/k8s-domain.yaml'"
( set -x
kubectl apply -f $WORKDIR/k8s-domain.yaml
)


echo "@@ Info: Getting pod status - ctrl-c when all is running and ready to exit"
( set -x
kubectl get pods -n ${DOMAIN_NAMESPACE} --watch
)
