#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

cwd=`pwd`
cd image/sample_app/wlsdeploy/applications
if [ -f "sample_app.ear" ] ; then
  rm sample_app.ear
fi
jar cvfM sample_app.ear *
cd $cwd
cd image/sample_app
if [ -f "../archive1.zip" ] ; then
    rm "../archive1.zip"
fi
zip ../archive1.zip wlsdeploy/applications/sample_app.ear wlsdeploy/config/amimemappings.properties
cd $cwd
