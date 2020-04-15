#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# TBD doc we skip self already a WORKDIR/model dir.  Maybe add a -force option that deletes and recreates.

#
# This script stages a wdt model to directory 'WORKDIR/model' for future inclusion
# in a model-in-image image:
#
#   - It removes any existing files in 'WORKDIR/model'.
#
#   - It copies the 'SCRIPTDIR/sample-app' files to 'WORKDIR/app' if the 
#     'WORKDIR/app' directory doesn't already exist.
#
#   - It builds the 'WORKDIR/sample-app' application 'ear' file, and puts the
#     ear into a 'WORKDIR/model/archive1.zip' along with the application's model
#     mime mappings file 'WORKDIR/app/wlsdeploy/config/amimemappings.properties'.
#
#   - It copies WDT model files from 'SCRIPTDIR/sample-model-jrf' or
#     'SCRIPTDIR/sample-model-wls' into 'WORKDIR/model'. It chooses the
#     source model file based on WDT_DOMAIN_TYPE.
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

# avoid using an env var in an 'rm -fr' for safety reasons
cd ${WORKDIR}
rm -fr ./model

mkdir -p ${WORKDIR}/model

echo "@@ Info: Staging wdt model yaml and properties from SCRIPTDIR to directory WORKDIR/model"

case "$WDT_DOMAIN_TYPE" in 
  WLS|RestrictedJRF) cp $SCRIPTDIR/sample-model-wls/* $WORKDIR/model ;;
  JRF)               cp $SCRIPTDIR/sample-model-jrf/* $WORKDIR/model ;;
esac

echo "@@ Info: Staging sample app model archive from SCRIPTDIR/sample-app to WORKDIR/model/archive1.zip"

if [ ! -d $WORKDIR/app ]; then
  cp -r $SCRIPTDIR/sample-app $WORKDIR/app
fi

cd $WORKDIR/app/wlsdeploy/applications
jar cvfM sample-app.ear *

cd $WORKDIR/app
zip ${WORKDIR}/model/archive1.zip \
    wlsdeploy/applications/sample-app.ear \
    wlsdeploy/config/amimemappings.properties

# TBD split this finer grained
#        build app
