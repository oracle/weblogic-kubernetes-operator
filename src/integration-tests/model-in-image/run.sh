#!/bin/bash

# TBD doc/copyright
# TBD add verbose mode which doesn't put output into files
# doc new 'DRY_RUN' to
# doc that 'JRF' starts DB

set -eu
set -o pipefail
trap '[ -z "$(jobs -pr)" ] || kill $(jobs -pr)' SIGINT SIGTERM EXIT

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"
MIISAMPLEDIR="${SRCDIR}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image"
DBSAMPLEDIR="${SRCDIR}/kubernetes/samples/scripts/create-oracle-db-service"

source $TESTDIR/util-dots.sh
source $TESTDIR/util-misc.sh

trace "Running end to end MII sample test."

DRY_RUN=false
DO_CLEAN=true

while [ ! -z "${1:-}" ]; do
  case "${1}" in
    -dryrun) DRY_RUN="true" ;;
    -skipclean) DO_CLEAN="false" ;;
    *) trace "Error: Unrecognized parameter '${1}'."; exit 1; ;;
  esac
  shift
done

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

doCommand -c set -e

doCommand -c SRCDIR=$SRCDIR
doCommand -c TESTDIR=$TESTDIR
doCommand -c MIISAMPLEDIR=$MIISAMPLEDIR
doCommand -c DBSAMPLEDIR=$DBSAMPLEDIR

doCommand -c source \$TESTDIR/env.sh

# must export following as sample scripts use these values

doCommand -c export WORKDIR=$WORKDIR
doCommand -c export WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE

if [ ! "$(basename $WORKDIR)" = "model-in-image-sample-work-dir" ]; then
  # check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature for this test
  # (we're going to rm -fr a directory, and rm -fr is safer without it relying on env vars)
  trace "Error: This test requires WORKDIR to end in 'model-in-image-sample-work-dir'. WORKDIR='$WORKDIR'."
  exit 1
fi

doCommand -c mkdir -p \$WORKDIR
doCommand -c cd \$WORKDIR/..

if [ "$DO_CLEAN" = "true" ]; then
  doCommand -c rm -fr ./model-in-image-sample-work-dir
  doCommand  "\$SRCDIR/src/integration-tests/bash/cleanup.sh"
fi

if [ ! "$DRY_RUN" = "true" ]; then
  # TBD this is not multi-user safe
  trace "Cleaning dangling docker images (if any)."
  #if [ ! -z "$(docker images -f "dangling=true" -q)" ]; then
    # TBD do we need the rmi command  if we do the prunes below?
  #  docker rmi -f $(docker images -f "dangling=true" -q)
  #fi
  docker container prune -f --filter label="com.oracle.weblogic.imagetool.buildid"
  docker image prune -f --filter label="com.oracle.weblogic.imagetool.buildid"
fi

# TBD note that start-db (and maybe stop-db) seem to alter files right inside the source tree - 
#     this should be fixed to have a WORKDIR or similar, and means that they aren't suitable for multi-user/multi-ns environments
#     also, the db ideally should use a secret for its credentials - is that possible?

if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  doCommand  "\$DBSAMPLEDIR/stop-db-service.sh -n ${DB_NAMESPACE}"
  doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n ${DB_NAMESPACE} -i ${DB_IMAGE_NAME}:${DB_IMAGE_TAG} -p ${DB_NODE_PORT} -s ${DB_IMAGE_PULL_SECRET}"
fi

doCommand  "\$TESTDIR/build-wl-operator.sh" 
doCommand  "\$TESTDIR/deploy-wl-operator.sh"
doCommand  "\$TESTDIR/deploy-traefik.sh"

doCommand  "\$MIISAMPLEDIR/stage-workdir.sh"
doCommand  "\$MIISAMPLEDIR/stage-tooling.sh"
doCommand  "\$MIISAMPLEDIR/stage-model.sh"

$TESTDIR/util-model-image-md5.sh model-image-current.md5 
if [ -e "$WORKDIR/model-image-orig.md5" ] \
   && [ "$(cat $WORKDIR/model-image-orig.md5)" = "$(cat $WORKDIR/model-image-current.md5)" ]; then
  trace "Info: Skipping model image build! MD5s still the same."
else
  doCommand  -c "export MODEL_IMAGE_BUILD=always"
  doCommand  "\$MIISAMPLEDIR/build-model-image.sh"
  $TESTDIR/util-model-image-md5.sh model-image-orig.md5 
fi
rm $WORKDIR/model-image-current.md5

doCommand  "\$MIISAMPLEDIR/stage-configmap.sh"

doCommand  "\$MIISAMPLEDIR/run_domain.sh"

doCommand  "\$MIISAMPLEDIR/util-wl-pod-wait.sh 3"

trace "Woo hoo! Finished without errors!"
