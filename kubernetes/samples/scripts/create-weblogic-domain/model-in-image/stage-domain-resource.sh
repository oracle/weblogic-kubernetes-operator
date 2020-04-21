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
#   CUSTOM_DOMAIN_NAME       - default 'domain1'.
#   DOMAIN_UID               - default 'sample-domain1'
#   DOMAIN_NAMESPACE         - default '${DOMAIN_UID}-ns'
#   MODEL_IMAGE_NAME         - default 'model-in-image'
#   MODEL_IMAGE_TAG          - default 'v1'
#   WDT_DOMAIN_TYPE          - WLS (default), RestrictedJRF, or JRF
#   INCLUDE_MODEL_CONFIGMAP  - defaults 'false'
#                              Set to true to uncomment the template's
#                              'model.configuration.configMap' reference,
#                              and to uncomment the secret that's referenced
#                              by the model file in this config map.
#
#   DOMAIN_RESOURCE_TEMPLATE - use as the domain resource template
#     default 'sample-domain-resource/k8s-domain.yaml.template-WDT_DOMAIN_TYPE'

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

for var in DOMAIN_UID \
           DOMAIN_NAMESPACE \
           CUSTOM_DOMAIN_NAME \
           WDT_DOMAIN_TYPE \
           MODEL_IMAGE_NAME \
           MODEL_IMAGE_TAG \
           INCLUDE_MODEL_CONFIGMAP \
           DOMAIN_RESOURCE_TEMPLATE
do
  echo "@@ Info: ${var}=${!var}"
done

function timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

domain_resource_file=${WORKDIR}/k8s-domain.yaml

echo "@@"
echo "@@ Info: Creating domain resource file 'WORKDIR/k8s-domain.yaml' from 'DOMAIN_RESOURCE_TEMPLATE'"

if [ -e "${domain_resource_file}" ]; then
  save_file=${WORKDIR}/k8s-domain-saved/k8s-domain.yaml.$(timestamp)
  mkdir -p $(dirname $save_file)
  echo "@@"
  echo "@@ Notice! An old version of the domain resource file already exists and will be replaced."
  echo "@@ Notice! Saving old version of the domain resource file to 'WORKDIR/k8s-domain-saved/$(basename $save_file)'."
  cp ${domain_resource_file} ${save_file}
fi

echo "@@"

cp ${DOMAIN_RESOURCE_TEMPLATE} ${domain_resource_file}

for template_var in WDT_DOMAIN_TYPE \
                    CUSTOM_DOMAIN_NAME \
                    DOMAIN_UID \
                    DOMAIN_NAMESPACE \
                    MODEL_IMAGE_NAME \
                    MODEL_IMAGE_TAG
do
  sed -i -e "s;@@${template_var}@@;${!template_var};" $domain_resource_file
done


if [ "${INCLUDE_MODEL_CONFIGMAP}" = "true" ]; then
  # we're going to deploy and use the model.configuration.configMap, and this
  # configmap depends on the datasource-secret.
  sed -i -e "s;\#\(configMap:\);\1;"           $domain_resource_file
  sed -i -e "s;\#\(secrets:\);\1;"             $domain_resource_file
  sed -i -e "s;\#\(-.*datasource-secret\);\1;" $domain_resource_file
fi

echo "@@ Info: Done."
