#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# By default, this script deploys a model configmap named
# 'DOMAIN_UID-wdt-config-map' from files in 
# 'WORKDIR/model-configmaps/datasource'.
#
# Note that this config map is only loaded at runtime if:
#  - its referenced secret(s) are deployed 
#    (See 'INCLUDE_MODEL_CONFIGMAP' in 'create-secrets.sh'.)
#  - a domain resource references these secrets in
#    configuration.model.secrets
#    (See 'INCLUDE_MODEL_CONFIGMAP' in 'stage-domain-resource.sh'.)
#  - a domain resource references the configmap
#    in configuration.model.configMap.
#    (See 'INCLUDE_MODEL_CONFIGMAP' in 'stage-domain-resource.sh')
#
# Optional command line params:
#
#  -dry kubectl        : Show the kubectl commands (prefixed with 'dryun:')
#                        but do not perform them.
#
#  -dry yaml           : Show the yaml (prefixed with 'dryun:') 
#                        but do not execute it.
#
# Optional environment variables:
#
#   MODEL_CONFIGMAP_DIR       - defaults to 'model-configmaps/datasource' 
#                                (relative to WORKDIR)
#   Others (see README):
#     WORKDIR, DOMAIN_UID, DOMAIN_NAMESPACE   
#

set -eu
set -o pipefail
SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

$WORKDIR/utils/create-configmap.sh -c ${DOMAIN_UID}-wdt-config-map -f ${WORKDIR}/${MODEL_CONFIGMAP_DIR} ${1:-} ${2:-}
