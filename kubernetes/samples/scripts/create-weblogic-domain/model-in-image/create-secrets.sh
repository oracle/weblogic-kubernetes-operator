#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script deploys secrets for the Model in Image sample. 
#
# Optional environment variables:
#
#   WORKDIR                  - Working directory for the sample with at least
#                              10g of space. Defaults to 
#                              '/tmp/$USER/model-in-image-sample-work-dir'.
#   DOMAIN_UID                - defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE          - defaults to '${DOMAIN_UID}-ns'
#   WDT_DOMAIN_TYPE           - WLS (default), RestrictedJRF, or JRF
#   DB_NAMESPACE              - default (default)
#   INCLUDE_MODEL_CONFIGMAP   - 'true' if sample is deploying its
#                               configuration.model.configMap
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

#
# WebLogic Credential Secret referenced by domain resource 
# field 'spec.weblogicCredentialsSecret'.
#

echo "@@ Info: Creating weblogic domain secret"
$SCRIPTDIR/util-create-secret.sh -s ${DOMAIN_UID}-weblogic-credentials \
  -l username=weblogic \
  -l password=welcome1

#
# Model runtime encryption secret referenced by domain resource
# field 'spec.configuration.model.runtimeEncryptionSecret'.
# This secret can have any password but the password must remain
# the same throughout the life of a running model domain.
#

set +e
sname="${DOMAIN_UID}-runtime-encryption-secret"
kubectl -n $DOMAIN_NAMESPACE get secret $sname > /dev/null 2>&1
errcode=$?
set -e
if [ $errcode -eq 0 ]; then
  echo "@@ Info: Skipping creation of the 'model runtime encryption secret because the secret already exists and must stay the same for the life of the model's domain resource."
else
  echo "@@ Info: Creating model runtime encryption secret"
  $SCRIPTDIR/util-create-secret.sh -s ${DOMAIN_UID}-runtime-encryption-secret \
    -l password=$(uuidgen).$SECONDS.$PPID.$RANDOM
fi

#
# JRF Domain's RCU secret and wallet password secret. Only needed for JRF
# domains.
#

if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  echo "@@ Info: Creating rcu access secret (referenced by model yaml macros if domain type is JRF)"
  $SCRIPTDIR/util-create-secret.sh -s ${DOMAIN_UID}-rcu-access \
    -l rcu_prefix=FMW1 \
    -l rcu_schema_password=Oradoc_db1 \
    -l rcu_db_conn_string=oracle-db.${DB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s
  echo "@@ Info: Creating OPSS wallet password secret (ignored unless domain type is JRF)"
  $SCRIPTDIR/util-create-secret.sh -s ${DOMAIN_UID}-opss-wallet-password-secret \
    -l walletPassword=welcome1
fi

#
# Datasource access secret. This is needed for the sample's optional
# 'configuration.model.configMap' because it contains a model file
# with an '@@SECRET' macro that references this datasource secret.
#

if [ "${INCLUDE_MODEL_CONFIGMAP}" = "true" ]; then
  # this secret is referenced by the datasource in this sample's optional config.configMap
  echo "@@ Info: Creating datasource secret"
  $SCRIPTDIR/util-create-secret.sh \
    -n ${DOMAIN_NAMESPACE} \
    -s ${DOMAIN_UID}-datasource-secret \
    -l password=Oradoc_db1 \
    -l url=jdbc:oracle:thin:@oracle-db.${DB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s
fi
