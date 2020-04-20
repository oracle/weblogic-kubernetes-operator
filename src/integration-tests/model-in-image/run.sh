#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a stand-alone test for testing the MII sample.
#
# See "usage()" below for usage.

set -eu
set -o pipefail
trap '[ -z "$(jobs -pr)" ] || kill $(jobs -pr)' SIGINT SIGTERM EXIT

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

source $TESTDIR/util-dots.sh
source $TESTDIR/util-misc.sh
source $TESTDIR/env.sh

m_image="${MODEL_IMAGE_NAME:-model-in-image}:${MODEL_IMAGE_TAG:-v1}"

trace "Running end to end MII sample test."

DRY_RUN=false
DO_CLEAN=true
DO_DB=false
DO_MAIN=true
DO_UPDATE=true
WDT_DOMAIN_TYPE=WLS

function usage() {
  cat << EOF

  Usage: $(basename $0)

  Optional args:

    -dry      : Dry run - show but don't do.

    -noclean  : Do not pre-delete MII image '$m_image', call
                'cleanup.sh', or delete WORKDIR.
                (Reuses artifacts from previous run.)

    -jrf      : Run in JRF mode instead of WLS mode.
                Note: this forces DB deployment.

    -db       : Deploy Oracle DB. 

    -nomain   : Skip main test.

    -noupdate : Skip testing a runtime model update.

    -?        : This help.

EOF
}

while [ ! -z "${1:-}" ]; do
  case "${1}" in
    -dry)       DRY_RUN="true" ;;
    -noclean)   DO_CLEAN="false" ;;
    -db)        DO_DB="true" ;;
    -jrf)       WDT_DOMAIN_TYPE="JRF"; DO_DB="true"; ;;
    -nomain)    DO_MAIN="false" ;;
    -nopatch)   DO_UPDATE="false" ;;
    -?)         usage; exit 0; ;;
    *)          trace "Error: Unrecognized parameter '${1}', pass '-?' for usage."; exit 1; ;;
  esac
  shift
done

# We check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature.
# (We're going to 'rm -fr' this directory, and its safer to do that without relying on env vars).

if [ ! "$(basename $WORKDIR)" = "model-in-image-sample-work-dir" ]; then
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
    if [ ! -z "$(docker images -q $m_image)" ]; then
      trace "Info: Forcing model image rebuild by removing old docker image '$m_image'!"
      docker image rm $m_image
    fi

    # TBD cleaning dangling is not multi-user safe:

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
# Deploy initial domain, wait for its pods to be ready, and test its cluster app
#

if [ "$DO_MAIN" = "true" ]; then

  doCommand  -c "export INCLUDE_CONFIGMAP=false"
  doCommand  "\$MIISAMPLEDIR/stage-workdir.sh"
  doCommand  "\$MIISAMPLEDIR/stage-tooling.sh"
  doCommand  "\$MIISAMPLEDIR/stage-model-image.sh"
  doCommand  "\$MIISAMPLEDIR/build-model-image.sh"
  doCommand  "\$MIISAMPLEDIR/stage-domain-resource.sh"
  doCommand  "\$MIISAMPLEDIR/create-secrets.sh"
  doCommand  "\$MIISAMPLEDIR/create-domain-resource.sh -predelete"
  doCommand  -c "\$MIISAMPLEDIR/util-wl-pod-wait.sh -p 3"

  # Cheat to speedup a subsequent roll/shutdown.
  diefast

  # TBD If JRF, TBD export wallet to $WORKDIR/somefile

  [ ! "$DRY_RUN" = "true" ] && testapp internal "Hello World!"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  "Hello World!"

fi




#
# Add datasource to the running domain, patch its
# restart version, wait for its pods to roll, and use
# the test app to verify that the datasource deployed.
#

if [ "$DO_UPDATE" = "true" ]; then

  # TBD If JRF, import wallet to wallet secret - is that a sufficient test? Or do we need to restart domain?

  doCommand  -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand  "\$MIISAMPLEDIR/stage-domain-resource.sh"
  doCommand  "\$MIISAMPLEDIR/stage-model-configmap.sh"
  doCommand  "\$MIISAMPLEDIR/create-secrets.sh"
  doCommand  "\$MIISAMPLEDIR/create-model-configmap.sh"
  doCommand  "\$MIISAMPLEDIR/create-domain-resource.sh"
  doCommand  "\$MIISAMPLEDIR/util-patch-restart-version.sh"
  doCommand  -c "\$MIISAMPLEDIR/util-wl-pod-wait.sh -p 3"

  # Cheat to speedup a subsequent roll/shutdown.
  diefast

  [ ! "$DRY_RUN" = "true" ] && testapp internal "mynewdatasource"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  "mynewdatasource"

fi

trace "Woo hoo! Finished without errors! Total runtime $SECONDS seconds."
