#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This script sets defaults for domain type, base image name/tag, and model image name/tag.
#
# It's called from 'build_image_base.sh' and 'build_image_model.sh'.
#
# Expects the following env vars to already be set:
#
#   WORKDIR           - working directory for the sample with at least 10g of space
#
# Optional env vars:
#
#   WDT_DOMAIN_TYPE   - WLS (default), RestrictedJRF, or JRF
#
#   BASE_IMAGE_NAME   - defaults to container-registry.oracle.com/middleware/weblogic for
#                       the 'WLS' domain type, and otherwise defaults to
#                       container-registry.oracle.com/middleware/fmw-infrastructure
#           
#   BASE_IMAGE_TAG    - defaults to 12.2.1.3
#
#   BASE_IMAGE_BUILD  - 'when-missing' (default) or 'always'
#
#   MODEL_IMAGE_NAME  - defaults to 'model-in-image'
#
#   MODEL_IMAGE_TAG   - defaults to 'v1'
#
#   MODEL_IMAGE_BUILD - 'when-missing' (default) or 'always'
#

cd ${WORKDIR}

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1
fi

if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
  BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/weblogic}"
else
  BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/fmw-infrastructure}"
fi

BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.3}
BASE_IMAGE_BUILD=${BASE_IMAGE_BUILD:-when-missing}

# If not using prebuilt image - TBD review this logic with JS
# if [[ ${BASE_IMAGE_NAME} != container-registry.oracle.com* ]] ; then
#   if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
#     BASE_IMAGE_NAME=${BASE_IMAGE_NAME}-wls
#   else
#     BASE_IMAGE_NAME=${BASE_IMAGE_NAME}-fmw
#   fi
# fi

MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-when-missing}
