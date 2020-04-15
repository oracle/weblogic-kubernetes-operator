#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is an example of how to configure a model-in-image domain resource.
# This script creates $WORKDIR/k8s-domain.yaml from a template.
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
#                              of k8s-domain.yaml.template 

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

DOMAIN_RESOURCE_FILE=${WORKDIR}/k8s-domain.yaml

echo "@@ Info: Creating domain resource file '${DOMAIN_RESOURCE_FILE}' from '${DOMAIN_RESOURCE_TEMPLATE}'"

cp ${DOMAIN_RESOURCE_TEMPLATE} ${DOMAIN_RESOURCE_FILE}

for template_var in WDT_DOMAIN_TYPE \
                    CUSTOM_DOMAIN_NAME \
                    DOMAIN_UID \
                    DOMAIN_NAMESPACE \
                    MODEL_IMAGE_NAME \
                    MODEL_IMAGE_TAG
do
  sed -i -e "s;@@${template_var}@@;${!template_var};" $DOMAIN_RESOURCE_FILE
done

# TBD
if [ ! -z "${CONFIGMAP_DIR:-}" ]; then
  # Since CONFIGMAPDIR is set, then assume we're going to deploy and use the model.configuration.configMap.
  sed -i -e "s/\#\(secrets\):/\1:/" $DOMAIN_RESOURCE_FILE
  sed -i -e "s/\#\(-.*datasource-secret\)/\1/" $DOMAIN_RESOURCE_FILE
fi
