#!/bin/bash
# Copyright 2020, Oracle Corporation and/or its affiliates. 
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
#  This script leverages the WebLogic image tool to build a WebLogic docker image with patches and
#  with full internet access.  The version is 12.2.1.3.0 with PS4 and interim patch 2915930
#  
#  Assumptions:
#    build_download.sh should be run first
#    The $WIT_HOME_DIR working directory must have at least 10g of space.
#
#  Expects the following installers to already be installed:
#    jdk-8u202-linux-x64.tar.gz to /scratch/artifacts/imagetool
#    fmw_12.2.1.3.0_wls_Disk1_1of1.zip to /scratch/artifacts/imagetool
#
#  Expects the following env vars to already be set:
#    ORACLE_SUPPORT_USERNAME - user id who is entitled to download patches from Oracle Support.
#    ORACLE_SUPPORT_PASSWORD - the credential for ORACLE_SUPPORT_USERNAME
#
#  If this is run behind a proxy. Environment varibale http_proxy and https_proxy must be set
#

# Exit immediately if a command exits with a non-zero status.
set -e

userinfo_usage()
{
  printf "\n"
  echo 1>&2 "username or password is not set. There are two ways to set it"
  echo 1>&2 "1. set up env var ORACLE_SUPPORT_USERNAME and ORACLE_SUPPORT_PASSWORD"
  echo 1>&2 "2. Create a file ${WIT_SCRIPT_DIR}/user_info.properties"
  echo 1>&2 "And add two lines below"
  echo 1>&2 "USER_NAME=username@mycompany.com"
  echo 1>&2 "USER_PASSWORD=MYPWD"
  printf "\n"
}

checkCondition()
{
  if [ ! -f "${JDK_WLS_INSTALLER_DIR}/${JDK_INSTALLER_NAME}" ] ||
     [ ! -f "${JDK_WLS_INSTALLER_DIR}/${WLS_INSTALLER_NAME}" ]; then
    echo @@
    echo "@@ JDK or WLS installer is not installed. Please install tnhem to ${JDK_WLS_INSTALLER_DIR} first! "
    echo @@

    exit 0
  fi

  if [ -z "${ORACLE_SUPPORT_USERNAME}" ] || [ -z "${ORACLE_SUPPORT_PASSWORD}" ]; then
    userinfo_usage
    exit 0
  fi

  if [ ! -d ${WIT_HOME_DIR} ] ; then
    echo @@
    echo "@@ imagetool is not installed. Please run build_downlosd.sh to install it! "
    echo @@

    exit 0
  fi
}

cleanup()
{
  if [ -d ${WLSIMG_DIR} ] ; then
    echo @@
    echo "@@ Cleanup WIT cache Entry and old WLS docker image"
    echo @@

    # Clean WIT cache
    ${WIT_HOME_DIR}/bin/imagetool cache deleteEntry --key wls_${WLS_IMAGE_VERSION}
    ${WIT_HOME_DIR}/bin/imagetool cache deleteEntry --key jdk_8u202
    ${WIT_HOME_DIR}/bin/imagetool cache deleteEntry --key 28186730_opatch
    ${WIT_HOME_DIR}/bin/imagetool cache deleteEntry --key 29135930_12.2.1.3.191004
    ${WIT_HOME_DIR}/bin/imagetool cache deleteEntry --key 30386660_12.2.1.3.0
    ${WIT_HOME_DIR}/bin/imagetool cache deleteEntry --key 30689820_12.2.1.4.0
  fi

  if [ -d ${WLSIMG_CACHEDIR} ] ; then
    echo @@
    echo "@@ rm -rf ${WLSIMG_CACHEDIR}"
    rm -rf ${WLSIMG_CACHEDIR}
  fi

  if [ -d ${WLSIMG_BLDDIR} ] ; then
    echo @@
    echo "@@ rm -rf ${WLSIMG_BLDDIR}"
    rm -rf ${WLSIMG_BLDDIR}
  fi

  if [ ! -z $(docker images -q ${WLS_IMAGE_TAG}) ]; then
    docker rmi ${WLS_IMAGE_TAG}
    rm -rf ~/wlsimgbuilder_temp*
  fi
}

prepare()
{
  if [ ! -d ${WLSIMG_CACHEDIR} ] ; then
    echo @@
    echo "@@ mkdir ${WLSIMG_CACHEDIR}"
    mkdir -p ${WLSIMG_CACHEDIR}
  fi

  if [ ! -d ${WLSIMG_BLDDIR} ] ; then
    echo @@
    echo "@@ mkdir ${WLSIMG_BLDDIR}"
    mkdir -p ${WLSIMG_BLDDIR}
  fi
}

setupCache()
{
  add_jdk_installer="${WIT_HOME_DIR}/bin/imagetool cache addInstaller --type jdk --version ${JDK_INSTALLER_VERSION} --path ${JDK_WLS_INSTALLER_DIR}/${JDK_INSTALLER_NAME}"
  add_wls_installer="${WIT_HOME_DIR}/bin/imagetool cache addInstaller --type wls --version ${WLS_IMAGE_VERSION} --path ${JDK_WLS_INSTALLER_DIR}/${WLS_INSTALLER_NAME}"

  echo "@@ Add installers to WIT cache"
  echo "@@ ${add_jdk_installer}"
  echo "@@ ${add_wls_installer}"
  echo "@@ ${WIT_HOME_DIR}/bin/imagetool cache listItems"
  echo @@

  # Add installers to WIT cache
  ${add_jdk_installer}
  ${add_wls_installer}
  ${WIT_HOME_DIR}/bin/imagetool cache listItems
}
createImage()
{
  create_wls_image="${WIT_HOME_DIR}/bin/imagetool create --tag ${WLS_IMAGE_TAG} --latestPSU --version ${WLS_IMAGE_VERSION} --httpProxyUrl ${http_proxy} --httpsProxyUrl ${https_proxy} --user ${ORACLE_SUPPORT_USERNAME} --password ${ORACLE_SUPPORT_PASSWORD}"

  echo @@
  echo "@@ Create WLS Docker image"
  echo "@@ ${create_wls_image}"
  echo "@@ using ${WLS_INSTALLER_NAME}"
  echo @@
  
  ${create_wls_image}

  if [ $? -eq 0 ]; then
    echo @@
    echo "@@ WebLogic docker image: ${WLS_IMAGE_TAG} created successfully!"
    echo @@
  else
    echo @@
    echo "@@ Failed to create WebLogic docker image"
    echo @@
  fi
}

#### Main
WIT_SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
cd ${WIT_SCRIPT_DIR}

# Set up WIT cache env var
source ${WIT_SCRIPT_DIR}/build_image_init.sh
export WLSIMG_CACHEDIR=${WLSIMG_CACHEDIR}
export WLSIMG_BLDDIR=${WLSIMG_BLDDIR}

checkCondition
cleanup
prepare
setupCache
createImage
