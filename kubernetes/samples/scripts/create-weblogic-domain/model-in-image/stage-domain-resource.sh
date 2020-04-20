#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is an example of how to configure a model-in-image domain resource.
#
# This script creates '$WORKDIR/k8s-domain.yaml' from a template.
#
# Warning!!: '$WORKDIR/k8s-domain.yaml' is overwritten if it already exists.
#
# Optional environment variables (see custom-env.sh for details):
#
#   WORKDIR                  - Working directory for the sample with at least
#                              10g of space. Defaults to 
#                              '/tmp/$USER/model-in-image-sample-work-dir'.
#   CUSTOM_DOMAIN_NAME       - defaults to 'domain1'.
#   DOMAIN_UID               - defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE         - defaults to '${DOMAIN_UID}-ns'
#   MODEL_IMAGE_NAME         - defaults to 'model-in-image'
#   MODEL_IMAGE_TAG          - defaults to 'v1'
#   WDT_DOMAIN_TYPE          - WLS (default), RestrictedJRF, or JRF
#   DOMAIN_RESOURCE_TEMPLATE - use this file for a domain resource template instead
#                              of TBD/k8s-domain.yaml.template-TBD
#   INCLUDE_MODEL_CONFIGMAP  - defaults to 'false'
#                              Set to true to uncomment the template's
#                              'model.configuration.configMap' reference,
#                              and to uncomment the secret that's referenced
#                              by the model file in this config map.

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

domain_resource_file=${WORKDIR}/k8s-domain.yaml

echo "@@ Info: Creating domain resource file '${domain_resource_file}' from '${DOMAIN_RESOURCE_TEMPLATE}'"

cp ${DOMAIN_RESOURCE_TEMPLATE} ${domain_resource_file}

for template_var in WDT_DOMAIN_TYPE \
                    CUSTOM_DOMAIN_NAME \
                    DOMAIN_UID \
                    DOMAIN_NAMESPACE \
                    MODEL_IMAGE_NAME \
                    MODEL_IMAGE_TAG
do
  echo "@@ Info: ${template_var}=${!template_var}"
  sed -i -e "s;@@${template_var}@@;${!template_var};" $domain_resource_file
done

echo "@@ Info: INCLUDE_MODEL_CONFIGMAP=$INCLUDE_MODEL_CONFIGMAP"

if [ "${INCLUDE_MODEL_CONFIGMAP}" = "true" ]; then
  # we're going to deploy and use the model.configuration.configMap, and this
  # configmap depends on the datasource-secret.
  sed -i -e "s;\#\(configMap:\);\1;"           $domain_resource_file
  sed -i -e "s;\#\(secrets:\);\1;"             $domain_resource_file
  sed -i -e "s;\#\(-.*datasource-secret\);\1;" $domain_resource_file
fi

# TBD add logic below and add DOMAIN_RESTART_VERSION to the template, env-custom, etc.
# nextRV=$(kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.restartVersion}' --ignore-not-found)
# nextRV=$(echo $nextRV | sed 's/[^0-9]//')
# nextRV=${nextRV:-0}
# nextRV=$((nextRV + 1))
# export DOMAIN_RESTART_VERSION=${DOMAIN_RESTART_VERSION:-$nextRV}
