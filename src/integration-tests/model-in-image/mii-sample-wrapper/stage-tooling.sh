#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script downloads the latest version of the WebLogic Deploy Tooling
# and of the WebLogic Image Tool to WORKDIR/weblogic-deploy-tooling.zip
# and WORKDIR/weblogic-image-tool.zip by default.
#
# Optional command line:
#    -dry    Show, but don't perform, the final download command. (This
#            may still implicilty perform some web actions in order
#            to locate the installer in github.com).
#
# Optional environment variables (see 'env-custom.sh' for more details):
#
#    WORKDIR 
#      Working directory for the sample with at least 10GB of space
#      defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    http_proxy https_proxy
#      If running behind a proxy, then set as needed to allow curl access
#      to github.com.
#
#    DOWNLOAD_WDT DOWNLOAD_WIT
#      Default to 'when-missing'. Set to 'always' to force download even
#      if local installer zip already exists.
#
#    WDT_INSTALLER_URL WIT_INSTALLER_URL
#      Defaults to 'https://github.com/oracle/weblogic-deploy-tooling/releases/latest'
#      and 'https://github.com/oracle/weblogic-image-tool/releases/latest' respectively.
#      To override an installer URL, see 'env-custom.sh' for an example.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

dry_run=false
[ "${1:-}" = "-dry" ] && dry_run=true

cd ${WORKDIR}/model-images

download_zip() {
  set -eu
  local ZIPFILE=$1
  local LOCATION=$2
  local DOWNLOAD_VAR_NAME=$3
  local DOWNLOAD=${!DOWNLOAD_VAR_NAME}

  if [ ! "$dry_run" = "true" ] && [ ! "$DOWNLOAD" = "always" ] && [ -f $ZIPFILE ]; then
    echo "@@"
    echo "@@ -----------------------------------------------------------------------"
    echo "@@ Info: NOTE! Skipping '$LOCATION' download since local                  "
    echo "@@             file '$WORKDIR/$ZIPFILE' already exists.                   "
    echo "@@             To force a download, 'export $DOWNLOAD_VAR_NAME=always'.   "
    echo "@@ -----------------------------------------------------------------------"
    echo "@@"
    return
  fi

  echo "@@ Info: Downloading '$LOCATION' to '$WORKDIR/$ZIPFILE'."

  local iurl="$LOCATION"
  if [ "`echo $iurl | grep -c 'https://github.com.*/latest$'`" = "1" ]; then
    echo "@@ Info: The location URL matches regex 'https://github.com.*/latest$'. About to convert to direct location."
    local tempfile="$(mktemp -u).$(basename $0).$SECONDS.$PPID.$RANDOM"
    curl -m 30 -fL $LOCATION -o $tempfile
    LOCATION=https://github.com/$(cat $tempfile | grep "releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
    rm -f $tempfile
    echo "@@ Info: The location URL matched regex 'https://github.com.*/latest$' so it was converted to '$LOCATION'"
    echo "@@ Info: Now downloading '$LOCATION' to '$WORKDIR/$ZIPFILE'."
  fi

  if [ ! "$dry_run" = "true" ]; then
    rm -f $ZIPFILE
    curl -m 30 -fL $LOCATION -o $ZIPFILE
  else
    echo "dryrun:rm -f $ZIPFILE"
    echo "dryrun:curl -m 30 -fL $LOCATION -o $ZIPFILE"
  fi
}

download_zip weblogic-deploy-tooling.zip $WDT_INSTALLER_URL DOWNLOAD_WDT
download_zip weblogic-image-tool.zip $WIT_INSTALLER_URL DOWNLOAD_WIT
