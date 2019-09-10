#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: build.sh <working directory> <oracle support id> <oracle support id password> <domain type:WLS|RestrictedJRF|JRF>
#
set -eu
usage() {
    echo "build.sh <working directory> <oracle support id> <oracle support id password> <domain type:WLS|RestrictedJRF|JRF>"
}
if [ "$#" != 4 ] ; then
  usage && exit
fi

export WORKDIR=$1
export USERID=$2
export USERPWD=$3
export DOMAINTYPE=$4
export SERVER_JRE=V982783-01.zip
export WLS_INSTALLER=V886423-01.zip
export FMW_INSTALLER=V886426-01.zip

if [ ! "${DOMAINTYPE}" == "WLS" ] \
   && [ ! "${DOMAINTYPE}" == "RestrictedJRF" ] \
   && [ ! "${DOMAINTYPE}" == "JRF"]; then  
  echo "Invalid domain type '$DOMAINTYPE': Expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit
fi

if [ ! -d "${WORKDIR}" ] ; then
  echo "Directory WORKDIR does not exists." && exit 
fi

if [ ! -f "${SERVER_JRE}" ] ; then
  echo "Directory ${WORKDIR} does not contain ${SERVER_JRE}." && exit 
fi

if [ ! -f "${WLS_INSTALLER}" ] \
   && [ "${DOMAINTYPE}" == "WLS" ] ; then
  echo "Directory ${WORKDIR} does not contain ${WLS_INSTALLER}." && exit 
fi

if [ ! -f "${FMW_INSTALLER}" ] \
   && [ "${DOMAINTYPE}" == "RestrictedJRF" -o "${DOMAINTYPE}" == "JRF" ] ; then
  echo "Directory ${WORKDIR} does not contain ${FMW_INSTALLER}." && exit 
fi

# This step downloads the latest WebLogic Deploy Tool and WebLogic Image Tool in the current directory
# If this is run behind a proxy, then environment variables http_proxy and https_proxy must be set.

./build_download.sh

# This step builds a base image (WebLogic Installer with patches) using the WebLogic Image Tool
# If you are using your own image, you can skip this step

./build_base_image.sh

# This step sets up the models directory that will be copied into the final sample image.

mkdir -p ./models

./build_app.sh # bundles the sample app into 'models/archive1.zip'

if [ "${DOMAINTYPE}" == "WLS" ] ; then
  cp model1.yaml.wls models/model1.yaml
fi

if [ "${DOMAINTYPE}" == "JRF" -o "${DOMAINTYPE}" == "RestrictedJRF" ] ; then
  cp model1.yaml.jrf models/model1.yaml
fi

# This step builds a model image for deploying to the Kubernetes Cluster using the base
# image and the model files in ./models

./build_model_image.sh

echo Setting Domain Type in k8s-domain.yaml
#
sed s/@@DOMTYPE@@/${DOMAINTYPE}/ k8s-domain.yaml.template > k8s-domain.yaml


