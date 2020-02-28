#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
#  This script obtains a WebLogic docker image with patches.  The default
#  version is 12.2.1.3 with interim patch 2915930. 
#  
#  Expects the following env vars to already be set:
#
#    WORKDIR - working directory for the sample with at least 10g of space
#
#  Optional env vars:
#
#    BASE_IMAGE_NAME, BASE_IMAGE_TAG, BASE_IMAGE_BUILD: 
#        See build_image_init.sh for a description.
#

set -eu

source build_image_init.sh

cd ${WORKDIR}

BASE_IMAGE_BUILD=${BASE_IMAGE_BUILD:-when-missing}
if [ ! "$BASE_IMAGE_BUILD" = "always" ] && \
   [ "`docker images $BASE_IMAGE_NAME:$BASE_IMAGE_TAG | awk '{ print $1 ":" $2 }' | grep -c $BASE_IMAGE_NAME:$BASE_IMAGE_TAG`" = "1" ]; then
  echo @@
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!! Skipping base image pull because image '$BASE_IMAGE_NAME:$BASE_IMAGE_TAG' already exists. "
  echo "@@ Info:         To always pull the base image, 'export BASE_IMAGE_BUILD=always'.                         "
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo @@
  sleep 3
  exit 0
else
  echo @@
  echo "@@ Info: Pulling image '$BASE_IMAGE_NAME:$BASE_IMAGE_TAG'"
  echo @@
fi

docker pull ${BASE_IMAGE_NAME}:${BASE_IMAGE_TAG}

