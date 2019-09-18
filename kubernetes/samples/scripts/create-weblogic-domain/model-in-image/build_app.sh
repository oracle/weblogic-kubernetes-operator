#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# This script creates a wdt model archive 'models/archive1.zip' containing the 
# application located in 'models/sample_app/wlsdeploy/applications' and
# its model mime mappings specifed in 'wlsdeploy/config/amimemappings.properties'.
#
# Expects the following env vars to already be set:
#    
#    WORKDIR - working directory for the sample with at least 10g of space
#


set -eu

echo @@
echo @@ Info: Creating sample app model archive models/archive1.zip
echo @@

cd ${WORKDIR}

mkdir -p ${WORKDIR}/models

cd sample_app/wlsdeploy/applications
rm -f sample_app.ear
jar cvfM sample_app.ear *

rm -f ${WORKDIR}/models/archive1.zip
cd ../..
zip ${WORKDIR}/models/archive1.zip wlsdeploy/applications/sample_app.ear wlsdeploy/config/amimemappings.properties

