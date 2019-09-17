#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#  This script leverages the WebLogic image tool to build a WebLogic docker image with patches.  The 
#  version is 12.2.1.3.0 with PS4 and interim patch 2915930
#  
#  Assumptions:
#    This script should be called by build.sh.  
#
#    The current directory is the working directory and must have at least 10g of space.
#
#    The script expects:
#
#      WebLogic Image Tool is downloaded to the current directory and named weblogic-image-tool.zip
#      WebLogic Deploy Tool is downloaded to the current directory and named weblogic-deploy-tooling.zip
#
#    Environment used:
#
#      WLS_INSTALLER    - Name of the WebLogic Installer zip file in the current directory
#      FMW_INSTALLER    - Name of the FMW Installer zip file in the current directory
#      SERVER_JRE       - Name of the zipped Server JRE (note: it will be unzipped locally)
#      WDT_DOMAIN_TYPE  - WebLogic Domain Type to be built : RestrictedJRF, JRF, WLS
#
#    This script prompts for:
#      
#      Oracle Support Username
#      Oracle Support Password
#

set -eu

cd ${WORKDIR}

BASE_IMAGE_REPO=${BASE_IMAGE_REPO:-model-in-image}
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-x0}
BASE_IMAGE_BUILD=${BASE_IMAGE_BUILD:-when-missing}

SERVER_JRE=${SERVER_JRE:-V982783-01.zip}
SERVER_JRE_VERSION=${SERVER_JRE_VERSION:-8u221}
SERVER_JRE_TGZ=${SERVER_JRE_TGZ:-server-jre-${SERVER_JRE_VERSION}-linux-x64.tar.gz}

WLS_INSTALLER=${WLS_INSTALLER:-V886423-01.zip}
FMW_INSTALLER=${FMW_INSTALLER:-V886426-01.zip}
WLS_VERSION=${WLS_VERSION:-12.2.1.3.0}
REQUIRED_PATCHES=${REQUIRED_PATCHES:-29135930_12.2.1.3.190416,29016089}

echo @@
echo "@@ Info: Starting base image build for '$BASE_IMAGE_REPO:$BASE_IMAGE_TAG'"
echo @@

if [ ! "$BASE_IMAGE_BUILD" = "always" ] && \
   [ "`docker images $BASE_IMAGE_REPO:$BASE_IMAGE_TAG | awk '{ print $1 ":" $2 }' | grep -c $BASE_IMAGE_REPO:$BASE_IMAGE_TAG`" = "1" ]; then
  echo @@
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!! Skipping base image build because image '$BASE_IMAGE_REPO:$BASE_IMAGE_TAG' already exists."
  echo "@@ Info:         To always build the base image, 'export  BASE_IMAGE_BUILD=always'.                        "
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo @@
  sleep 3
  exit 0
fi

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit
fi

if [ ! -d "${WORKDIR}" ] ; then
  echo "@@ Error: Directory WORKDIR='${WORKDIR}' does not exist." && exit
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
# rm -f cache/.metadata
unzip -o ${SERVER_JRE}
unzip -o weblogic-image-tool.zip

#
echo @@ Setting up imagetool and populating its caches
#

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

#
echo @@ Creating base image with patches
#

(
set +x

if [ -z "${USERID:-}" ]; then
  echo -n "Enter an Oracle Support Username: "
  read USERID
  echo
fi

if [ -z "${USERPWD:-}" ]; then
  echo -n "Enter Oracle Support Password for Username '$USERID': "
  read -s USERPWD
  echo
fi

${IMGTOOL_BIN} create \
  --tag $BASE_IMAGE_REPO:$BASE_IMAGE_TAG \
  --user ${USERID} \
  --password ${USERPWD} \
  --patches ${REQUIRED_PATCHES} \
  --jdkVersion ${SERVER_JRE_VERSION} \
  --type ${IMGTYPE}

)
