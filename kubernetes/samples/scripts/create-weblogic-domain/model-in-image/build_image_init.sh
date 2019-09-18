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

BASE_IMAGE_BUILD=${BASE_IMAGE_BUILD:-when-missing}
BASE_IMAGE_REPO=${BASE_IMAGE_REPO:-model-in-image}
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-x0}

MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-when-missing}
MODEL_IMAGE_REPO=${MODEL_IMAGE_REPO:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-x1}

SERVER_JRE=${SERVER_JRE:-V982783-01.zip}
SERVER_JRE_VERSION=${SERVER_JRE_VERSION:-8u221}
SERVER_JRE_TGZ=${SERVER_JRE_TGZ:-server-jre-${SERVER_JRE_VERSION}-linux-x64.tar.gz}

WLS_INSTALLER=${WLS_INSTALLER:-V886423-01.zip}
FMW_INSTALLER=${FMW_INSTALLER:-V886426-01.zip}
WLS_VERSION=${WLS_VERSION:-12.2.1.3.0}
REQUIRED_PATCHES=${REQUIRED_PATCHES:-29135930_12.2.1.3.190416,29016089}

echo @@
echo @@ Info: Setting up imagetool and populating its caches
echo @@

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit
fi

if [ ! -f "${SERVER_JRE}" ] ; then
  echo "@@ Error: Directory WORKDIR='${WORKDIR}' does not contain installer for SERVER_JRE '${SERVER_JRE}'." && exit
fi

if [ ! -f "${WLS_INSTALLER}" ] \
   && [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
  echo "@@ Error: Directory WORKDIR='${WORKDIR}' does not contain WLS_INSTALLER '${WLS_INSTALLER}'." && exit
fi

if [ ! -f "${FMW_INSTALLER}" ] \
   && [ "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" -o "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  echo "@@ Error: Directory WORKDIR=${WORKDIR} does not contain FMW_INSTALER '${FMW_INSTALLER}'." && exit
fi

mkdir -p cache
unzip -o ${SERVER_JRE}
unzip -o weblogic-image-tool.zip

IMGTOOL_BIN=${WORKDIR}/imagetool/bin/imagetool.sh

export WLSIMG_CACHEDIR=${WORKDIR}/cache
export WLSIMG_BLDDIR=${WORKDIR}

function updateImageToolCache() {
  ${IMGTOOL_BIN} cache deleteEntry --key $1_$2
  ${IMGTOOL_BIN} cache addInstaller --type $1 --version $2 --path $3
}

updateImageToolCache jdk ${SERVER_JRE_VERSION} ${SERVER_JRE_TGZ}
updateImageToolCache wdt latest ${WORKDIR}/weblogic-deploy-tooling.zip

if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
  updateImageToolCache wls ${WLS_VERSION} ${WLS_INSTALLER}
  IMGTYPE=wls
else 
  updateImageToolCache fmw ${WLS_VERSION} ${FMW_INSTALLER}
  IMGTYPE=fmw
fi
