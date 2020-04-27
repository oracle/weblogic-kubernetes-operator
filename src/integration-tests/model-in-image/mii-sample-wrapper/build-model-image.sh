#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 
#  Summary:
#
#    This script builds a model image using the WebLogic Image Tool. The
#    tool pulls a base image if there isn't already a local base image.
#    This script, by default, builds the model image with model files from
#    MODEL_DIR using tooling downloaded by './stage-tooling.sh.'.
#
#  Optional Argument(s):
#
#    Pass '-dry' to show but not do.
#
#  Assumptions:
#
#    - The WebLogic Image Tool zip is:
#         'WORKDIR/model-images/weblogic-image-tool.zip' 
#      the WebLogic Deploy Tool zip is:
#         'WORKDIR/model-images/weblogic-deploy-tooling.zip'
#      (see './stage-tooling.sh').
#
#    - Model files have been staged in the MODEL_DIR directory.
#
#  Optional environment variables:
#
#    WORKDIR
#      Working directory for the sample with at least 10GB of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    MODEL_DIR:
#      Location relative to WORKDIR of the model .zip, .properties,
#      and .yaml files that will be copied to the model image.  Default is:
#        'model-images/$(basename $MODEL_IMAGE_NAME):$MODEL_IMAGE_TAG'.
#
#    ARCHIVE_SOURCEDIR:
#      Location of archive source for MODEL_DIR/archive.zip relative to WORKDIR
#      Default is "archives/archive-v1". This directory must contain a
#      'wlsdeploy' directory.
#
#    MODEL_IMAGE_BUILD:
#      Set to 'when-changed' or 'always' (default). 'when-changed' behavior is to
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
#      Defaults to 'model-in-image' and 'WDT_DOMAIN_TYPE-v1'.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

DRY_RUN="false"
if [ "${1:-}" = "-dry" ]; then
  DRY_RUN="true"
fi

echo @@ Info: WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE}
echo @@ Info: MODEL_DIR=${MODEL_DIR}
echo @@ Info: BASE_IMAGE_NAME=${BASE_IMAGE_NAME}
echo @@ Info: BASE_IMAGE_TAG=${BASE_IMAGE_TAG}
echo @@ Info: MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME}
echo @@ Info: MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG}
echo @@ Info: MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD}

IMGTOOL=$WORKDIR/model-images/imagetool/bin/imagetool.sh

