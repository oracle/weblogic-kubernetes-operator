#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Usage: 'stage_model_image.sh'
# 
# This script stages a wdt model to directory 'WORKDIR/model' for future
# inclusion in a model-in-image image:
#
#   - It removes any existing files in 'WORKDIR/model'.
#
#   - It copies the 'SCRIPTDIR/sample-archive' directory tree to
#     'WORKDIR/archive' if the 'WORKDIR/archive' directory doesn't 
#     already exist. This tree contains an exploded ear jsp application.
#
#   - It zips the 'WORKDIR/archive' directory contents and puts
#     the zip in 'WORKDIR/model/archive1.zip'.
#
#   - It copies WDT model files from 'SCRIPTDIR/sample-model/WDT_DOMAIN_TYPE/*'
#     into 'WORKDIR/model'. 
#
# Optionally set these environment variables:
#
#    WORKDIR
#      Working directory for the sample with at least 10g of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    WDT_DOMAIN_TYPE 
#      'WLS' (default), 'JRF', or 'RestrictedJRF'.
#
# CUSTOMIZATION NOTES:
#
#   If you want to specify your own model files for an image, then you 
#   don't need to run this script. Instead, set an environment variable
#   that points to the location of your custom model files prior to building
#   your image as per the instructions in './build-model-image.sh'.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

echo "@@ WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE"

mkdir -p ${WORKDIR}

#
# copy over sample model yaml/properties - but skip if this was already done before
#

echo "@@"
echo "@@ Info: Copying wdt model yaml and properties files from directory 'SCRIPTDIR/sample-model/$WDT_DOMAIN_TYPE' to directory 'WORKDIR/model'."

if [ -e ${WORKDIR}/model ]; then
  echo "@@"
  echo "@@ Notice! Skipping copy of yaml and properties files - target directory already exists."
else 
  mkdir -p ${WORKDIR}/model
  cp $SCRIPTDIR/sample-model/$WDT_DOMAIN_TYPE/* $WORKDIR/model
fi

#
# copy over sample archive - but skip if this was already done before
#

echo "@@"
echo "@@ Info: Copying sample archive with an exploded ear jsp app from SCRIPTDIR to the 'WORKDIR/archive' directory."

if [ ! -d $WORKDIR/archive ]; then
  cp -r $SCRIPTDIR/sample-archive $WORKDIR/archive
else
  echo "@@"
  echo "@@ Notice! Skipping copy of sample archive - target directory already exists."
fi

#
# zip the archive and place it in the WORKDIR/model directory
#

echo "@@"
echo "@@ Info: Removing old archive zip (if any), and zipping 'WORKDIR/archive' contents to 'WORKDIR/model/archive1.zip'."

cd $WORKDIR/archive
rm -f ../model/archive1.zip
zip -q -r ../model/archive1.zip wlsdeploy

echo "@@"
echo "@@ Done!"
