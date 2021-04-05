#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script downloads the latest version of the WebLogic Deploy Tooling
# and of the WebLogic Image Tool to WORKDIR/model-images/weblogic-deploy-tooling.zip
# and WORKDIR/model-images/weblogic-image-tool.zip by default.
#
# Optional command line:
#    -dry    Show, but don't perform, the final download command. (This
#            may still implicilty perform some web actions in order
#            to locate the installer in github.com).
#
# Optional environment variables (see README for details):
#
#    http_proxy https_proxy
#      If running behind a proxy, then set as needed to allow curl access
#      to github.com.
#
#    WORKDIR 
#    DOWNLOAD_WDT DOWNLOAD_WIT
#    WDT_INSTALLER_URL WIT_INSTALLER_URL
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

# curl timeout args:

curl_parms="--connect-timeout 5"
curl_parms+=" --max-time 120"       # max seconds for each try
curl_parms+=" --retry 3"            # retry up to 3 times
curl_parms+=" --retry-delay 0"      # disable exponential backoff 
curl_parms+=" --retry-max-time 400" # total seconds before giving up

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
    echo "@@             file '$WORKDIR/model-images/$ZIPFILE' already exists.      "
    echo "@@             To force a download, 'export $DOWNLOAD_VAR_NAME=always'.   "
    echo "@@ -----------------------------------------------------------------------"
    echo "@@"
    return
  fi

  echo "@@ Info: Downloading $ZIPFILE from '$LOCATION' to '$WORKDIR/$ZIPFILE'."

  local iurl="$LOCATION"
  if [ "`echo $iurl | grep -c 'https://github.com.*/latest$'`" = "1" ]; then
    echo "@@ Info: The location URL matches regex 'https://github.com.*/latest$'. About to convert to direct location."
    if [ "$DOWNLOAD_VAR_NAME" == "DOWNLOAD_WDT" ]; then
      LOCATION=https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/
    fi
    if [ "$DOWNLOAD_VAR_NAME" == "DOWNLOAD_WIT" ]; then
      LOCATION=https://github.com/oracle/weblogic-image-tool/releases/latest/download/
    fi
    echo "@@ Info: The location URL matched regex 'https://github.com.*/latest$' so it was converted to '$LOCATION'"
    echo "@@ Info: Now downloading '$LOCATION' to '$WORKDIR/$ZIPFILE'."
  fi

  if [ ! "$dry_run" = "true" ]; then
    rm -f $ZIPFILE
    echo "@@ Info: Calling 'curl $curl_parms -fL $LOCATION/$ZIPFILE -o $ZIPFILE'"
    curl $curl_parms -fL $LOCATION/$ZIPFILE -o $ZIPFILE
  else
    echo "dryrun:rm -f $ZIPFILE"
    echo "dryrun:curl $curl_parms -fL $LOCATION/$ZIPFILE -o $ZIPFILE"
  fi
}

if [ "$dry_run" = "true" ]; then
  echo "dryrun:#!/bin/bash"
  echo "dryrun:# Copyright (c) 2019, 2021, Oracle and/or its affiliates."
  echo "dryrun:# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl."
  echo "dryrun:"
  echo "dryrun:set -eux"
  echo "dryrun:"
fi

echo "@@ Info: Proxy settings:"
set +e
env | grep -i _proxy=
set -e

download_zip weblogic-deploy.zip $WDT_INSTALLER_URL DOWNLOAD_WDT
download_zip imagetool.zip $WIT_INSTALLER_URL DOWNLOAD_WIT
