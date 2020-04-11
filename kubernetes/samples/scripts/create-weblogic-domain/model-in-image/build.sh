#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Usage: build.sh 
#
# Optionally set the following env var:
#
#    WORKDIR
#      Working directory for the sample with at least 10g of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
# For other env vars, see the scripts that this script calls.
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

# This step downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
# If this is run behind a proxy, then environment variables http_proxy and https_proxy must be set.

$SCRIPTDIR/stage-tooling.sh

# This step populates the model. It places a sample application and WDT files in the WORKDIR/model directory.

$SCRIPTDIR/stage-model.sh

# This step obtains a base image using a docker pull (WebLogic with patches).

$SCRIPTDIR/stage-base-image.sh

# This step builds a model image. By default, it builds in the 
# base image setup by ./stage-base-iamge.sh, embeds the
# model files in WORKDIR/model that was setup by ./stage-model.sh and embeds the 
# WebLogic Deploy Tool that was downloaded by ./stage-tooling.sh.

$SCRIPTDIR/build-model-image.sh

# This step stages files for an optional configmap to WORKDIR/configmap. WORKDIR/configmap
# is unused unless both (a) a configmap is deployed with these files, and (b) the domain
# resource configuration.model.configMap field references the config map from (a).

$SCRIPTDIR/stage-configmap.sh
