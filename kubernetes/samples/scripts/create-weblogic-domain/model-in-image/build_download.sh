#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
#  This script downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
#  If this is run behind a proxy.  Environment varibale http_proxy and https_proxy must be set
#
set -eu

cd $WORKDIR

DOWNLOAD_WIT=${DOWNLOAD_WIT:-when-missing}

DOWNLOAD_WDT=${DOWNLOAD_WDT:-when-missing}

download_zip() {
  set -eu
  local ZIPFILE=$1
  local LOCATION=$2
  local DOWNLOAD=$3
  local DOWNLOAD_VAR_NAME=$4

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

  local downloadlink=$(curl -sL https://github.com/$LOCATION/releases/latest | grep "/$LOCATION/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)

  echo "@@ Downloading latest '$LOCATION' to '$ZIPFILE' from 'https://github.com$downloadlink'."

  curl -L  https://github.com$downloadlink -o $ZIPFILE
}

download_zip weblogic-deploy-tooling.zip oracle/weblogic-deploy-tooling $DOWNLOAD_WDT DOWNLOAD_WDT
download_zip weblogic-image-tool.zip oracle/weblogic-image-tool $DOWNLOAD_WIT DOWNLOAD_WIT
