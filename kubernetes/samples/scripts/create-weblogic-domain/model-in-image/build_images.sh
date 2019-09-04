#!/bin/bash

# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
#  This script setup the image tool use it to build docker image for deploying to WebLogic Kubernetes Operator
#  
#  Assumption:
#    This script should be called by build.sh.  
#
#    The current directory is the working directory and must have at least 10g of space
#    The script expects
#      WebLogic Image Tool is downloaded to the current directory and named weblogic-image-tool.zip
#      Environment used:
#
#     WLS_INSTALLER -  Name of the WebLogic Installer zip file in the current directory
#     FMW_INSTALLER -  Name of the FMW Installer zip file in the current directory
#     DOMAINTYPE - WebLogic Domain Type to be built : RestrictedJRF, JRF, WLS
#     SERVER_JRE - Name of the zipped Server JRF (note: it will be unzipped locally)
# 
#    
set -e

SERVER_JRE_TGZ=${SERVER_JRE_TGZ:-server-jre-8u221-linux-x64.tar.gz}
SERVER_JRE_VERSION=${SERVER_JRE_VERSION:-8u221}
unzip weblogic-image-tool.zip
CURRENT_DIR=`pwd`
#
# echo Setting up imagetool
#
IMGTOOL_BIN=${CURRENT_DIR}/imagetool-*/bin/imagetool.sh
#
mkdir cache
export WLSIMG_CACHEDIR=`pwd`/cache
export WLSIMG_BLDDIR=`pwd`
#
unzip ${SERVER_JRE}.zip

${IMGTOOL_BIN} cache addInstaller --type jdk --version ${SERVER_JRE_VERSION} --path `pwd`/${SERVER_JRE_TGZ}
if [ "${DOMAINTYPE}" == "WLS" ] ; then
    ${IMGTOOL_BIN} cache addInstaller --type wls --version 12.2.1.3.0 --path `pwd`/${WLS_INSTALLER}
    IMGTYPE=wls
else 
    ${IMGTOOL_BIN} cache addInstaller --type fmw --version 12.2.1.3.0 --path `pwd`/${FMW_INSTALLER}
    IMGTYPE=fmw
fi
${IMGTOOL_BIN} cache addInstaller --type wdt --version latest --path `pwd`/weblogic-deploy.zip
#
echo Creating base image with patches
#
${IMGTOOL_BIN} create --tag model-in-image:x0 --user ${USERID} --password ${USERPWD} --patches 29135930_12.2.1.3.190416,29016089 --jdkVersion ${SERVER_JRE_VERSION} --type ${IMGTYPE}
