#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Usage: 'stage_model_image.sh'
# 
# This script stages a wdt archive and then stages a wdt model
# and the zipped archive to directory MODEL_DIR for future inclusion
# in a model-in-image image. In detail:
#
#   - It defaults MODEL_DIR to
#     'WORKDIR/model/image--$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG'.
#
#   - It copies WDT model files from 'SCRIPTDIR/sample-model/WDT_DOMAIN_TYPE/*'
#     into 'MODEL_DIR' if MODEL_DIR doesn't already exist.
#
#   - It copies the 'SCRIPTDIR/sample-archive' directory tree to the
#     'WORKDIR/archive/image--$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG'
#     directory if the target directory doesn't already exist.
#     The 'SCRIPTDIR/sample-archive' direcctory contains an exploded ear
#     jsp application.
#
#   - It zips the archive target directory contents and puts
#     the zip in 'MODEL_DIR/archive1.zip' even if 'MODEL_DIR/archive1.zip'
#     already exists.
#
# Optionally set these environment variables:
#
#   WORKDIR
#     Working directory for the sample with at least 10GB of space.
#     Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#   WDT_DOMAIN_TYPE 
#     'WLS' (default), 'JRF', or 'RestrictedJRF'.
#
#   MODEL_DIR
#     Location to stage the model files. Default is:
#     WORKDIR/model/image--$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG
#
# CUSTOMIZATION NOTES:
#
#   If you want to specify your own model files for an image in this
#   sample, then you have two options: 
#    (1) DO NOT run this script. Instead, export the MODEL_DIR environment
#        variable to point to the location of your custom model files
#        prior to building your image as per the instructions in 
#        './build-model-image.sh'.
#    (2) Run this script, modify the resulting WORKDIR/archive directory
#        and/or MODEL_DIR, then run this script again (to zip the
#        archive to the MODEL_DIR), and finally run './build-model-image.sh'.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

echo "@@ Info: WDT_DOMAIN_TYPE='$WDT_DOMAIN_TYPE'"
echo "@@ Info: MODEL_DIR='$MODEL_DIR'"

mkdir -p ${WORKDIR}

#
# copy over sample model yaml/properties - but skip if this was already done before
#

echo "@@"
echo "@@ Info: Copying wdt model yaml and properties files from directory 'SCRIPTDIR/sample-model/$WDT_DOMAIN_TYPE' to directory 'MODEL_DIR'."

if [ -e ${MODEL_DIR} ]; then
  echo "@@"
  echo "@@ Notice! Skipping copy of yaml and properties files - target MODEL_DIR directory already exists."
else 
  mkdir -p ${MODEL_DIR}
  cp $SCRIPTDIR/sample-model/$WDT_DOMAIN_TYPE/* ${MODEL_DIR}
fi

#
# copy over sample archive to WORKDIR/archive/... - but skip if this was already done before
#

target_archive_suffix="archive/image--$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG"
target_archive_dir="$WORKDIR/$target_archive_suffix"

echo "@@"
echo "@@ Info: Copying sample archive with an exploded ear jsp app from 'SCRIPTDIR/sample-archive' to the 'WORKDIR/$target_archive_suffix' directory."

if [ ! -d $target_archive_dir ]; then
  mkdir -p $target_archive_dir
  cp -r $SCRIPTDIR/sample-archive/wlsdeploy $target_archive_dir
else
  echo "@@"
  echo "@@ Notice! Skipping copy of sample archive - target directory 'WORKDIR/$target_archive_suffix' already exists."
fi

#
# zip the archive and place it in the WORKDIR/model directory
#

echo "@@"
echo "@@ Info: Removing old archive zip (if any), and zipping 'WORKDIR/$target_archive_suffix' contents to 'MODEL_DIR/archive1.zip'."

cd $target_archive_dir
rm -f $MODEL_DIR/archive1.zip
zip -q -r $MODEL_DIR/archive1.zip wlsdeploy

echo "@@"
echo "@@ Done!"
