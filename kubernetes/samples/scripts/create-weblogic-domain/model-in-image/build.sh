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

# This step builds a base image (WebLogic Installer with patches) using the WebLogic Image Tool
# If you are using your own image, you can skip this step

./build_image_base.sh

# This step builds the sample application

./build_app.sh

# This step builds a model image for deploying to the Kubernetes Cluster using the base
# image and the model files in ./models

./build_image_model.sh
