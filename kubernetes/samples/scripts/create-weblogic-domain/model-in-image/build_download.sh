#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
#  This script downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
#  If this is run behind a proxy.  Environment varibale http_proxy and https_proxy must be set
#
set -eu

echo Downloading latest WebLogic Image Tool

ZIPFILE=weblogic-image-tool.zip

if [ -f $ZIPFILE ]; then
  echo Skipping download since local file $ZIPFILE already exists.
else
  downloadlink=$(curl -sL https://github.com/oracle/weblogic-image-tool/releases/latest | grep "/oracle/weblogic-image-tool/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
  echo Downloading $downloadlink
  curl -L  https://github.com$downloadlink -o $ZIPFILE
fi

echo Downloading latest WebLogic Deploy Tool

ZIPFILE=weblogic-deploy.zip

if [ -f $ZIPFILE ]; then
  echo Skipping download since local file $ZIPFILE already exists.
else
  downloadlink=$(curl -sL https://github.com/oracle/weblogic-deploy-tooling/releases/latest | grep "/oracle/weblogic-deploy-tooling/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
  echo $downloadlink
  curl -L  https://github.com$downloadlink -o $ZIPFILE
fi
