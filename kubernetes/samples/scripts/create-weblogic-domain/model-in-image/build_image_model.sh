#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#  This script builds a sample docker image for deploying to the Kubernetes cluster with 
#  model in image artifacts. It is based on a base image built earlier with build_image_base.sh
#  
#  Assumption:
#    This script should be called by build.sh.  
#
#  Required environment variables:
#    WORKDIR - working directory for the sample with at least 10g of space
#    WDT_DOMAIN_TYPE - WLS, RestrictedJRF, or JRF
#

set -eu

cd ${WORKDIR}

source build_image_init.sh

echo "@@"
echo "@@ Info: Starting model image build for '$MODEL_IMAGE_REPO:$MODEL_IMAGE_TAG'"
echo "@@"

if [ ! "$MODEL_IMAGE_BUILD" = "always" ] && \
   [ "`docker images $MODEL_IMAGE_REPO:$MODEL_IMAGE_TAG | awk '{ print $1 ":" $2 }' | grep -c $MODEL_IMAGE_REPO:$MODEL_IMAGE_TAG`" = "1" ]; then
  echo @@
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!! Skipping model image build because image '$MODEL_IMAGE_REPO:$MODEL_IMAGE_TAG' already exists."
  echo "@@ Info:         To always build the model image, 'export  MODEL_IMAGE_BUILD=always'.                      "
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo @@
  sleep 3
  exit 0
fi

echo "@@"
echo "@@ Info: Setting up wdt models in directory ./models"
echo "@@"

if [ "${WDT_DOMAIN_TYPE}" == "WLS" -o "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] ; then
  cp model1.yaml.wls models/model1.yaml
fi

if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  cp model1.yaml.jrf models/model1.yaml
fi

cp model1.10.properties models/model1.10.properties

echo @@
echo @@ Info: Creating deploy image with wdt models
echo @@

${IMGTOOL_BIN} update \
  --tag $MODEL_IMAGE_REPO:$MODEL_IMAGE_TAG \
  --fromImage $BASE_IMAGE_REPO:$BASE_IMAGE_TAG \
  --wdtModel models/model1.yaml \
  --wdtVariables models/model1.10.properties \
  --wdtArchive models/archive1.zip \
  --wdtModelOnly \
  --wdtDomainType ${WDT_DOMAIN_TYPE}
