#!/bin/bash
# Copyright 2020, Oracle Corporation and/or its affiliates. 
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This script sets up the image tool and the image tool cache for use
# by the scripts that build the base image.
# It also sets up env vars that are used by these scripts.
#
# It's called from 'build_download.sh' and 'build_image_base.sh'
#

# Exit immediately if a command exits with a non-zero status.
set -e  

# Get the absolute path of this file's folder
WIT_SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
cd ${WIT_SCRIPT_DIR}

# vars for downloading WIT
IMAGE_TOOL_VERSION=${IMAGE_TOOL_VERSION}
WIT_INSTALL_DIR="/scratch/`whoami`/imagetool_dir"
WIT_HOME_DIR="${WIT_INSTALL_DIR}/imagetool"
ZIP_FILE=imagetool.zip

# vars for creating a WebLogic docker image
JDK_INSTALLER_NAME=${JDK_INSTALLER_NAME:-jdk-8u202-linux-x64.tar.gz}
JDK_INSTALLER_VERSION=${JDK_INSTALLER_VERSION:-8u202}
WLS_INSTALLER_NAME=${WLS_INSTALLER_NAME:-fmw_12.2.1.3.0_wls_Disk1_1of1.zip}
WLS_IMAGE_VERSION=${IMAGE_TAG_WEBLOGIC_WIT:-12.2.1.3.0}
DEFAULT_WLS_IMAGE_NAME="itimagetool-build-wls-image"
if  [[  ${SHARED_CLUSTER} = "true"  ]]; then
    DEFAULT_WLS_IMAGE_NAME="${REPO_REGISTRY}/weblogick8s/${DEFAULT_WLS_IMAGE_NAME}"
fi
WLS_IMAGE_NAME=${IMAGE_NAME_WEBLOGIC_WIT:-$DEFAULT_WLS_IMAGE_NAME}
WLS_IMAGE_TAG=$WLS_IMAGE_NAME:$WLS_IMAGE_VERSION
WLSIMG_DIR="/scratch/`whoami`/imagetool_lib"
DEFAULT_WLSIMG_CACHEDIR="${WLSIMG_DIR}/wit_cachedir"
WLSIMG_CACHEDIR=${WLSIMG_CACHEDIR:-$DEFAULT_WLSIMG_CACHEDIR}
DEFAULT_WLSIMG_BLDDIR="${WLSIMG_DIR}/wit_blddir"
WLSIMG_BLDDIR=${WLSIMG_BLDDIR:-$DEFAULT_WLSIMG_BLDDIR}

case ${WLS_IMAGE_VERSION} in
  "12.2.1.3.0")
    PATCH_LIST="29135930_12.2.1.3.191004,30386660_12.2.1.3.0"
  ;;
  "12.2.1.4.0")
    PATCH_LIST="30689820_12.2.1.4.0"
    WLS_INSTALLER_NAME="fmw_12.2.1.4.0_wls_Disk1_1of1.zip"
  ;;
  *)
esac
