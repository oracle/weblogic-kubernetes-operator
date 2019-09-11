#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: build.sh 
#
# Expects the following env vars to already be set:
#
#   WORKDIR
#   WDT_DOMAIN_TYPE
#

set -eu

export SERVER_JRE=${SERVER_JRE:-V982783-01.zip}
export WLS_INSTALLER=${WLS_INSTALLER:-V886423-01.zip}
export FMW_INSTALLER=${FMW_INSTALLER:-V886426-01.zip}

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then  
  echo "Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit
fi

if [ ! -d "${WORKDIR}" ] ; then
  echo "Directory WORKDIR='${WORKDIR}' does not exist." && exit 
fi

if [ ! -f "${SERVER_JRE}" ] ; then
  echo "Directory WORKDIR='${WORKDIR}' does not contain installer for SERVER_JRE '${SERVER_JRE}'." && exit 
fi

if [ ! -f "${WLS_INSTALLER}" ] \
   && [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
  echo "Directory WORKDIR='${WORKDIR}' does not contain WLS_INSTALLER '${WLS_INSTALLER}'." && exit 
fi

if [ ! -f "${FMW_INSTALLER}" ] \
   && [ "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" -o "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  echo "Directory WORKDIR=${WORKDIR} does not contain FMW_INSTALER '${FMW_INSTALLER}'." && exit 
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

if [ "${WDT_DOMAIN_TYPE}" == "WLS" -o "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] ; then
  cp model1.yaml.wls models/model1.yaml
fi

if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  cp model1.yaml.jrf models/model1.yaml
fi

# This step builds a model image for deploying to the Kubernetes Cluster using the base
# image and the model files in ./models

./build_model_image.sh

echo Setting Domain Type in k8s-domain.yaml
#
sed s/@@DOMTYPE@@/${WDT_DOMAIN_TYPE}/ k8s-domain.yaml.template > k8s-domain.yaml