function output_dryrun() {

cat << EOF

dryrun:#!/bin/bash
dryrun:# Use this script to build image '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG'
dryrun:# using the contents of '$MODEL_DIR'.
dryrun:
dryrun:set -eu
dryrun:
dryrun:echo "@@ STEP 1"
dryrun:echo "@@ Recreate the archive zip in case the source changed"
dryrun:
dryrun:rm -f $WORKDIR/$MODEL_DIR/archive.zip
dryrun:cd $WORKDIR/$ARCHIVE_SOURCEDIR
dryrun:zip -q -r $WORKDIR/$MODEL_DIR/archive.zip wlsdeploy
dryrun:
dryrun:echo "@@ STEP 2"
dryrun:echo "@@ Locate the model files we will put in the image"
dryrun:
dryrun:cd $WORKDIR/$(dirname $MODEL_DIR)
dryrun:MODEL_YAML_FILES="\$(ls $WORKDIR/$MODEL_DIR/*.yaml | xargs | sed 's/ /,/g')"
dryrun:MODEL_ARCHIVE_FILES="\$(ls $WORKDIR/$MODEL_DIR/*.zip | xargs | sed 's/ /,/g')"
dryrun:MODEL_VARIABLE_FILES="\$(ls $WORKDIR/$MODEL_DIR/*.properties | xargs | sed 's/ /,/g')"
dryrun:echo @@ Info: MODEL_YAML_FILES=\${MODEL_YAML_FILES}
dryrun:echo @@ Info: MODEL_ARCHIVE_FILES=\${MODEL_ARCHIVE_FILES}
dryrun:echo @@ Info: MODEL_VARIABLE_FILES=\${MODEL_VARIABLE_FILES}
dryrun:
dryrun:echo "@@ STEP 2"
dryrun:echo "@@ Unzip image tool. (You can use 'stage-tooling.sh'"
dryrun:echo "@@ to retrieve the WIT and WDT zips)."
dryrun:
dryrun:cd $WORKDIR/model-images
dryrun:unzip -o weblogic-image-tool.zip
dryrun:
dryrun:echo "@@ STEP 3"
dryrun:echo "@@ Setup image tool cache directory, it will use"
dryrun:echo "@@ the WLSIMG_CACHEDIR env var to find its cache."
dryrun:
dryrun:mkdir -p $WORKDIR/model-images/imagetool/cache
dryrun:export WLSIMG_CACHEDIR=$WORKDIR/model-images/imagetool/cache
dryrun:
dryrun:echo "@@ STEP 4"
dryrun:echo "@@ Setup the image tool build directory, it will"
dryrun:echo "@@ use the WLSIMG_BLDDIR to find this directory."
dryrun:
dryrun:mkdir -p $WORKDIR/model-images/imagetool/bld
dryrun:export WLSIMG_BLDDIR=$WORKDIR/model-images/imagetool/bld
dryrun:
dryrun:echo "@@ STEP 5"
dryrun:echo "@@ Put the location of the WDT zip installer in the "
dryrun:echo "@@ image tool cache as type 'wdt' with version "
dryrun:echo "@@ 'latestversion'. When we later create the image using"
dryrun:echo "@@ the image tool, this cache entry will be referenced"
dryrun:echo "@@ using '--wdtVersion latestversion'."
dryrun:
dryrun:$IMGTOOL cache deleteEntry \\
dryrun:  --key wdt_latestversion
dryrun:
dryrun:$IMGTOOL cache addInstaller \\
dryrun:  --type wdt \\
dryrun:  --version latestversion \\
dryrun:  --path ${WORKDIR}/model-images/weblogic-deploy-tooling.zip
dryrun:
dryrun:echo "@@ STEP 6"
dryrun:echo "@@ Use the image tool to build image '$MODEL_IMAGE'"
dryrun:
dryrun:set -x
dryrun:$IMGTOOL update \\
dryrun:  --tag $MODEL_IMAGE \\
dryrun:  --fromImage $BASE_IMAGE \\
dryrun:  \${MODEL_YAML_FILES:+--wdtModel \${MODEL_YAML_FILES}} \\
dryrun:  \${MODEL_VARIABLE_FILES:+--wdtVariables \${MODEL_VARIABLE_FILES}} \\
dryrun:  \${MODEL_ARCHIVE_FILES:+--wdtArchive \${MODEL_ARCHIVE_FILES}} \\
dryrun:  --wdtModelOnly \\
dryrun:  --wdtVersion latestversion \\
dryrun:  --wdtDomainType ${WDT_DOMAIN_TYPE}
dryrun:set +x
dryrun:
dryrum:echo "@@"
dryrun:echo "@@ Info: Success! Model image '$MODEL_IMAGE' build complete. Seconds=$SECONDS."
dryrun:echo "@@"

EOF

} # end of function output_dryrun()

if [ "$DRY_RUN" = "true" ]; then

  output_dryrun
  exit 0 # done with dry run

else

  # we're not dry running

  if [ ! "$MODEL_IMAGE_BUILD" = "always" ] && [ ! -z "$(docker images -q $MODEL_IMAGE)" ]; then
    echo "@@"
    echo "@@ Info: ----------------------------------------------------------------------------"
    echo "@@ Info: NOTE!!!                                                                     "
    echo "@@ Info:   Skipping model image build because '$MODEL_IMAGE' found in docker images. "
    echo "@@ Info:   To always build the model image, 'export MODEL_IMAGE_BUILD=always'.       "
    echo "@@ Info: ----------------------------------------------------------------------------"
    echo "@@"
    exit 0
  fi

  if [ ! -d "$WORKDIR/$MODEL_DIR" ]; then
    echo "@@ Error: MODEL_DIR directory not found. Did you remember to stage it first?"
    exit 1
  fi

  CURPID=$(bash -c "echo \$PPID")

  tmpfil="$WORKDIR/$(basename $0).$CURPID.$PPID.$SECONDS.$RANDOM.sh"

  output_dryrun | grep "^dryrun:" | sed 's/^dryrun://' > $tmpfil

  chmod +x $tmpfil

  echo "@@ Info: About to run '$tmpfil'."

  $tmpfil

  echo "@@ Info: About to remove '$tmpfil'."

  rm $tmpfil

  echo "@@ Info: Done!"

fi

