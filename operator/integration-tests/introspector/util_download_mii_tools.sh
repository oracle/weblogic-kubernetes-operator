#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script downloads the latest WebLogic Deploy Tool and WebLogic Image Tool 
# to the WORKDIR directory.
#
# Optional environment variables:
#
#    WORKDIR 
#      working directory with at least 10g of space
#      defaults to /tmp/$USER/model-in-image-sample-work-dir
#
#    http_proxy https_proxy
#      If running behind a proxy, then set as needed to allow curl access to github.com.
#
#    DOWNLOAD_WDT DOWNLOAD_WIT
#      Default to 'when-missing'. Set to 'always' to force download even
#      if local installer zip is missing.
#
#    WDT_INSTALLER_URL WIT_INSTALLER_URL
#      Defaults to 'https://github.com/oracle/weblogic-deploy-tooling/releases/latest'
#      and 'https://github.com/oracle/weblogic-image-tool/releases/latest' respectively.
#
#      To override an installer URL, export the URL env to point to a specific zip file, for example:
#      export WDT_INSTALLER_URL=https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-1.9.7/weblogic-deploy.zip
#

set -o pipefail

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

DOWNLOAD_WIT=${DOWNLOAD_WIT:-when-missing}
DOWNLOAD_WDT=${DOWNLOAD_WDT:-when-missing}
WDT_INSTALLER_URL=${WDT_INSTALLER_URL:-https://github.com/oracle/weblogic-deploy-tooling/releases/latest}
WIT_INSTALLER_URL=${WIT_INSTALLER_URL:-https://github.com/oracle/weblogic-image-tool/releases/latest}
WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

echo "@@ Info: WORKDIR='$WORKDIR'."

mkdir -p ${WORKDIR}
cd ${WORKDIR}

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
    echo "@@             file '$WORKDIR/$ZIPFILE' already exists.                   "
    echo "@@             To force a download, 'export $DOWNLOAD_VAR_NAME=always'.   "
    echo "@@ -----------------------------------------------------------------------"
    echo "@@"
    sleep 2
    return
  fi

  echo "@@ Downloading '$LOCATION' to '$WORKDIR/$ZIPFILE'."

  local iurl="$LOCATION"
  if [ "`echo $iurl | grep -c 'https://github.com.*/latest$'`" = "1" ]; then
    echo "@@ The location URL matches regex 'https://github.com.*/latest$'. About to convert to direct location."
    local tempfile="$(mktemp -u).$(basename $0).$SECONDS.$PPID.$RANDOM"
    curl -fL $LOCATION -o $tempfile
    LOCATION=https://github.com/$(cat $tempfile | grep "releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
    rm -f $tempfile
    echo "@@ The location URL matched regex 'https://github.com.*/latest$' so it was converted to '$LOCATION'"
    echo "@@ Now downloading '$LOCATION' to '$WORKDIR/$ZIPFILE'."
  fi

  rm -f $ZIPFILE
  curl -fL $LOCATION -o $ZIPFILE
}

download_zip weblogic-deploy-tooling.zip $WDT_INSTALLER_URL DOWNLOAD_WDT
download_zip weblogic-image-tool.zip $WIT_INSTALLER_URL DOWNLOAD_WIT
