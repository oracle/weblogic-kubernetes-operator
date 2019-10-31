#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This script sets up the image tool and the image tool cache for use
# by the scripts that build the base image and the final model image.
# It also sets up env vars that are used by these scripts.
#
# It's called from 'build_image_base.sh' and 'build_image_model.sh'.
#
# The script expects files:
#
#   WebLogic Image Tool is downloaded to the current directory and named weblogic-image-tool.zip
#   WebLogic Deploy Tool is downloaded to the current directory and named weblogic-deploy-tooling.zip
#   
# Expects the following env vars to already be set:
#
#   WORKDIR - working directory for the sample with at least 10g of space
#   WDT_DOMAIN_TYPE - WLS, RestrictedJRF, or JRF
#

set -eu

cd ${WORKDIR}

if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
  IMGTYPE="wls"
  BASE_IMAGE_REPO="container-registry.oracle.com/middleware/weblogic"
else
  BASE_IMAGE_REPO="container-registry.oracle.com/middleware/fmw-infrastructure"
  IMGTYPE="fmw"
fi


BASE_IMAGE_BUILD=${BASE_IMAGE_BUILD:-when-missing}
BASE_IMAGE_REPO=${BASE_IMAGE_REPO:-model-in-image}
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.3}

MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-when-missing}
MODEL_IMAGE_REPO=${MODEL_IMAGE_REPO:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}


echo @@
echo @@ Info: Setting up imagetool and populating its caches
echo @@

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit
fi


mkdir -p cache
unzip -o weblogic-image-tool.zip

IMGTOOL_BIN=${WORKDIR}/imagetool/bin/imagetool.sh

export WLSIMG_CACHEDIR=${WORKDIR}/cache
export WLSIMG_BLDDIR=${WORKDIR}

function updateImageToolCache() {
  ${IMGTOOL_BIN} cache deleteEntry --key $1_$2
  ${IMGTOOL_BIN} cache addInstaller --type $1 --version $2 --path $3
}

updateImageToolCache wdt latest ${WORKDIR}/weblogic-deploy-tooling.zip

