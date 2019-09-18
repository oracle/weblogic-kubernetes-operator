#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#  This script build a sample docker image for deploying to the Kubernetes cluster with the model in image
#  artifacts. It is based on a base image built earlier with build_base_image.sh
#  
#  Assumption:
#    This script should be called by build.sh.  
#
#  Environment variables used:
#    WORKDIR - working directory for the sample 

set -eu

CWD=`pwd`
cd ${WORKDIR}
export WLSIMG_CACHEDIR=${WORKDIR}/cache
export WLSIMG_BLDDIR=${WORKDIR}

IMGTOOL_BIN=${WORKDIR}/imagetool/bin/imagetool.sh

BASE_IMAGE_REPO=${BASE_IMAGE_REPO:-model-in-image}
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-x0}

MODEL_IMAGE_REPO=${MODEL_IMAGE_REPO:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-x1}
MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-when-missing}

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

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then
  echo @@
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit
  echo @@
fi

echo "@@"
echo "@@ Info: Setting up wdt models in directory ./models"
echo "@@"

if [ "${WDT_DOMAIN_TYPE}" == "WLS" -o "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] ; then
  cp ${CWD}/model1.yaml.wls models/model1.yaml
fi

if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  cp ${CWD}/model1.yaml.jrf models/model1.yaml
fi

cp ${CWD}/model1.10.properties models/model1.10.properties

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
