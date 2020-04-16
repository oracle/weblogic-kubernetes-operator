#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# By default, this script deploys a configmap named '${DOMAIN_UID}-wdt-config-map'
# from files in the 'WORKDIR/configmap' directory that were staged by 'stage-configmap.sh'.
#
# If the 'CONFIGMAP_DIR' env var is customized, then this script instead deploys a
# configmap from the given directory.
#
# Note that this config map is only loaded at runtime if:
#  - its referenced secret(s) are deployed 
#  - a domain resource references these secrets in
#    configuration.model.secrets
#  - a domain resource references the configmap
#    in configuration.model.configMap.
# (See 'INCLUDE_CONFIGMAP' in 'stage-domain-resource.sh' and 'create-secrets.sh'.)
#
# Optional environment variables:
#
#   WORKDIR                  - Working directory for the sample with at least
#                              10g of space. Defaults to 
#                              '/tmp/$USER/model-in-image-sample-work-dir'.
#   DOMAIN_UID                - defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE          - defaults to '${DOMAIN_UID}-ns'
#   CONFIGMAP_DIR             - defaults to '${WORKDIR}/configmap' (a directory populated by stage-configmap.sh)
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

echo "@@ Info: Creating sample wdt configmap"

$SCRIPTDIR/util-create-configmap.sh -c ${DOMAIN_UID}-wdt-config-map -f ${CONFIGMAP_DIR}
