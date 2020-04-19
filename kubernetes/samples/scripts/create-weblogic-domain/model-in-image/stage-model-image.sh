#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# TBD should we skip self if there's already a WORKDIR/model dir?
#     maybe have a 'when-missing/always' option?

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
# CUSTOMIZATION NOTE:
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

#
# delete old model files in WORKDIR/model, if any
# avoid using an env var in an 'rm -fr' for safety reasons
#

cd ${WORKDIR}
rm -fr ./model
mkdir -p ${WORKDIR}/model

echo "@@ Info: Copying wdt model yaml and properties files from directory 'SCRIPTDIR/sample-model/$WDT_DOMAIN_TYPE' to directory 'WORKDIR/model'."

cp $SCRIPTDIR/sample-model/$WDT_DOMAIN_TYPE/* $WORKDIR/model

echo "@@ Info: Copying sample archive with an exploded ear jsp app from SCRIPTDIR to the 'WORKDIR/archive' directory."

if [ ! -d $WORKDIR/archive ]; then
  cp -r $SCRIPTDIR/sample-archive $WORKDIR/archive
fi

echo "@@ Info: Zipping 'WORKDIR/archive' contents to 'WORKDIR/model/archive1.zip'."

cd $WORKDIR/archive
zip -r ../model/archive1.zip wlsdeploy
