#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is an example of how to configure a model-in-image domain resource.
# This scripts creates $WORKDIR/k8s-domain.yaml from a template.
#
# This script is called from run_domain.sh, which also sets up the resources
# that the domain resource depends on plus deploys the domain resource.
#
# Optional environment variables:
#   WORKDIR                  - Working directory for the sample with at least
#                              10g of space. Defaults to 
#                              '/tmp/$USER/model-in-image-sample-work-dir'.
#   DOMAIN_UID               - defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE         - defaults to '${DOMAIN_UID}-ns'
#   MODEL_IMAGE_NAME         - defaults to 'model-in-image'
#   MODEL_IMAGE_TAG          - defaults to 'v1'
#   DOMAIN_RESOURCE_TEMPLATE - use this file for a domain resource template instead
#                              of k8s-domain.yaml.template 
#   WDT_DOMAIN_TYPE          - WLS (default), RestrictedJRF, or JRF
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}
MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
DOMAIN_RESOURCE_TEMPLATE="${DOMAIN_RESOURCE_TEMPLATE:-$SCRIPTDIR/k8s-domain.yaml.template}"
WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}
DOMAIN_RESOURCE_FILE="${WORKDIR}/k8s-domain.yaml"

echo "@@ Info: WORKDIR='$WORKDIR'."

source ${WORKDIR}/env.sh

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
  sed -i -e "s;@@${template_var}@@;${!template_var};" $DOMAIN_RESOURCE_FILE
done

if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  # uncomment domain resource template fields used by the JRF path through the sample
  sed -i -e "s/\#\(secrets\):/\1:/" $DOMAIN_RESOURCE_FILE
  sed -i -e "s/\#\(opss\):/\1:/" $DOMAIN_RESOURCE_FILE
  sed -i -e "s/\#\(walletPasswordSecret\):/\1:/" $DOMAIN_RESOURCE_FILE
  sed -i -e "s/\#\(introspectorJobActiveDeadlineSeconds\):/\1:/" $DOMAIN_RESOURCE_FILE
fi
