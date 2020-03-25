#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is an example of how to setup a WebLogic Kubernetes Cluster for model-in-image. This
# script can to be called once an model-in-image image is prepared via "./build.sh".
#
# Optional environment variables:
#
#   WORKDIR                  - Working directory for the sample with at least
#                              10g of space. Defaults to 
#                              '/tmp/$USER/model-in-image-sample-work-dir'.
#   DOMAIN_UID                - defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE          - defaults to '${DOMAIN_UID}-ns'
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

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}
WDTCONFIGMAPDIR=${WDTCONFIGMAPDIR:-$WORKDIR/wdtconfigmap}
WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}
RCUDB_NAMESPACE=${RCUDB_NAMESPACE:-default}

echo "@@ Info: WORKDIR='$WORKDIR'."
mkdir -p ${WORKDIR}

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
$SCRIPTDIR/create_secret.sh -s ${DOMAIN_UID}-weblogic-credentials \
  -l username=weblogic \
  -l password=welcome1


echo "@@ Info: Creating rcu access secret (referenced by model yaml macros if domain type is JRF)"
$SCRIPTDIR/create_secret.sh -s ${DOMAIN_UID}-rcu-access \
  -l rcu_prefix=FMW1 \
  -l rcu_schema_password=Oradoc_db1 \
  -l rcu_db_conn_string=oracle-db.${RCUDB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s

echo "@@ Info: Creating OPSS wallet password secret (ignored unless domain type is JRF)"
$SCRIPTDIR/create_secret.sh -s ${DOMAIN_UID}-opss-wallet-password-secret \
  -l walletPassword=welcome1

echo "@@ Info: Creating model runtime encryption secret"
$SCRIPTDIR/create_secret.sh -s ${DOMAIN_UID}-runtime-encryption-secret \
  -l password=$(uuidgen).$SECONDS.$PPID.$RANDOM


echo "@@ Info: Creating sample wdt configmap (optional)"
$SCRIPTDIR/create_configmap.sh -c ${DOMAIN_UID}-wdt-config-map -f ${WDTCONFIGMAPDIR}


echo "@@ Info: Creating domain resource yaml '$WORKDIR/k8s-domain.yaml'."
$SCRIPTDIR/create_domain.sh


echo "@@ Info: Applying domain resource yaml '$WORKDIR/k8s-domain.yaml'"
( set -x
kubectl apply -f $WORKDIR/k8s-domain.yaml
)

echo
echo "@@ Info: Your Model in Image domain resource deployed!"
echo
echo "@@ Info: To watch pods start and get their status, run 'kubectl get pods -n ${DOMAIN_NAMESPACE} --watch' and ctrl-c when done watching."
echo
echo "@@ Info: If the introspector job fails or you see any other unexpected issue, see 'User Guide -> Manage WebLogic Domains -> Model in Image -> Debugging' in the documentation."
