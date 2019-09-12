#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# This script creates a wdt model archive 'models/archive1.zip' containing the 
# application located in 'models/sample_app/wlsdeploy/applications' and
# its model mime mappings specifed in 'wlsdeploy/config/amimemappings.properties'.

set -eu

echo @@
echo @@ Info: Creating sample app model archive models/archive1.zip
echo @@

cd $WORKDIR

mkdir -p models

cd sample_app/wlsdeploy/applications
rm -f sample_app.ear
jar cvfM sample_app.ear *

cd $WORKDIR
cd sample_app
rm -f ../models/archive1.zip
zip ../models/archive1.zip wlsdeploy/applications/sample_app.ear wlsdeploy/config/amimemappings.properties

cd $WORKDIR
