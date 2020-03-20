#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: build.sh 
#
# Expects the following env var to already be set:
#
#    WORKDIR - working directory for the sample with at least 10g of space
#
# For other env vars, see the scripts that this script calls.
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

# This step downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
# If this is run behind a proxy, then environment variables http_proxy and https_proxy must be set.

$SCRIPTDIR/build_download.sh

# This step populates the model. It places a sample application and WDT files in the WORKDIR/models directory.

$SCRIPTDIR/build_model.sh

# This step obtains a base image using a docker pull (WebLogic with patches).

$SCRIPTDIR/build_image_base.sh

# This step builds a model image using the base image. It embeds the model files
# in WORKDIR/models that was setup by ./build_model.sh and embeds the 
# WebLogic Deploy Tool that was downloaded by build_download.sh.

$SCRIPTDIR/build_image_model.sh
