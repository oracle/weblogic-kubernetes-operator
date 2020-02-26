#!/bin/bash
# Copyright 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This script downloads the WebLogic Image Tool to env.RESULT_ROOT.
# If env.RESULT_ROOT is not set, the script will create RESULT_ROOT dir
#
# For the imagetool version to be downloaded, there are two cases
#
# 1. One can install imagetool with a desired version number
#    by specifying the version number in commandline
#    or via env variable IMAGE_TOOL_VERSION
# 2. The latest imagetool version will be installed if no version number
#    specified
#
# If this is run behind a proxy.  Environment varibale http_proxy and https_proxy must be set
#

# Exit immediately if a command exits with a non-zero status.
set -e

usage()
{
  printf "\n"
  echo 1>&2 "1. To install imagetool with the version number specified by env.IMAGE_TOOL_VERSION"
  echo 1>&2 "Usage: export IMAGE_TOOL_VERSION=1.8.1; $0"
  echo 1>&2 "Or on Jenkins, set the version number in IMAGE_TOOL_VERSION field"
  echo 1>&2 "2. To install imagetool with a specified version number"
  echo 1>&2 "Usage: $0 -v <imagetool version number>"
  echo 1>&2 "Example: $0 -v 1.8.1"
  echo 1>&2 "3. To install latest imagetool version"
  echo 1>&2 "Usage: $0"
  printf "\n"
}

get_param()
{
  case $1 in
    -h | --help ) usage
      exit 0
      ;;
    -v | -version ) IMAGE_TOOL_VERSION=$2
      ;;
    *) usage
      exit 0
      ;;
  esac
}

function cleanZipDir() {
  echo "@@ Deleting ${WIT_INSTALL_DIR}"
  rm -rf ${WIT_INSTALL_DIR}
}

getLatestWITVersion() {
  latestWITVersion=$(curl -sL https://github.com/oracle/weblogic-image-tool/releases/latest | grep "/oracle/weblogic-image-tool/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1 | cut -d'/' -f6 | cut -d'-' -f2)
}

checkInstalledWIT() {
  if [ -d ${WIT_INSTALL_DIR} ] ; then
    installedWITVersion=$(sh ${WIT_HOME_DIR}/bin/imagetool.sh -V | grep "imagetool:" | cut -d':' -f2)
  fi

  if [ "$IMAGE_TOOL_VERSION" = "$installedWITVersion" ] && [ -f $WIT_INSTALL_DIR/$ZIP_FILE ] ; then
    echo @@
    echo "@@ Skipping imagetool download because version <${installedWITVersion}> already exists in ${WIT_INSTALL_DIR}"
    echo @@

    exit 0
  fi
}

downloadImagetoolZip() {
  if [ ! -z "$installedWITVersion" ] && [ "$IMAGE_TOOL_VERSION" != "$installedWITVersion" ] && [ -f $WIT_INSTALL_DIR/$ZIP_FILE ] ; then
    echo @@
    echo "@@ Installed imagetool version <$installedWITVersion> is different from required version <${IMAGE_TOOL_VERSION}>"
    echo "@@ Delete installed version <$installedWITVersion>"
    echo @@

    cleanZipDir
  fi

  echo @@
  echo "@@ Latest imagetool version is : <${latestWITVersion}>"
  echo "@@ Request to install imagetool version : <${IMAGE_TOOL_VERSION}>"
  echo @@
  echo "@@ Downloading the imagetool.zip "
  echo "@@ from https://github.com/oracle/weblogic-image-tool/releases/download/release-${IMAGE_TOOL_VERSION}/${ZIP_FILE}"
  echo "@@ to ${WIT_INSTALL_DIR}"
  echo @@

  wget -P ${WIT_INSTALL_DIR} \
    https://github.com/oracle/weblogic-image-tool/releases/download/release-${IMAGE_TOOL_VERSION}/${ZIP_FILE}
}

unzipImagetool() {
  if [ -d ${WIT_HOME_DIR} ] ; then
    rm -rf ${WIT_HOME_DIR}
  fi

  if [ -f $WIT_INSTALL_DIR/$ZIP_FILE ] ; then
    echo @@
    echo "@@ unzip ${WIT_INSTALL_DIR}/${ZIP_FILE} to ${WIT_INSTALL_DIR}"
    echo @@
    unzip ${WIT_INSTALL_DIR}/${ZIP_FILE}  -d ${WIT_INSTALL_DIR}
  fi

  case "$OSTYPE" in
    linux* )
      echo "@@ Installed imagetool in LINUX"
      ln -s ${WIT_HOME_DIR}/bin/imagetool.sh ${WIT_HOME_DIR}/bin/imagetool
      ;;
    msys* )
      echo "@@ Installed imagetool on WINDOWS"
      ln -s ${WIT_HOME_DIR}/bin/imagetool.cmd ${WIT_HOME_DIR}/bin/imagetool
      ;;
    * )
      echo "@@ unknown: $OSTYPE"
      exit 0
      ;;
  esac
}

#### Main
WIT_SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
cd ${WIT_SCRIPT_DIR}

source ${WIT_SCRIPT_DIR}/build_image_init.sh

if [ "$#" != 0 ] ; then
  get_param $@
fi

getLatestWITVersion
IMAGE_TOOL_VERSION=${IMAGE_TOOL_VERSION:-$latestWITVersion}

if [ ! -d ${WIT_INSTALL_DIR} ] ; then
  echo @@
  echo "@@ mkdir ${WIT_INSTALL_DIR}"
  mkdir -p ${WIT_INSTALL_DIR}
else
  checkInstalledWIT
fi

downloadImagetoolZip
unzipImagetool
