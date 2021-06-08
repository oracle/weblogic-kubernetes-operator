#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 
#  Summary:
#
#    This script builds a model image using the WebLogic Image Tool. The
#    tool pulls a base image if there isn't already a local base image.
#    This script, by default, builds the model image with model files from
#    WORKDIR/MODEL_DIR using tooling downloaded by './stage-tooling.sh.'.
#
#  Optional Argument(s):
#
#    Pass '-dry' to show but not do.
#
#  Assumptions:
#
#    - The WebLogic Image Tool zip is:
#         'WORKDIR/model-images/imagetool.zip' 
#      the WebLogic Deploy Tool zip is:
#         'WORKDIR/model-images/weblogic-deploy.zip'
#      (see './stage-tooling.sh').
#
#    - Model files have been staged in the MODEL_DIR directory.
#
#  Optional environment variables:
#
#    MODEL_DIR:
#      Location relative to WORKDIR of the model .zip, .properties,
#      and .yaml files that will be copied to the model image.  Default is:
#        'model-images/model-in-image__$MODEL_IMAGE_TAG'.
#
#    ARCHIVE_SOURCEDIR:
#      Location of archive source for MODEL_DIR/archive.zip relative to WORKDIR
#      Default is "archives/archive-v1". This directory must contain a
#      'wlsdeploy' directory.
#
#    MODEL_IMAGE_NAME, MODEL_IMAGE_TAG:
#      Defaults to 'model-in-image' and 'WDT_DOMAIN_TYPE-v1'.
#
#    Others (see README)
#      WORKDIR
#      MODEL_IMAGE_BUILD
#      WDT_DOMAIN_TYPE
#      BASE_IMAGE_NAME, BASE_IMAGE_TAG
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

MODEL_YAML_FILES="$(ls $WORKDIR/$MODEL_DIR/*.yaml | xargs | sed 's/ /,/g')"
MODEL_ARCHIVE_FILES=$WORKDIR/$MODEL_DIR/archive.zip
MODEL_VARIABLE_FILES="$(ls $WORKDIR/$MODEL_DIR/*.properties | xargs | sed 's/ /,/g')"
CHOWN_ROOT="--chown oracle:root"

if [[ ${MODEL_IMAGE_TAG} == *"-CM"* ]]; then
cat << EOF
dryrun:#!/bin/bash
dryrun:# Use this script to build the common mount image '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG'
dryrun:# using the contents of '$WORKDIR/$MODEL_DIR'.
dryrun:
dryrun:set -eux
dryrun:
dryrun:rm -f $WORKDIR/$MODEL_DIR/archive.zip
dryrun:cd $WORKDIR/$ARCHIVE_SOURCEDIR
dryrun:zip -q -r $WORKDIR/$MODEL_DIR/archive.zip wlsdeploy
dryrun:
dryrun:cd "$WORKDIR"
dryrun:[ -d "cm-image/${MODEL_IMAGE_TAG}" ] && rm -rf cm-image/${MODEL_IMAGE_TAG}
dryrun:
dryrun:mkdir -p $WORKDIR/cm-image/${MODEL_IMAGE_TAG}
dryrun:cd $WORKDIR/cm-image/${MODEL_IMAGE_TAG}
dryrun:mkdir ./models
dryrun:cp $MODEL_YAML_FILES ./models
dryrun:cp $MODEL_VARIABLE_FILES ./models
dryrun:cp $WORKDIR/$MODEL_DIR/archive.zip ./models
dryrun:unzip ${WORKDIR}/model-images/weblogic-deploy.zip -d .
dryrun:rm ./weblogic-deploy/bin/*.cmd
dryrun:
dryrun:# see file $WORKDIR/cm-image/${MODEL_IMAGE_TAG}/Dockerfile for an explanation of each --build-arg
dryrun:docker build -f $WORKDIR/$COMMON_MOUNT_DOCKER_FILE_SOURCEDIR/Dockerfile \\
dryrun:             --build-arg COMMON_MOUNT_PATH=${COMMON_MOUNT_PATH} \\
dryrun:             --build-arg COMMON_MOUNT_STAGE=. \\
dryrun:             --tag ${MODEL_IMAGE_NAME}:${MODEL_IMAGE_TAG}  .
EOF
else
cat << EOF
dryrun:#!/bin/bash
dryrun:# Use this script to build image '$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG'
dryrun:# using the contents of '$WORKDIR/$MODEL_DIR'.
dryrun:
dryrun:set -eux
dryrun:
dryrun:rm -f $WORKDIR/$MODEL_DIR/archive.zip
dryrun:cd $WORKDIR/$ARCHIVE_SOURCEDIR
dryrun:zip -q -r $WORKDIR/$MODEL_DIR/archive.zip wlsdeploy
dryrun:
dryrun:cd $WORKDIR/model-images
dryrun:unzip -o imagetool.zip
dryrun:
dryrun:mkdir -p $WORKDIR/model-images/imagetool/cache
dryrun:export WLSIMG_CACHEDIR=$WORKDIR/model-images/imagetool/cache
dryrun:
dryrun:mkdir -p $WORKDIR/model-images/imagetool/bld
dryrun:export WLSIMG_BLDDIR=$WORKDIR/model-images/imagetool/bld
dryrun:
dryrun:$IMGTOOL cache deleteEntry \\
dryrun:  --key wdt_latest
dryrun:
dryrun:$IMGTOOL cache addInstaller \\
dryrun:  --type wdt \\
dryrun:  --version latest \\
dryrun:  --path ${WORKDIR}/model-images/weblogic-deploy.zip
dryrun:
dryrun:$IMGTOOL update \\
dryrun:  --tag $MODEL_IMAGE \\
dryrun:  --fromImage $BASE_IMAGE \\
dryrun:  ${MODEL_YAML_FILES:+--wdtModel ${MODEL_YAML_FILES}} \\
dryrun:  ${MODEL_VARIABLE_FILES:+--wdtVariables ${MODEL_VARIABLE_FILES}} \\
dryrun:  ${MODEL_ARCHIVE_FILES:+--wdtArchive ${MODEL_ARCHIVE_FILES}} \\
dryrun:  --wdtModelOnly \\
dryrun:  ${CHOWN_ROOT:+${CHOWN_ROOT}} \\
dryrun:  --wdtDomainType ${WDT_DOMAIN_TYPE}
dryrun:
dryrun:echo "@@ Info: Success! Model image '$MODEL_IMAGE' build complete. Seconds=\$SECONDS."

EOF
fi

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

