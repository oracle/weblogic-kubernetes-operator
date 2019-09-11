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
#      WebLogic Deploy Tool is downloaded to the current directory and named weblogic-deploy.zip
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

CURRENT_DIR=`pwd`

mkdir -p cache
# rm -f cache/.metadata
unzip -o ${SERVER_JRE}
unzip -o weblogic-image-tool.zip

SERVER_JRE_VERSION=${SERVER_JRE_VERSION:-8u221}
SERVER_JRE_TGZ=${SERVER_JRE_TGZ:-server-jre-${SERVER_JRE_VERSION}-linux-x64.tar.gz}

#
echo Setting up imagetool and populating its caches
#

IMGTOOL_BIN=${CURRENT_DIR}/imagetool/bin/imagetool.sh

export WLSIMG_CACHEDIR=${CURRENT_DIR}/cache
export WLSIMG_BLDDIR=${CURRENT_DIR}

${IMGTOOL_BIN} cache addInstaller \
  --type jdk --version ${SERVER_JRE_VERSION} --path ${CURRENT_DIR}/${SERVER_JRE_TGZ}

if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
  ${IMGTOOL_BIN} cache addInstaller \
    --type wls --version 12.2.1.3.0 --path ${CURRENT_DIR}/${WLS_INSTALLER}
  IMGTYPE=wls
else 
  ${IMGTOOL_BIN} cache addInstaller \
    --type fmw --version 12.2.1.3.0 --path ${CURRENT_DIR}/${FMW_INSTALLER}
  IMGTYPE=fmw
fi

${IMGTOOL_BIN} cache addInstaller \
  --type wdt --version latest --path ${CURRENT_DIR}/weblogic-deploy.zip

#
echo Creating base image with patches
#

(
set +x

echo -n "Oracle Support Username: "
read USERID
echo -n "Oracle Support Password: "
read -s USERPWD

${IMGTOOL_BIN} create --tag model-in-image:x0 \
  --user ${USERID} \
  --password ${USERPWD} \
  --patches 29135930_12.2.1.3.190416,29016089 \
  --jdkVersion ${SERVER_JRE_VERSION} \
  --type ${IMGTYPE}

)
