#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 
#  Summary:
#
#    This script builds a model image. It pulls a base image if there isn't already
#    a local base image, and, by default, builds the model image using model files from
#    ./stage-model.sh plus tooling that was downloaded by ./stage-tooling.sh.
#
#  Assumptions:
#
#    - The WebLogic Image Tool zip is 'WORKDIR/weblogic-image-tool.zip' and the WebLogic
#      Deploy Tool zip is 'WORKDIR/weblogic-deploy-tooling.zip' (see ./stage-tooling.sh).
#    - Model files have been staged in the 'WORKDIR/model' directory (see ./stage-model.sh) or
#      MODEL_DIR has been explicitly set to point to a different location.
#
#  Optional environment variables:
#
#    WORKDIR
#      Working directory for the sample with at least 10g of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    MODEL_DIR:
#      Location of the model .zip, .properties, and .yaml files
#      that will be copied to the model image.  Default is 'WORKDIR/model'
#      which is populated by the ./stage-model.sh script.
#
#    MODEL_YAML_FILES, MODEL_ARCHIVE_FILES, MODEL_VARIABLES_FILES:
#      Optionally, set one or more of these with comma-separated lists of file
#      locations to override the corresponding .yaml, .zip, and .properties
#      files normally obtained from MODEL_DIR.
#
#    MODEL_IMAGE_BUILD:
#      Set to 'when-changed' (default) or 'always'. Default behavior is to skip
#      image build if there's an existing model image with the same specified
#      model, tooling, and base image as the previous run.
#
#    BASE_IMAGE_NAME, BASE_IMAGE_TAG:
#      The base image name defaults to 
#         'container-registry.oracle.com/middleware/weblogic'
#      for the 'WLS' domain type, and otherwise defaults to 
#         'container-registry.oracle.com/middleware/fmw-infrastructure'. 
#      The tag defaults to '12.2.1.4'.
#
#    WDT_DOMAIN_TYPE   - WLS (default), RestrictedJRF, or JRF
#    MODEL_IMAGE_NAME  - defaults to 'model-in-image'
#    MODEL_IMAGE_TAG   - defaults to 'v1'
#

set -eu

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

echo "@@ Info: WORKDIR='$WORKDIR'."

source ${WORKDIR}/env.sh

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
BASE_IMAGE="${BASE_IMAGE_NAME}:${BASE_IMAGE_TAG}"

MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
MODEL_IMAGE="${MODEL_IMAGE_NAME}:${MODEL_IMAGE_TAG}"

MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-when-changed}

echo @@
echo @@ Info: Obtaining model files
echo @@

MODEL_DIR=${MODEL_DIR:-$WORKDIR/model}
MODEL_YAML_FILES="${MODEL_YAML_FILES:-$(ls $MODEL_DIR/*.yaml | xargs | sed 's/ /,/g')}"
MODEL_ARCHIVE_FILES="${MODEL_ARCHIVE_FILES:-$(ls $MODEL_DIR/*.zip | xargs | sed 's/ /,/g')}"
MODEL_VARIABLE_FILES="${MODEL_VARIABLE_FILES:-$(ls $MODEL_DIR/*.properties | xargs | sed 's/ /,/g')}"

echo @@ MODEL_YAML_FILES=${MODEL_YAML_FILES}
echo @@ MODEL_ARCHIVE_FILES=${MODEL_ARCHIVE_FILES}
echo @@ MODEL_VARIABLE_FILES=${MODEL_VARIABLE_FILES}
echo @@
echo @@ BASE_IMAGE_NAME=${BASE_IMAGE_NAME}
echo @@ BASE_IMAGE_TAG=${BASE_IMAGE_TAG}
echo @@
echo @@ MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME}
echo @@ MODEL_IMAGE_TAG=${MODEL_IMAGE_NAME}
echo @@

#
# Exit early if there's a current model image that has the same model files, tooling,
# and base image.
#

