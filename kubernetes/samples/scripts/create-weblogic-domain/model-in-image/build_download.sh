#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This script downloads the latest WebLogic Deploy Tool and WebLogic Image Tool 
# to the WORKDIR directory.
#
# Expects the following env vars to already be set:
#    
#    WORKDIR 
#      working directory for the sample with at least 10g of space
#
# Optional env vars:
#    http_proxy https_proxy
#      If running behind a proxy, then set as needed to allow curl access to github.com.
#
#    DOWNLOAD_WDT DOWNLOAD_WIT
#      Default to 'when-missing'. Set to 'always' to force download even
#      if local installer zip is missing.
#
#    WDT_INSTALLER_URL WIT_INSTALLER_URL
#      Defaults to 'https://github.com/oracle/weblogic-deploy-tooling/releases/latest'
#      and 'https://github.com/oracle/weblogic-image-tooling/releases/latest' respectively.
#
#      To override an installer URL, export the URL env to point to a specific zip file, for example:
#      export WDT_INSTALLER_URL=https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-1.7.0/weblogic-deploy.zip
#

set -eu

cd ${WORKDIR}

DOWNLOAD_WIT=${DOWNLOAD_WIT:-when-missing}
DOWNLOAD_WDT=${DOWNLOAD_WDT:-when-missing}
WDT_INSTALLER_URL=${WDT_INSTALLER_URL:-https://github.com/oracle/weblogic-deploy-tooling/releases/latest}
WIT_INSTALLER_URL=${WIT_INSTALLER_URL:-https://github.com/oracle/weblogic-image-tooling/releases/latest}

download_zip() {
  set -eu
  local ZIPFILE=$1
  local LOCATION=$2
  local DOWNLOAD_VAR_NAME=$3
  local DOWNLOAD=${!DOWNLOAD_VAR_NAME}

  if [ ! "$DOWNLOAD" = "always" ] && [ -f $ZIPFILE ]; then
    echo "@@"
    echo "@@ -----------------------------------------------------------------------"
    echo "@@ Info: NOTE! Skipping '$LOCATION' download since local                  "
    echo "@@             file '$ZIPFILE' already exists.                            "
    echo "@@             To force a download, 'export $DOWNLOAD_VAR_NAME=always'.   "
    echo "@@ -----------------------------------------------------------------------"
    echo "@@"
    sleep 2
    return
  fi

  echo "@@ Downloading '$LOCATION' to '$ZIPFILE' from 'https://github.com$downloadlink'."

  local iurl=$1
  if [ "`echo $iurl | grep -c '/latest$'`" = "1" ]; then
    LOCATION=$(curl -sL https://github.com/$LOCATION/releases/latest | grep "releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
    echo "@@ URL ends in 'latest' so it was converted to '$LOCATION'"
  fi

  curl -L $LOCATION -o $ZIPFILE
}

download_zip weblogic-deploy-tooling.zip $WDT_INSTALLER_DIR DOWNLOAD_WDT
download_zip weblogic-image-tool.zip $WIT_INSTALLER_DIR DOWNLOAD_WIT
