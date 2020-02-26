#!/bin/bash
# Copyright 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Exit immediately if a command exits with a non-zero status.
#set -e

# Get the absolute path of this file's folder
WIT_SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
cd ${WIT_SCRIPT_DIR}

JDK_WLS_INSTALLER_DIR="/scratch/artifacts/imagetool"

# Get user info from a file if it exists. Use this info as default values
if [ -f ${WIT_SCRIPT_DIR}/user_info.properties ] ; then
  echo @@
  echo "@@ Get user info from ${WIT_SCRIPT_DIR}/user_info.properties"
  echo @@

  source ${WIT_SCRIPT_DIR}/user_info.properties
  USER_NAME=${USER_NAME}
  USER_PASSWORD=${USER_PASSWORD}
fi

# Get user info from env vars
ORACLE_SUPPORT_USERNAME=${ORACLE_SUPPORT_USERNAME-$USER_NAME}
ORACLE_SUPPORT_PASSWORD=${ORACLE_SUPPORT_PASSWORD-$USER_PASSWORD}

# This step downloads the latest version or specified-version of WebLogic Image Tool in a specified directory
# If this is run behind a proxy, then environment variables http_proxy and https_proxy must be set.

sh ${WIT_SCRIPT_DIR}/build_download.sh $@

# In case Weblogic and JDK installers don't exist on Jenkins, use oners in a local box

if [ ! -d ${JDK_WLS_INSTALLER_DIR} ] || [ -d "ls -A ${JDK_WLS_INSTALLER_DIR}" ]; then
  JDK_WLS_INSTALLER_DIR="/net/slc12enr.us.oracle.com/scratch/artifacts/imagetool"
fi

export JDK_WLS_INSTALLER_DIR=${JDK_WLS_INSTALLER_DIR}

echo @@
echo "@@ Installers are located at ${JDK_WLS_INSTALLER_DIR}"
echo @@

echo @@
echo "@@ ls  -l ${JDK_WLS_INSTALLER_DIR}"
echo @@

ls -l ${JDK_WLS_INSTALLER_DIR}

# This step builds a docker image (WebLogic Installer with patches) using the WebLogic Image Tool

if [ ! -z "${ORACLE_SUPPORT_USERNAME}" ] && [ ! -z "${ORACLE_SUPPORT_PASSWORD}" ]; then
  echo @@
  echo "@@ Creating WebLogic docker image with full internet access"
  echo @@
  
  export ORACLE_SUPPORT_USERNAME=${ORACLE_SUPPORT_USERNAME}
  export ORACLE_SUPPORT_PASSWORD=${ORACLE_SUPPORT_PASSWORD}
  
  sh ${WIT_SCRIPT_DIR}/create_image_base_w_internet.sh
else
  echo @@
  echo "@@ Creating WebLogic docker image without internet access"
  echo @@
  
  sh ${WIT_SCRIPT_DIR}/create_image_base_no_internet.sh
fi
