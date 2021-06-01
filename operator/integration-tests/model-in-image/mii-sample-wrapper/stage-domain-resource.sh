#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is an example of how to configure a model-in-image domain resource.
#
# This script creates 'WORKDIR/DOMAIN_RESOURCE_FILENAME'
# from template './DOMAIN_RESOURCE_TEMPLATE'.
#
# Optional environment variables (see ./README for details):
#
#   WORKDIR, CUSTOM_DOMAIN_NAME, DOMAIN_UID, DOMAIN_NAMESPACE
#   MODEL_IMAGE_NAME, MODEL_IMAGE_TAG, IMAGE_PULL_SECRET_NAME
#   WDT_DOMAIN_TYPE
#   INCLUDE_MODEL_CONFIGMAP
#   DOMAIN_RESOURCE_FILENAME, DOMAIN_RESOURCE_TEMPLATE
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

for var in DOMAIN_UID \
           DOMAIN_NAMESPACE \
           CUSTOM_DOMAIN_NAME \
           WDT_DOMAIN_TYPE \
           IMAGE_PULL_SECRET_NAME \
           MODEL_IMAGE_NAME \
           MODEL_IMAGE_TAG \
           INCLUDE_MODEL_CONFIGMAP \
           DOMAIN_RESOURCE_FILENAME \
           DOMAIN_RESOURCE_TEMPLATE \
           INTROSPECTOR_DEADLINE_SECONDS \
           BASE_IMAGE_NAME \
           BASE_IMAGE_TAG \
           COMMON_MOUNT_PATH \
           WDT_MODEL_HOME \
           WDT_INSTALL_HOME
do
  echo "@@ Info: ${var}=${!var}"
done

mkdir -p $(dirname $WORKDIR/$DOMAIN_RESOURCE_FILENAME)

echo "@@"
echo "@@ Info: Creating domain resource file 'WORKDIR/DOMAIN_RESOURCE_FILENAME' from 'SCRIPTDIR/DOMAIN_RESOURCE_TEMPLATE'"
echo "@@"

cp "$SCRIPTDIR/$DOMAIN_RESOURCE_TEMPLATE" "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"

if [ ! -z "${IMAGE_PULL_SECRET_NAME}" ]; then
  sed -i -e "s;\#\(imagePullSecrets:\);\1;"        "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"
  sed -i -e "s;\#\(-.*IMAGE_PULL_SECRET_NAME\);\1;" "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"
fi

IMAGE_PULL_SECRET_NAME="${IMAGE_PULL_SECRET_NAME:-regsecret}"

for template_var in WDT_DOMAIN_TYPE \
                    CUSTOM_DOMAIN_NAME \
                    DOMAIN_UID \
                    DOMAIN_NAMESPACE \
                    IMAGE_PULL_SECRET_NAME \
                    MODEL_IMAGE_NAME \
                    MODEL_IMAGE_TAG \
                    INTROSPECTOR_DEADLINE_SECONDS \
                    BASE_IMAGE_NAME \
                    BASE_IMAGE_TAG \
                    COMMON_MOUNT_PATH \
                    WDT_MODEL_HOME \
                    WDT_INSTALL_HOME
do
  sed -i -e "s;@@${template_var}@@;${!template_var};" "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"
done


if [ "${INCLUDE_MODEL_CONFIGMAP}" = "true" ]; then
  # we're going to deploy and use the model.configuration.configMap, and this
  # configmap depends on the datasource-secret.
  sed -i -e "s;\#\(configMap:\);\1;"           "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"
  sed -i -e "s;\#\(secrets:\);\1;"             "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"
  sed -i -e "s;\#\(-.*datasource-secret\);\1;" "$WORKDIR/$DOMAIN_RESOURCE_FILENAME"
fi

echo "@@ Info: Done."
