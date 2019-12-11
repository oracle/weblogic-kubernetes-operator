#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: build.sh 
#
# Expects the following env vars to already be set:
#
#    WORKDIR - working directory for the sample with at least 10g of space
#    WDT_DOMAIN_TYPE - WLS, RestrictedJRF, or JRF
#

set -eu

# This step downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
# If this is run behind a proxy, then environment variables http_proxy and https_proxy must be set.

./build_download.sh

# This step populates the model. It places a sample application and WDT files in the WORKDIR/model directory.

./build_app.sh

# This step obtains a base image using a docker pull (WebLogic with patches).

./build_image_base.sh

# This step builds a model image for deploying to the Kubernetes Cluster using the base
# image and the model files in WORKDIR/models

./build_image_model.sh
