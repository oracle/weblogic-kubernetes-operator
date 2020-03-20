#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

#
# This script stages a wdt model to directory 'WORKDIR/models' for future inclusion
# in a model-in-image image:
#
#   - It builds the 'SCRIPTDIR/sample_app' application 'ear' file, and puts the ear into
#     'WORKDIR/models/archive1.zip' along with the application's model mime mappings 
#     file 'WORKDIR/sample_app/wlsdeploy/config/amimemappings.properties'.
#
#   - It copies WDT model files that contain WebLogic configuration from
#     'SCRIPTDIR' into 'WORKDIR/models'. It chooses the source model file
#     based on WDT_DOMAIN_TYPE.
#
# This script also stages a wdt file to 'WORKDIR/wdtconfigmap' for future inclusion
# in a config map that's in turn referenced by a domain resource.
#
#   - It copies 'WORKDIR/model1.20.properties' to 'WORKDIR/wdtconfigmap'.
#
# CUSTOMIZATION NOTE:
#   If you want to specify your own model files for an image and your own
#   wdt config map files, then you don't need to run this script. Instead:
#   - Set environment variables that indicate the location 
#     of your model files as per prior to building your image
#     as per the instructions in './build_image_model.sh'.
#   - Set the WDTCONFIGMAPDIR to indicate the location of your
#     wdt config map files, prior to creating your wdt config
#     map (see ./run_domain.sh).
#
# This script expects the following env vars to already be set:
#    
#    WORKDIR - working directory for the sample with at least 10g of space
#
# Optionally set:
#
#    WDT_DOMAIN_TYPE - 'WLS' (default), 'JRF', or 'RestrictedJRF'.
#
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

echo @@
echo @@ Info: Staging sample app model archive from SCRIPTDIR/sample_app to WORKDIR/models/archive1.zip
echo @@

cd ${WORKDIR?}

mkdir -p ${WORKDIR}/models

cp -r $SCRIPTDIR/sample_app $WORKDIR/sample_app_stage
cd sample_app_stage/wlsdeploy/applications
rm -f sample_app.ear
jar cvfM sample_app.ear *

rm -f ${WORKDIR}/models/archive1.zip
cd ../..
zip ${WORKDIR}/models/archive1.zip wlsdeploy/applications/sample_app.ear wlsdeploy/config/amimemappings.properties

echo "@@"
echo "@@ Info: Staging wdt model yaml and properties from SCRIPTDIR to directory WORKDIR/models"
echo "@@"

cd ${WORKDIR?}

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

case "$WDT_DOMAIN_TYPE" in 
  WLS|RestrictedJRF) cp $SCRIPTDIR/model1.yaml.wls models/model1.yaml ;;
  JRF)               cp $SCRIPTDIR/model1.yaml.jrf models/model1.yaml ;;
  *)                 echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1 ;;
esac

cp $SCRIPTDIR/model1.10.properties models/model1.10.properties

mkdir -p ${WORKDIR}/wdtconfigmap

echo "@@"
echo "@@ Info: Staging wdt properties file from SCRIPTDIR to WORKDIR/wdtconfigmap"
echo "@@"

cp $SCRIPTDIR/model1.20.properties ${WORKDIR}/wdtconfigmap

