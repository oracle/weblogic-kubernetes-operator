#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# TBD add doc here
# TBD add a usage
#
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
source $TESTDIR/env.sh

trace "Running end to end MII sample test."

DRY_RUN=false
DO_CLEAN=true
DO_DB=false
WDT_DOMAIN_TYPE=WLS

while [ ! -z "${1:-}" ]; do
  case "${1}" in
    -dry)       DRY_RUN="true" ;;
    -noclean)   DO_CLEAN="false" ;;
    -db)        DO_DB="true" ;;
    -jrf)       WDT_DOMAIN_TYPE="JRF"; DO_DB="true"; ;;
    *)          trace "Error: Unrecognized parameter '${1}'."; exit 1; ;;
  esac
  shift
done


if [ ! "$(basename $WORKDIR)" = "model-in-image-sample-work-dir" ]; then
  # check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature for this test
  # (we're going to rm -fr a directory, and rm -fr is safer without it relying on env vars)
  trace "Error: This test requires WORKDIR to end in 'model-in-image-sample-work-dir'. WORKDIR='$WORKDIR'."
  exit 1
fi

# 
# Clean
#

if [ "$DO_CLEAN" = "true" ]; then
  doCommand -c mkdir -p \$WORKDIR
  doCommand -c cd \$WORKDIR/..
  doCommand -c rm -fr ./model-in-image-sample-work-dir
  doCommand  "\$SRCDIR/src/integration-tests/bash/cleanup.sh"

  # delete model image, if any, and dangling images
  if [ ! "$DRY_RUN" = "true" ]; then
    m_image="${MODEL_IMAGE_NAME:-model-in-image}:${MODEL_IMAGE_TAG:-v1}"
    if [ ! -z "$(docker ls $m_image)"; then
      trace "Info: Forcing model image rebuild by removing old docker image '$m_image'!"
      docker image rm $m_image
    fi

    # TBD this is not multi-user safe
    trace "Cleaning dangling docker images (if any)."
    #if [ ! -z "$(docker images -f "dangling=true" -q)" ]; then
      # TBD do we need the rmi command  if we do the prunes below?
    #  docker rmi -f $(docker images -f "dangling=true" -q)
    #fi
    docker container prune -f --filter label="com.oracle.weblogic.imagetool.buildid"
    docker image prune -f --filter label="com.oracle.weblogic.imagetool.buildid"
  fi
fi

# 
# Env var pre-reqs
#

doCommand -c set -e
doCommand -c SRCDIR=$SRCDIR
doCommand -c TESTDIR=$TESTDIR
doCommand -c MIISAMPLEDIR=$MIISAMPLEDIR
doCommand -c DBSAMPLEDIR=$DBSAMPLEDIR
doCommand -c source \$TESTDIR/env.sh
doCommand -c export WORKDIR=$WORKDIR
doCommand -c export WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE

#
# Build pre-req (operator)
#

doCommand  "\$TESTDIR/build-wl-operator.sh" 

#
# Deploy pre-reqs (db, traefik, operator)
#

if [ "$DO_DB" = "true" ]; then
  # TBD note that start-db (and maybe stop-db) seem to alter files right inside the source tree - 
  #     this should be fixed to have a WORKDIR or similar, and means that they aren't suitable for multi-user/multi-ns environments
  #TBD add support for DB_IMAGE_PULL_SECRET
  doCommand  "\$DBSAMPLEDIR/stop-db-service.sh -n \$DB_NAMESPACE"
  doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n \$DB_NAMESPACE -i \$DB_IMAGE_NAME:\$DB_IMAGE_TAG -p \$DB_NODE_PORT -s \$DB_IMAGE_PULL_SECRET"
fi

doCommand  "\$TESTDIR/deploy-wl-operator.sh"
doCommand  "\$TESTDIR/deploy-traefik.sh"

#
# Deploy initial domain
#

doCommand  -c "export INCLUDE_CONFIGMAP=false"
doCommand  "\$MIISAMPLEDIR/stage-workdir.sh"
doCommand  "\$MIISAMPLEDIR/stage-tooling.sh"
doCommand  "\$MIISAMPLEDIR/stage-model-image.sh"
doCommand  "\$MIISAMPLEDIR/build-model-image.sh"
doCommand  "\$MIISAMPLEDIR/stage-domain-resource.sh"
doCommand  "\$MIISAMPLEDIR/create-secrets.sh"
doCommand  "\$MIISAMPLEDIR/create-domain-resource.sh -predelete"
doCommand  -c "\$MIISAMPLEDIR/util-wl-pod-wait.sh -p 3"

#
# Add datasource to the running domain
#

doCommand  -c "export INCLUDE_CONFIGMAP=true"
doCommand  "\$MIISAMPLEDIR/stage-domain-resource.sh"
doCommand  "\$MIISAMPLEDIR/stage-model-configmap.sh"
doCommand  "\$MIISAMPLEDIR/create-secrets.sh"
doCommand  "\$MIISAMPLEDIR/create-model-configmap.sh"
doCommand  "\$MIISAMPLEDIR/create-domain-resource.sh"
doCommand  "\$MIISAMPLEDIR/util-patch-restart-version.sh"
doCommand  -c "\$MIISAMPLEDIR/util-wl-pod-wait.sh -p 3"

trace "Woo hoo! Finished without errors!"
