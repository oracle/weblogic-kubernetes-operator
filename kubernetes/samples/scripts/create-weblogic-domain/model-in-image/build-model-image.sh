#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 
#  Summary:
#
#    This script builds a model image using the WebLogic Image Tool. The
#    tool pulls a base image if there isn't already a local base image.
#    This script, by default, builds the model image with model files from
#    './stage-model-image.sh' plus tooling downloaded by './stage-tooling.sh.'.
#
#  Assumptions:
#
#    - The WebLogic Image Tool zip is 'WORKDIR/weblogic-image-tool.zip' and
#      the WebLogic Deploy Tool zip is 'WORKDIR/weblogic-deploy-tooling.zip'
#      (see './stage-tooling.sh').
#
#    - Model files have been staged in the MODEL_DIR directory
#      (by './stage-model-image.sh' or some custom process).
#
#  Optional environment variables:
#
#    WORKDIR
#      Working directory for the sample with at least 10GB of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    MODEL_DIR:
#      Location of the model .zip, .properties, and .yaml files
#      that will be copied to the model image.  Default is:
#        'WORKDIR/models/image--$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG'
#      which is usually populated by the './stage-model-image.sh' script.
#
#    MODEL_IMAGE_BUILD:
#      Set to 'when-changed' (default) or 'always'. Default behavior is to
#      exit without building if the docker image BASE_IMAGE_NAME:BASE_IMAGE_TAG
#      is found in the local docker image cache.
#
#    WDT_DOMAIN_TYPE
#      'WLS' (default), 'RestrictedJRF', or 'JRF'.
#
#    BASE_IMAGE_NAME, BASE_IMAGE_TAG:
#      The base image name defaults to 
#         'container-registry.oracle.com/middleware/weblogic'
#      for the 'WLS' domain type, and otherwise defaults to 
#         'container-registry.oracle.com/middleware/fmw-infrastructure'. 
#      The tag defaults to '12.2.1.4'.
#
#    MODEL_IMAGE_NAME, MODEL_IMAGE_TAG:
#      Defaults to 'model-in-image' and 'v1'.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

DRY_RUN="false"
if [ "${1:-}" = "-dry" ]; then
  DRY_RUN="true"
fi

if [ ! "$WORKDIR" = "." ]; then
  if [ "$DRY_RUN" = "false" ]; then
    cd ${WORKDIR}
  else
    echo dryrun: cd ${WORKDIR}
  fi
fi

echo @@ Info: WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE}
echo @@ Info: MODEL_DIR=${MODEL_DIR}
echo @@ Info: BASE_IMAGE_NAME=${BASE_IMAGE_NAME}
echo @@ Info: BASE_IMAGE_TAG=${BASE_IMAGE_TAG}
echo @@ Info: MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME}
echo @@ Info: MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG}
echo @@ Info: MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD}

if [ "$DRY_RUN" = "false" ] \
   && [ ! "$MODEL_IMAGE_BUILD" = "always" ] \
   && [ ! -z "$(docker images -q $MODEL_IMAGE)" ]; then
  echo "@@"
  echo "@@ Info: ----------------------------------------------------------------------------"
  echo "@@ Info: NOTE!!!                                                                     "
  echo "@@ Info:   Skipping model image build because '$MODEL_IMAGE' found in docker images. "
  echo "@@ Info:   To always build the model image, 'export MODEL_IMAGE_BUILD=always'.       "
  echo "@@ Info: ----------------------------------------------------------------------------"
  echo "@@"
  exit 0
fi

if [ ! -d "$MODEL_DIR" ]; then
  echo "@@ Error: MODEL_DIR directory not found. Did you remember to stage it first?"
  exit 1
fi

MODEL_YAML_FILES="$(ls $MODEL_DIR/*.yaml | xargs | sed 's/ /,/g')"
MODEL_ARCHIVE_FILES="$(ls $MODEL_DIR/*.zip | xargs | sed 's/ /,/g')"
MODEL_VARIABLE_FILES="$(ls $MODEL_DIR/*.properties | xargs | sed 's/ /,/g')"

echo @@ Info: MODEL_YAML_FILES=${MODEL_YAML_FILES}
echo @@ Info: MODEL_ARCHIVE_FILES=${MODEL_ARCHIVE_FILES}
echo @@ Info: MODEL_VARIABLE_FILES=${MODEL_VARIABLE_FILES}

echo @@
echo @@ Info: Setting up imagetool and populating its cache with the WDT installer
echo @@

IMGTOOL=${WORKDIR}/imagetool/bin/imagetool.sh

if [ "$DRY_RUN" = "false" ]; then

mkdir -p imagetool/cache
mkdir -p imagetool/bld
unzip -o weblogic-image-tool.zip

# The image tool uses the WLSIMG_CACHEDIR and WLSIMG_BLDIR env vars:
export WLSIMG_CACHEDIR=${WORKDIR}/imagetool/cache
export WLSIMG_BLDDIR=${WORKDIR}/imagetool/bld

set -x