function modelImageCksum {
  (
  set +e
  echo "This file is used by the $(basename $0) script to determine if a model image rebuild is needed. The script checks if any of the following changed since the last run:"
  md5sum weblogic-image-tool.zip
  md5sum weblogic-deploy-tooling.zip
  for file in ${MODEL_YAML_FILES} ${MODEL_ARCHIVE_FILES} ${MODEL_VARIABLE_FILES} ; do
    md5sum $file
  done
  echo "Base docker image: $BASE_IMAGE $(docker images -q $BASE_IMAGE)"
  echo "Model docker image: $MODEL_IMAGE $(docker images -q $MODEL_IMAGE)"
  exit 0
  )
}

cksum_file_orig=$WORKDIR/$(basename $0).cksum
cksum_file_tmp=${cksum_file_orig}_tmp
modelImageCksum > ${cksum_file_tmp} 2>&1

if [ ! "$MODEL_IMAGE_BUILD" = "always" ]; then
  if [ -e ${cksum_file_orig} ] && [ "$(cat ${cksum_file_tmp})" = "$(cat ${cksum_file_orig})" ]; then
    rm ${cksum_file_tmp}
    echo "@@"
    echo "@@ Info: --------------------------------------------------------------------------------------------------"
    echo "@@ Info: NOTE!!!                                                                                           "
    echo "@@ Info:   Skipping model image build because existing model files, tool files, base image, and            "
    echo "@@ Info:   model image are unchanged since the last build.                                                 "
    echo "@@ Info:   To always build the model image, 'export MODEL_IMAGE_BUILD=always'.                             "
    echo "@@ Info: --------------------------------------------------------------------------------------------------"
    echo "@@"
    echo "@@ Info: Success! Model image '$MODEL_IMAGE' build complete. Checksum for build is in 'WORKDIR/$(basename $0).cksum'."
    echo "@@"
    exit 0
  fi
fi

rm -f ${cksum_file_orig} ${cksum_file_tmp}

echo @@
echo @@ Info: Setting up imagetool and populating its caches
echo @@

mkdir -p cache
unzip -o weblogic-image-tool.zip

IMGTOOL=${WORKDIR}/imagetool/bin/imagetool.sh

# The image tool uses the WLSIMG_CACHEDIR and WLSIMG_BLDIR env vars:
export WLSIMG_CACHEDIR=${WORKDIR}/cache
export WLSIMG_BLDDIR=${WORKDIR}

set -x

${IMGTOOL} cache deleteEntry --key wdt_myversion
${IMGTOOL} cache addInstaller \
  --type wdt --version myversion --path ${WORKDIR}/weblogic-deploy-tooling.zip

set +x

echo "@@"
echo "@@ Info: Starting model image build for '$MODEL_IMAGE'"
echo "@@"

#
# Run the image tool to create the model image. It will use the WDT binaries
# in the local image tool cache marked with key 'myversion' (see 'cache' commands above).
# Note: The "${macro:+text}" syntax expands to "" if 'macro' is empty, and to 'text' if it isn't
#

# TBD test empty cases, even all three empty

set -x

${IMGTOOL} update \
  --tag $MODEL_IMAGE \
  --fromImage $BASE_IMAGE \
  ${MODEL_YAML_FILES:+--wdtModel ${MODEL_YAML_FILES}} \
  ${MODEL_VARIABLE_FILES:+--wdtVariables ${MODEL_VARIABLE_FILES}} \
  ${MODEL_ARCHIVE_FILES:+--wdtArchive ${MODEL_ARCHIVE_FILES}} \
  --wdtModelOnly \
  --wdtVersion myversion \
  --wdtDomainType ${WDT_DOMAIN_TYPE}

set +x

modelImageCksum > ${cksum_file_orig} 2>&1

echo "@@"
echo "@@ Info: Success! Model image '$MODEL_IMAGE' build complete. Checksum for build is in 'WORKDIR/$(basename $0).cksum'."
echo "@@"
