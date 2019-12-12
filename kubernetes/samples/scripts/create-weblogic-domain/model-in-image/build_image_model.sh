#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#  This script uses the WebLogic Image Tool to build a docker image with model in image
#  artifacts. It is based on a base image obtained earlier with build_image_base.sh
#  
#  Assumptions:
#
#    This script should be called by build.sh.  
#    WebLogic Image Tool is downloaded to the current directory and named weblogic-image-tool.zip
#    WebLogic Deploy Tool is downloaded to the current directory and named weblogic-deploy-tooling.zip
#    Model files have been populated into the "./models" directory.
#
#  Required environment variables:
#
#    WORKDIR - working directory for the sample with at least 10g of space
#
#  Optional environment variables:
#
#    WDT_DOMAIN_TYPE, BASE_IMAGE_NAME, BASE_IMAGE_TAG, 
#    MODEL_IMAGE_NAME, MODEL_IMAGE_TYPE, MODEL_IMAGE_BUILD:
#
#      See build_image_init.sh for a description.
#

set -eu

cd ${WORKDIR}

source build_image_init.sh

if [ ! "$MODEL_IMAGE_BUILD" = "always" ] && \
   [ "`docker images $MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG | awk '{ print $1 ":" $2 }' | grep -c $MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG`" = "1" ]; then
  echo @@
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!!                                                                                           "
  echo "@@ Info:   Skipping model image build because image '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG' already exists.   "
  echo "@@ Info:   To always build the model image, 'export MODEL_IMAGE_BUILD=always'.                             "
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo @@
  sleep 3
  exit 0
fi

echo @@
echo @@ Info: Setting up imagetool and populating its caches
echo @@

mkdir -p cache
unzip -o weblogic-image-tool.zip

IMGTOOL_BIN=${WORKDIR}/imagetool/bin/imagetool.sh

# The image tool uses the WLSIMG_CACHEDIR and WLSIMG_BLDIR env vars:
export WLSIMG_CACHEDIR=${WORKDIR}/cache
export WLSIMG_BLDDIR=${WORKDIR}

${IMGTOOL_BIN} cache deleteEntry --key wdt_latest
${IMGTOOL_BIN} cache addInstaller \
  --type wdt --version latest --path ${WORKDIR}/weblogic-deploy-tooling.zip

echo "@@"
echo "@@ Info: Starting model image build for '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG'"
echo "@@"

#
# Run the image tool to create the image. It will implicitly use the latest WDT binaries
# in the local image tool cache marked with key 'wdt_latest' (see 'cache' commands above). To
# use a different version of WDT, specify '--wdtVersion'.
#

set -x
${IMGTOOL_BIN} update \
  --tag $MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG \
  --fromImage $BASE_IMAGE_NAME:$BASE_IMAGE_TAG \
  --wdtModel models/model1.yaml \
  --wdtVariables models/model1.10.properties \
  --wdtArchive models/archive1.zip \
  --wdtModelOnly \
  --wdtDomainType ${WDT_DOMAIN_TYPE}