${IMGTOOL} cache deleteEntry --key wdt_myversion
${IMGTOOL} cache addInstaller \
  --type wdt --version myversion --path ${WORKDIR}/weblogic-deploy-tooling.zip

set +x

else

cat << EOF

dryrun:#!/bin/bash
dryrun:# Use this script to build image '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG'
dryrun:# using the contents of '$MODEL_DIR'.
dryrun:
dryrun:SCRIPTDIR="\$( cd "\$(dirname "\$0")" > /dev/null 2>&1 ; pwd -P )"
dryrun:set -e
dryrun:
dryrun:echo "@@ STEP 1"
dryrun:echo "@@ Recreate the archive were using in case its contents changed"
dryrun:

TBD copy the zip of the archive into the non-dryrun mode, and make the source directory a parameter/env-var.

dryrun:rm -f $MODEL_DIR/archive1.zip
dryrun:cd ../archives/${TARGET_ARCHIVE_OVERRIDE:-image--$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG}
dryrun:zip -q -r ../../models/$MODEL_DIR/archive1.zip wlsdeploy
dryrun:cd \$SCRIPTDIR
dryrun:
dryrun:echo "@@ STEP 2"
dryrun:echo "@@ Unzip image tool. (You can use 'stage-tooling.sh'"
dryrun:echo "@@ to retrieve the WIT and WDT zips)."
dryrun:
dryrun:unzip -o weblogic-image-tool.zip
dryrun:
dryrun:echo "@@ STEP 3"
dryrun:echo "@@ Setup image tool cache directory, it will use"
dryrun:echo "@@ the WLSIMG_CACHEDIR env var to find its cache."
dryrun:
dryrun:mkdir -p ${WORKDIR}/imagetool/cache
dryrun:export WLSIMG_CACHEDIR=${WORKDIR}/imagetool/cache
dryrun:
dryrun:echo "@@ STEP 4"
dryrun:echo "@@ Setup the image tool build directory, it will"
dryrun:echo "@@ use the WLSIMG_BLDDIR to find this directory."
dryrun:
dryrun:mkdir -p imagetool/bld
dryrun:export WLSIMG_BLDDIR=${WORKDIR}/imagetool/bld
dryrun:
dryrun:echo "@@ STEP 5"
dryrun:echo "@@ Put the location of the WDT zip installer in the "
dryrun:echo "@@ image tool cache as type 'wdt' with version "
dryrun:echo "@@ 'myversion'. When we later create the image using"
dryrun:echo "@@ the image tool, this cache entry will be referenced"
dryrun:echo "@@ using '--wdtVersion myversion'."
dryrun:
dryrun:$IMGTOOL cache deleteEntry \\
dryrun:  --key wdt_myversion
dryrun:
dryrun:$IMGTOOL cache addInstaller \\
dryrun:  --type wdt \\
dryrun:  --version myversion \\
dryrun:  --path ${WORKDIR}/weblogic-deploy-tooling.zip
dryrun:

EOF

fi

echo "@@"
echo "@@ Info: Starting model image build for '$MODEL_IMAGE'"
echo "@@"

#
# Run the image tool to create the model image. It will use the WDT binaries
# in the local image tool cache marked with key 'myversion' (see 'cache' commands above).
# Note: The "${macro:+text}" syntax expands to "" if 'macro' is empty, and to 'text' if it isn't
#

# TBD test empty cases, even all three empty


if [ "$DRY_RUN" = "false" ]; then

set -x

${IMGTOOL} update \
  --tag $MODEL_IMAGE \
  --fromImage $BASE_IMAGE \
  ${MODEL_YAML_FILES:+     --wdtModel     ${MODEL_YAML_FILES}} \
  ${MODEL_VARIABLE_FILES:+ --wdtVariables ${MODEL_VARIABLE_FILES}} \
  ${MODEL_ARCHIVE_FILES:+  --wdtArchive   ${MODEL_ARCHIVE_FILES}} \
  --wdtModelOnly \
  --wdtVersion myversion \
  --wdtDomainType ${WDT_DOMAIN_TYPE}

set +x

else

cat << EOF

dryrun:# STEP 6
dryrun:# Use the image tool to build image '$MODEL_IMAGE'.
dryrun:
dryrun:$IMGTOOL update \\
dryrun:  --tag $MODEL_IMAGE \\
dryrun:  --fromImage $BASE_IMAGE \\
dryrun:  ${MODEL_YAML_FILES:+--wdtModel ${MODEL_YAML_FILES}} \\
dryrun:  ${MODEL_VARIABLE_FILES:+--wdtVariables ${MODEL_VARIABLE_FILES}} \\
dryrun:  ${MODEL_ARCHIVE_FILES:+--wdtArchive ${MODEL_ARCHIVE_FILES}} \\
dryrun:  --wdtModelOnly \\
dryrun:  --wdtVersion myversion \\
dryrun:  --wdtDomainType ${WDT_DOMAIN_TYPE}
dryrun:

EOF

fi


echo "@@"
echo "@@ Info: Success! Model image '$MODEL_IMAGE' build complete. Seconds=$SECONDS."
echo "@@"
