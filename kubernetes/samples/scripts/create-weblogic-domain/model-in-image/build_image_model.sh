#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#  This script uses the WebLogic Image Tool to build a docker image with model in image
#  artifacts. By default, it uses the base image obtained earlier with build_image_base.sh,
#  and it gets model files from the WORKDIR/models directory that was setup by the build_model.sh script.
#
#  The model image is named MODEL_IMAGE_NAME:MODEL_IMAGE_TAG.  See build_init_image.sh for
#  the defaults for these values.
#  
#  Assumptions:
#
#    This script should be called by build.sh.  
#    The WebLogic Image Tool is downloaded to WORKDIR/weblogic-image-tool.zip (see ./build_download.sh).
#    The WebLogic Deploy Tool is downloaded to WORKDIR/weblogic-deploy-tooling.zip (see ./build_download.sh).
#    Model files have been staged in the "WORKDIR/models" directory (see ./build_model.sh) or
#    MODEL_DIR has been explicitly set to point to a different location.
#
#  Required environment variables:
#
#    WORKDIR - working directory for the sample with at least 10g of space
#
#  Optional environment variables:
#
#    MODEL_DIR:
#      Location of the model .zip, .properties, and .yaml files
#      that will be copied in to the image.  Default is 'WORKDIR/models'
#      which is populated by the ./build_model.sh script.
#
#    MODEL_YAML_FILES, MODEL_ARCHIVE_FILES, MODEL_VARIABLES_FILES:
#      Optionally set one or more of these with comma-separated lists of file
#      locations to override the corresponding .yaml, .zip, and .properties
#      files normally obtained from MODEL_DIR.
#
#    WDT_DOMAIN_TYPE   - WLS (default), RestrictedJRF, or JRF
#    BASE_IMAGE_NAME   - defaults to container-registry.oracle.com/middleware/weblogic for
#                        the 'WLS' domain type, and otherwise defaults to
#                        container-registry.oracle.com/middleware/fmw-infrastructure
#    BASE_IMAGE_TAG    - defaults to 12.2.1.4
#    BASE_IMAGE_BUILD  - 'when-missing' (default) or 'always'
#    MODEL_IMAGE_NAME  - defaults to 'model-in-image'
#    MODEL_IMAGE_TAG   - defaults to 'v1'
#    MODEL_IMAGE_BUILD - 'when-missing' or 'always' (default)
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

cd ${WORKDIR}

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

case "$WDT_DOMAIN_TYPE" in
  WLS) 
    BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/weblogic}" ;;
  JRF|RestrictedJRF)
    BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/fmw-infrastructure}" ;;
  *) 
    echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1 ;;
esac

BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.4}
BASE_IMAGE_BUILD=${BASE_IMAGE_BUILD:-when-missing}

MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-always}

if [ ! "$MODEL_IMAGE_BUILD" = "always" ] && \
   [ "`docker images $MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG | awk '{ print $1 ":" $2 }' | grep -c $MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG`" = "1" ]; then
  echo @@
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!!                                                                                           "
  echo "@@ Info:   Skipping model image build because image '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG' already exists.   "
  echo "@@ Info:   To always build the model image, 'export MODEL_IMAGE_BUILD=always'.                             "
  echo "@@ Info: --------------------------------------------------------------------------------------------------"
  echo @@
  sleep 3
  exit 0
fi

echo @@
echo @@ Info: Obtaining model files
echo @@

MODEL_DIR=${MODEL_DIR:-$WORKDIR/models}
MODEL_YAML_FILES="${MODEL_YAML_FILES:-$(ls $MODEL_DIR/*.yaml | xargs | sed 's/ /,/g')}"
MODEL_ARCHIVE_FILES="${MODEL_ARCHIVE_FILES:-$(ls $MODEL_DIR/*.zip | xargs | sed 's/ /,/g')}"
MODEL_VARIABLE_FILES="${MODEL_VARIABLE_FILES:-$(ls $MODEL_DIR/*.properties | xargs | sed 's/ /,/g')}"

echo @@ MODEL_YAML_FILES=${MODEL_YAML_FILES}
echo @@ MODEL_ARCHIVE_FILES=${MODEL_ARCHIVE_FILES}
echo @@ MODEL_VARIABLE_FILES=${MODEL_VARIABLE_FILES}

echo @@
echo @@ Info: Setting up imagetool and populating its caches
echo @@

mkdir -p cache
unzip -o weblogic-image-tool.zip

IMGTOOL_BIN=${WORKDIR}/imagetool/bin/imagetool.sh

# The image tool uses the WLSIMG_CACHEDIR and WLSIMG_BLDIR env vars:
export WLSIMG_CACHEDIR=${WORKDIR}/cache
export WLSIMG_BLDDIR=${WORKDIR}

${IMGTOOL_BIN} cache deleteEntry --key wdt_myversion
${IMGTOOL_BIN} cache addInstaller \
  --type wdt --version myversion --path ${WORKDIR}/weblogic-deploy-tooling.zip

echo "@@"
echo "@@ Info: Starting model image build for '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG'"
echo "@@"

#
# Run the image tool to create the image. It will use the WDT binaries
# in the local image tool cache marked with key 'myversion' (see 'cache' commands above). 
#

${IMGTOOL_BIN} update \
  --tag $MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG \
  --fromImage $BASE_IMAGE_NAME:$BASE_IMAGE_TAG \
  --wdtModel ${MODEL_YAML_FILES} \
  --wdtVariables ${MODEL_VARIABLE_FILES} \
  --wdtArchive ${MODEL_ARCHIVE_FILES} \
  --wdtModelOnly \
  --wdtVersion myversion \
  --wdtDomainType ${WDT_DOMAIN_TYPE}

