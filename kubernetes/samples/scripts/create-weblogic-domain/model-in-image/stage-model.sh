#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# TBD doc we skip self already a WORKDIR/model dir.  Maybe add a -force option that deletes and recreates.

#
# This script stages a wdt model to directory 'WORKDIR/model' for future inclusion
# in a model-in-image image:
#
#   - It builds the 'SCRIPTDIR/sample-app' application 'ear' file, and puts the ear into
#     'WORKDIR/model/archive1.zip' along with the application's model mime mappings 
#     file 'WORKDIR/app/wlsdeploy/config/amimemappings.properties'.
#
#   - It copies WDT model files that contain WebLogic configuration from
#     'SCRIPTDIR/sample-model-jrf' or 'SCRIPTDIR/sample-model-wls'
#     into 'WORKDIR/model'. It chooses the source model file
#     based on WDT_DOMAIN_TYPE.
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
#   don't need to run this script. Instead, set environment variables 
#   that point to the location of your custom model files prior to building
#   your image as per the instructions in './build-model-image.sh'.
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

echo "@@ Info: WORKDIR='$WORKDIR'."

source ${WORKDIR}/env.sh

# TBD if env.sh changes WORKDIR itself, all bets are off! maybe we should check for that?
# TBD add explicit check for existance of env.sh in all places that we source it
# TBD let's put the boiler plate in init_x.sh and have it do the stuff that's common to all scripts
#     such as sourcing env.sh, making sure WORKDIR is setup,  moving to WORKDIR, etc.
#     maybe they can all call stage-workdir.sh which could behave differently depending
#     on whether WORKDIR already exists, and source env.sh file otherwise...

echo @@
echo @@ Info: Staging sample app model archive from SCRIPTDIR/sample-app to WORKDIR/model/archive1.zip
echo @@

mkdir -p ${WORKDIR}/model

rm -f $WORKDIR/model/archive1.zip

# avoid using an env var in an 'rm -fr' for safety reasons
cd $WORKDIR
rm -fr ./app

cp -r $SCRIPTDIR/sample-app $WORKDIR/app

cd $WORKDIR/app/wlsdeploy/applications
jar cvfM sample-app.ear *

cd $WORKDIR/app
zip ${WORKDIR}/model/archive1.zip wlsdeploy/applications/sample-app.ear wlsdeploy/config/amimemappings.properties


echo "@@"
echo "@@ Info: Staging wdt model yaml and properties from SCRIPTDIR to directory WORKDIR/model"
echo "@@"

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

case "$WDT_DOMAIN_TYPE" in 
  WLS|RestrictedJRF) cp $SCRIPTDIR/sample-model-wls/* $WORKDIR/model ;;
  JRF)               cp $SCRIPTDIR/sample-model-jrf/* $WORKDIR/model ;;
  *)                 echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1 ;;
esac
