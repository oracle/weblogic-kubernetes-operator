#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This is an example of how to create and deploy a model-in-image domain resource.
# This scripts creates ./k8s-domain.yaml from a template, and deploys it.
#
# This script is called from run_domain.sh, which also sets up the resources
# that the domain resource depends on.
#
# Expects the following env vars to already be set:
#
#   WORKDIR - working directory for the sample with at least 10g of space
#
# Optional:
#
#   DOMAIN_UID               - defaults to 'domain1'
#   DOMAIN_NAMESPACE         - defaults to 'sample-${DOMAIN_UID}-ns'
#   MODEL_IMAGE_NAME         - defaults to 'model-in-image'
#   MODEL_IMAGE_TAG          - defaults to 'v1'
#   DOMAIN_RESOURCE_TEMPLATE - use this file for a domain resource template instead
#                              of k8s-domain.yaml.template 
#   WDT_DOMAIN_TYPE          - WLS (default), RestrictedJRF, or JRF
#

set -eu

cd ${WORKDIR}

DOMAIN_UID=${DOMAIN_UID:-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-${DOMAIN_UID}-ns}
MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
DOMAIN_RESOURCE_TEMPLATE="${DOMAIN_RESOURCE_TEMPLATE:-./k8s-domain.yaml.template}"
WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}
DOMAIN_RESOURCE_FILE="./k8s-domain.yaml"

echo "@@ Info: Creating domain resource file '${DOMAIN_RESOURCE_FILE}' from '${DOMAIN_RESOURCE_TEMPLATE}'"

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF" ]; then
  echo "Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1
fi

if [ "${DOMAIN_RESOURCE_FILE}" = "${DOMAIN_RESOURCE_TEMPLATE}" ]; then
  echo @@ Error: source and target file match.
  exit 1
fi

cp ${DOMAIN_RESOURCE_TEMPLATE} ${DOMAIN_RESOURCE_FILE}

for template_var in WDT_DOMAIN_TYPE DOMAIN_UID DOMAIN_NAMESPACE MODEL_IMAGE_NAME MODEL_IMAGE_TAG; do
  sed -i "s/@@${template_var}@@/${!template_var}/" $DOMAIN_RESOURCE_FILE
done

if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  sed -i 's/\#\(opss\):/\1:/' $DOMAIN_RESOURCE_FILE
  sed -i 's/\#\(walletPasswordSecret\):/\1:/' $DOMAIN_RESOURCE_FILE
fi

# TBD this is temporary until we replace nginx with traefik
cp k8s-nginx.yaml.template k8s-nginx.yaml
sed -i "s/@@DOMAIN_UID@@/${DOMAIN_UID}/" k8s-nginx.yaml
sed -i "s/@@DOMAIN_NAMESPACE@@/${DOMAIN_NAMESPACE}/" k8s-nginx.yaml

echo "@@ Info: Applying domain resource yaml '$DOMAIN_RESOURCE_FILE'"
( set -x
kubectl apply -f $DOMAIN_RESOURCE_FILE
)
