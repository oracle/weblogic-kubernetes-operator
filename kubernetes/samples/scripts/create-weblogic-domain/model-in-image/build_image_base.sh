#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
#  This script obtains a WebLogic docker image with patches.  The default
#  version is 12.2.1.4 with no patches applied.
#  
#  Optional environment variables:
#
#   WORKDIR           - working directory for the sample with at least 10g of space
#                       defaults to /tmp/$USER/model-in-image-sample-work-dir
#
#   WDT_DOMAIN_TYPE   - WLS (default), RestrictedJRF, or JRF
#
#   BASE_IMAGE_NAME   - defaults to container-registry.oracle.com/middleware/weblogic for
#                       the 'WLS' domain type, and otherwise defaults to
#                       container-registry.oracle.com/middleware/fmw-infrastructure
#
#   BASE_IMAGE_TAG    - defaults to 12.2.1.4
#
#   BASE_IMAGE_BUILD  - 'when-missing' (default) or 'always'
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

echo "@@ Info: WORKDIR='$WORKDIR'."

source $WORKDIR/env.sh

cd ${WORKDIR} 

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

case "$WDT_DOMAIN_TYPE" in
  WLS) 
    BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/weblogic}" ;;
  JRF|RestrictedJRF)
    BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/fmw-infrastructure}" ;;
  *) 
    echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1 ;;
esac
  
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.4}

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

