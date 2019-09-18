#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
#  This script leverages the WebLogic image tool to build a WebLogic docker image with patches.  The 
#  version is 12.2.1.3.0 with PS4 and interim patch 2915930
#  
#  Assumptions:
#    This script should be called by build.sh.  
#    The $WORKDIR working directory must have at least 10g of space.
#
#  Expects the following env vars to already be set:
#    WORKDIR - working directory for the sample with at least 10g of space
#    WDT_DOMAIN_TYPE - WLS, RestrictedJRF, or JRF
#
#  This script prompts for:
#    Oracle Support Username
#    Oracle Support Password
#

set -eu

cd ${WORKDIR}

source build_image_init.sh

echo @@
echo "@@ Info: Starting base image build for '$BASE_IMAGE_REPO:$BASE_IMAGE_TAG'"
echo @@

if [ ! "$BASE_IMAGE_BUILD" = "always" ] && \
   [ "`docker images $BASE_IMAGE_REPO:$BASE_IMAGE_TAG | awk '{ print $1 ":" $2 }' | grep -c $BASE_IMAGE_REPO:$BASE_IMAGE_TAG`" = "1" ]; then
  echo @@
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!! Skipping base image build because image '$BASE_IMAGE_REPO:$BASE_IMAGE_TAG' already exists."
  echo "@@ Info:         To always build the base image, 'export  BASE_IMAGE_BUILD=always'.                        "
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo @@
  sleep 3
  exit 0
fi

(
set +x

if [ -z "${USERID:-}" ]; then
  echo -n "Enter an Oracle Support Username: "
  read USERID
  echo
fi

if [ -z "${USERPWD:-}" ]; then
  echo -n "Enter Oracle Support Password for Username '$USERID': "
  read -s USERPWD
  echo
fi

${IMGTOOL_BIN} create \
  --tag $BASE_IMAGE_REPO:$BASE_IMAGE_TAG \
  --user ${USERID} \
  --password ${USERPWD} \
  --patches ${REQUIRED_PATCHES} \
  --jdkVersion ${SERVER_JRE_VERSION} \
  --type ${IMGTYPE}

)
