#!/bin/bash

# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
#  This script downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
#  If this is run behind a proxy.  Environment varibale http_proxy and https_proxy must be set
#
set -e

echo Downloading latest WebLogic Image Tool

downloadlink=$(curl -sL https://github.com/oracle/weblogic-image-tool/releases/latest | grep "/oracle/weblogic-image-tool/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo Downdloading $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-image-tool.zip

echo Downloading latest WebLogic Deploy Tool

downloadlink=$(curl -sL https://github.com/oracle/weblogic-deploy-tooling/releases/latest | grep "/oracle/weblogic-deploy-tooling/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-deploy.zip
