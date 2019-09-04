#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

#
# Usage: build.sh <working directory> <oracle support id> <oracle support id password> <domain type:WLS|RestrictedJRF|JRF>
#
set -e
usage() {
    echo "build.sh <working directory> <oracle support id> <oracle support id password> <domain type:WLS|RestrictedJRF|JRF>"
}
if [ "$#" != 4 ] ; then
    usage && exit
fi

export WORKDIR=$1
export USERID=$2
export USERPWD=$3
export DOMAINTYPE=$4
export SERVER_JRE=V982783-01.zip
export WLS_INSTALLER=V886423-01.zip
export FMW_INSTALLER=V886426-01.zip

if [ ! "${DOMAINTYPE}" == "WLS" ] && [ ! "${DOMAINTYPE}" == "RestrictedJRF" ] && [ ! "${DOMAINTYPE}" == "JRF"]; then  echo "Invalid domain type: WLS or
FMW"; fi

if [ ! -d "${WORKDIR}" ] ; then
 echo "Directory WORKDIR does not exists." && exit 
fi

if [ -f "${SERVER_JRE}" ] ; then
 echo "Directory ${WORKDIR} does not contain ${SERVER_JRE}." && exit 
fi

if [ -f "${WLS_INSTALLER}" ] && [ "${DOMAINTYPE}" == "WLS" ] ; then
 echo "Directory ${WORKDIR} does not contain ${WLS_INSTALLER}." && exit 
fi

if [ -f "${FMW_INSTALLER}" ] && [ "${DOMAINTYPE}" == "RestrictedJRF" -o "${DOMAINTYPE}" == "JRF" ] ; then
 echo "Directory ${WORKDIR} does not contain ${FMW_INSTALLER}." && exit 
fi

#
#
cp -R * ${WORKDIR}
cd ${WORKDIR}
#

#  This step downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
#  If this is run behind a proxy.  Environment varibale http_proxy and https_proxy must be set
#
./download_tools.sh

# This step builds a base image (WebLogic Installer with patches) for deploying to the Kubernetes cluster using the WebLogic Image Tool
# If you are using your own image, you can skip this step
#
./build_baseimage.sh

# # unzip weblogic-image-tool.zip
# # #
# # echo Setting up imagetool
# #
# IMGTOOL_BIN=${WORKDIR}/imagetool-*/bin/imagetool.sh
# #source ${WORKDIR}/imagetool-*/bin/setup.sh
# #
# mkdir cache
# export WLSIMG_CACHEDIR=`pwd`/cache
# export WLSIMG_BLDDIR=`pwd`
# #
# unzip ${SERVER_JRE}.zip

# ${IMGTOOL_BIN} cache addInstaller --type jdk --version 8u221 --path `pwd`/server-jre-8u221-linux-x64.tar.gz
# if [ "${DOMAINTYPE}" == "WLS" ] ; then
#     ${IMGTOOL_BIN} cache addInstaller --type wls --version 12.2.1.3.0 --path `pwd`/${WLS_INSTALLER}
#     IMGTYPE=wls
# else 
#     ${IMGTOOL_BIN} cache addInstaller --type fmw --version 12.2.1.3.0 --path `pwd`/${FMW_INSTALLER}
#     IMGTYPE=fmw
# fi
# ${IMGTOOL_BIN} cache addInstaller --type wdt --version latest --path `pwd`/weblogic-deploy.zip
# #
# echo Creating base image with patches
# #
# ${IMGTOOL_BIN} create --tag model-in-image:x0 --user ${USERID} --password ${USERPWD} --patches 29135930_12.2.1.3.190416,29016089 --jdkVersion 8u221 --type ${IMGTYPE}
#


# Building sample app ear file
#
./build_app.sh
#

if [ "${DOMAINTYPE}" == "JRF" ] ; then
    cp image/model1.yaml.jrf image/model1.yaml
fi

# Build sample image for deploying to the Kubernetes Cluster

./build_sample_image.sh

# echo Creating deploy image with wdt models
# #
# cd image
# ${IMGTOOL_BIN} update --tag model-in-image:x1 --fromImage model-in-image:x0 --wdtModel model1.yaml --wdtVariables model1.10.properties --wdtArchive archive1.zip --wdtModelOnly --wdtDomainType ${DOMAINTYPE}
# cd ..

echo Setting Domain Type in domain.yaml
#
sed -i s/@@DOMTYPE@@/${DOMAINTYPE}/ domain.yaml








