#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#  This script uses the WebLogic Image Tool to build an image with model in image
#  artifacts. 
#
#  Assumptions:
#
#    WORKDIR
#      Working directory. Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    MODEL_DIR:
#      Location of the model .zip, .properties, and .yaml files
#      that will be copied in to the image.  Default is 'WORKDIR/models'
#      which is populated by the ./build_model.sh script.
#
#    MODEL_YAML_FILES, MODEL_ARCHIVE_FILES, MODEL_VARIABLES_FILES:
#      Optionally, set one or more of these with comma-separated lists of file
#      locations to override the corresponding .yaml, .zip, and .properties
#      files normally obtained from MODEL_DIR.
#
#    WDT_DOMAIN_TYPE   - WLS (default), RestrictedJRF, or JRF
#    BASE_IMAGE_NAME   - defaults to container-registry.oracle.com/middleware/weblogic for
#                        the 'WLS' domain type, and otherwise defaults to
#                        container-registry.oracle.com/middleware/fmw-infrastructure
#    BASE_IMAGE_TAG    - defaults to 12.2.1.4
#    MODEL_IMAGE_NAME  - defaults to 'model-in-image'
#    MODEL_IMAGE_TAG   - defaults to 'v1'
#    MODEL_IMAGE_BUILD - 'when-missing' or 'always' (default)
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

echo "@@ Info: WORKDIR='$WORKDIR'."

mkdir -p ${WORKDIR}
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
