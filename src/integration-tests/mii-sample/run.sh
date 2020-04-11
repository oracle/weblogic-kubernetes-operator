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

if [ "${1:-}" = "" ]; then
  DRY_RUN="${DRY_RUN:-}"
elif [ "${1}" = "-dryrun" ]; then
  DRY_RUN="true"
else
  trace "Error: Unrecognized parameter '${1}'."
  exit 1
fi

# these env vars are directly used by the sample scripts

export WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
export WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

# these env vars for the db, and are only used if WDT_DOMAIN_TYPE is JRF

if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  DB_NAMESPACE=${DB_NAMESPACE:-default}
  DB_NODE_PORT=${DB_NODE_PORT:-30011}
  DB_IMAGE_NAME=${DB_IMAGE_NAME:-container-registry.oracle.com/database/enterprise}
  DB_IMAGE_TAG=${DB_IMAGE_TAG:-12.2.0.1-slim}
  DB_IMAGE_PULL_SECRET=${DB_IMAGE_PULL_SECRET:-docker-store}
fi

# load customized env vars for this test, if any

source $TESTDIR/env.sh

# check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature for this test
# (we're going to rm -fr a directory, and rm -fr is safer without it relying on env vars)

if [ ! "$(basename $WORKDIR)" = "model-in-image-sample-work-dir" ]; then
  trace "Error: This test requires WORKDIR to end in 'model-in-image-sample-work-dir'. WORKDIR='$WORKDIR'."
  exit 1
fi

trace "TESTDIR=$TESTDIR"
trace "SRCDIR=$SRCDIR"
trace "MIISAMPLEDIR=$MIISAMPLEDIR"
trace "WORKDIR=$WORKDIR"
trace "WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE"

if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  trace "DBSAMPLEDIR=$DBSAMPLEDIR"
  trace "DB_NAMESPACE=$DB_NAMESPACE"
  trace "DB_NODE_PORT=$DB_NODE_PORT"
  trace "DB_IMAGE_NAME=$DB_IMAGE_NAME"
  trace "DB_IMAGE_TAG=$DB_IMAGE_TAG"
  trace "DB_IMAGE_PULL_SECRET=$DB_IMAGE_PULL_SECRET"
fi

if [ $DRY_RUN ]; then
  cat << EOF
dryrun: set -e
dryrun: TESTDIR=$TESTDIR
dryrun: SRCDIR=$SRCDIR
dryrun: MIISAMPLEDIR=$MIISAMPLEDIR
dryrun: DBSAMPLEDIR=$DBSAMPLEDIR
dryrun: export WORKDIR=$WORKDIR
dryrun: export WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE
dryrun: source \$TESTDIR/env.sh
dryrun: mkdir -p \$WORKDIR
dryrun: cd \$WORKDIR/..
dryrun: rm -fr ./model-in-image-sample-work-dir
EOF
else
  mkdir -p $WORKDIR
  cd $WORKDIR/..
  rm -fr ./model-in-image-sample-work-dir
  mkdir -p $WORKDIR/command_out
  cleanDanglingDockerImages # TBD is this multi-user safe?
fi

# TBD note that start-db (and maybe stop-db) seem to alter files right inside the source tree - 
#     this should be fixed to have a WORKDIR or similar, and means that they aren't suitable for multi-user/multi-ns environments
#     also, the db ideally should use a secret for its credentials - is that possible?


doCommand  "\$SRCDIR/src/integration-tests/bash/cleanup.sh"

if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  doCommand  "\$DBSAMPLEDIR/stop-db-service.sh -n ${DB_NAMESPACE}"
  doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n ${DB_NAMESPACE} -i ${DB_IMAGE_NAME}:${DB_IMAGE_TAG} -p ${DB_NODE_PORT} -s ${DB_IMAGE_PULL_SECRET}"
fi

doCommand  "\$TESTDIR/build-wl-operator.sh" 
doCommand  "\$TESTDIR/deploy-wl-operator.sh"
doCommand  "\$TESTDIR/deploy-traefik.sh"
doCommand  "\$MIISAMPLEDIR/stage-workdir.sh"
doCommand  "\$MIISAMPLEDIR/build.sh"
doCommand  "\$MIISAMPLEDIR/run_domain.sh"
doCommand  "\$MIISAMPLEDIR/util-wl-pod-wait.sh 3"

trace "Woo hoo! Finished without errors!"
