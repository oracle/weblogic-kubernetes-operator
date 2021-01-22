#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script deploys secrets for the Model in Image sample,
# including extra secretes for the JRF domain type or for
# the datasource config map as needed.
#
# Optional parameters:
#
#   -dry kubectl              - Dry run. Show but don't do. Dry run
#   -dry yaml                   output is prefixed with 'dryrun:'.
#
# Optional environment variables (see README for details):
#
#   WORKDIR, DOMAIN_UID, DOMAIN_NAMESPACE, WDT_DOMAIN_TYPE,
#   DB_NAMESPACE, INCLUDE_MODEL_CONFIGMAP, CORRECTED_DATASOURCE_SECRET
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

DRY_RUN=""
if [ "${1:-}" = "-dry" ]; then
  DRY_RUN="-dry $2"
fi

#
# WebLogic Credential Secret referenced by domain resource 
# field 'spec.weblogicCredentialsSecret'.
#

echo "@@ Info: Creating weblogic domain secret"
$WORKDIR/utils/create-secret.sh $DRY_RUN -s ${DOMAIN_UID}-weblogic-credentials \
  -d $DOMAIN_UID -n $DOMAIN_NAMESPACE \
  -l username=weblogic \
  -l password=welcome1

#
# Model runtime encryption secret referenced by domain resource
# field 'spec.configuration.model.runtimeEncryptionSecret'.
# This secret can have any password but the password must remain
# the same throughout the life of a running model domain.
#

echo "@@ Info: Creating model runtime encryption secret"
$WORKDIR/utils/create-secret.sh $DRY_RUN -s ${DOMAIN_UID}-runtime-encryption-secret \
  -d $DOMAIN_UID -n $DOMAIN_NAMESPACE \
  -l password=my_runtime_password

#
# JRF Domain's RCU secret and wallet password secret. Only needed for JRF
# domains.
#

if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  echo "@@ Info: Creating rcu access secret (referenced by model yaml macros if domain type is JRF)"
  $WORKDIR/utils/create-secret.sh $DRY_RUN -s ${DOMAIN_UID}-rcu-access \
    -d $DOMAIN_UID -n $DOMAIN_NAMESPACE \
    -l rcu_prefix=FMW${CUSTOM_DOMAIN_NAME} \
    -l rcu_schema_password=Oradoc_db1 \
    -l rcu_db_conn_string=oracle-db.${DB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s
  echo "@@ Info: Creating OPSS wallet password secret (ignored unless domain type is JRF)"
  $WORKDIR/utils/create-secret.sh $DRY_RUN -s ${DOMAIN_UID}-opss-wallet-password-secret \
    -d $DOMAIN_UID -n $DOMAIN_NAMESPACE \
    -l walletPassword=welcome1
fi

#
# Datasource access secret. This is needed for the sample's optional
# 'configuration.model.configMap' because it contains a model file
# with an '@@SECRET' macro that references this datasource secret.
#

if [ "${INCLUDE_MODEL_CONFIGMAP}" = "true" ]; then
  if [ "${CORRECTED_DATASOURCE_SECRET}" = "true" ]; then
    echo "@@ Info: Creating corrected datasource secret with correct password and updated max-capacity"
    dspw=Oradoc_db1
    dscap=10
  else
    # specify an incorrect password and a minimal maximum capacity
    # because we demonstrate dynamically correcting
    # them using online updates in the Update 4 use case
    echo "@@ Info: Creating incorrect datasource secret with minimal max-capacity and the wrong password"
    dspw=incorrect_password
    dscap=1
  fi
  $WORKDIR/utils/create-secret.sh $DRY_RUN \
    -d $DOMAIN_UID -n $DOMAIN_NAMESPACE \
    -n ${DOMAIN_NAMESPACE} \
    -s ${DOMAIN_UID}-datasource-secret \
    -l "user=sys as sysdba" \
    -l password=$dspw \
    -l max-capacity=$dscap \
    -l url=jdbc:oracle:thin:@oracle-db.${DB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s
fi
