#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#  This script build a sample docker image for deploying to the Kubernetes cluster with the model in image
#  artifacts. It is based on a base image build earlier with build_base_image.sh
#  
#  Assumption:
#    This script should be called by build.sh.  
#
#  Environment variable used:
#  IMGTOOL_BIN - directory of the WebLogic Image Tool

set -eu
CURRENT_DIR=`pwd`
IMGTOOL_BIN=${CURRENT_DIR}/imagetool/bin/imagetool.sh

echo Creating deploy image with wdt models
#
cd models 
${IMGTOOL_BIN} update \
  --tag model-in-image:x1 \
  --fromImage model-in-image:x0 \
  --wdtModel model1.yaml \
  --wdtVariables model1.10.properties \
  --wdtArchive archive1.zip \
  --wdtModelOnly \
  --wdtDomainType ${DOMAINTYPE}
cd ..
