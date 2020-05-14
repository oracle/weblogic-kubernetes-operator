#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a stand-alone test for testing the MII sample.
#
# See "usage()" below for usage.

# TBD Verify again that this works for JRF and RestrictedJRF.

set -eu
set -o pipefail
trap '[ -z "$(jobs -pr)" ] || kill $(jobs -pr)' SIGINT SIGTERM EXIT

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

source $TESTDIR/util-dots.sh
source $TESTDIR/util-misc.sh
source $TESTDIR/test-env.sh

trace "Running end to end MII sample test."

DRY_RUN=false
DO_CLEAN=false
DO_DB=false
DO_OPER=false
DO_TRAEFIK=false
DO_INITIAL=false
DO_UPDATE1=false
DO_UPDATE2=false
DO_UPDATE3=false
WDT_DOMAIN_TYPE=WLS

function usage() {
  cat << EOF

  Usage: $(basename $0)

  Optional args:

    -jrf      : Run in JRF mode instead of WLS mode. 
                Note that this depends on the db
                being deployed. So either pre-deploy
                the db or pass '-db' too.

    -dry      : Dry run - show but don't do.

    -clean    : Call cleanup.sh, pre-delete MII image
                'MODEL_IMAGE_NAME:*', and delete WORKDIR.
                (Reuses artifacts from previous run, if any.)
                IMPORTANT: This implicitly enables '-oper' and '-traefik'.

    -db       : Deploy Oracle DB. 

    -oper     : Build and deploy Operator. This
                operator will monitor 'DOMAIN_NAMESPACE'
                which defaults to 'sample-domain1-ns'.

    -traefik  : Deploy Traefik. This will monitor
                'DOMAIN_NAMESPACE' which defaults 
                to 'sample-domain1-ns'.

    -initial  : Run initial use case.

    -update1  : Run update1 use case (add data source via configmap).

    -update2  : Run update2 use case (deploy second domain).

    -update3  : Run update3 use case (update first domain's app with new image).

    -?        : This help.

  Optional env var:
    
    Set "DOMAIN_NAMESPACE" prior to running (default sample-domain1-ns).

EOF
}

while [ ! -z "${1:-}" ]; do
  case "${1}" in
    -dry)       DRY_RUN="true" ;;
    -clean)     DO_CLEAN="true" 
                DO_OPER="true"
                DO_TRAEFIK="true"
                ;;
    -db)        DO_DB="true" ;;
    -oper)      DO_OPER="true" ;;
    -traefik)   DO_TRAEFIK="true" ;;
    -jrf)       WDT_DOMAIN_TYPE="JRF"; ;;
    -initial)   DO_INITIAL="true" ;;
    -update1)   DO_UPDATE1="true" ;;
    -update2)   DO_UPDATE2="true" ;;
    -update3)   DO_UPDATE3="true" ;;
    -?)         usage; exit 0; ;;
    *)          trace "Error: Unrecognized parameter '${1}', pass '-?' for usage."; exit 1; ;;
  esac
  shift
done

# We check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature.
# (We're going to 'rm -fr' this directory, and its safer to do that without relying on env vars).

bname=$(basename $WORKDIR)
if [ ! "$bname" = "model-in-image-sample-work-dir" ] \
   && [ ! "$bname" = "mii-sample" ] ; then
  trace "Error: This test requires WORKDIR to end in 'mii-sample' or 'model-in-image-sample-work-dir'. WORKDIR='$WORKDIR'."
  exit 1
fi

# 
# Clean
#

if [ "$DO_CLEAN" = "true" ]; then
  doCommand -c mkdir -p \$WORKDIR
  doCommand -c cd \$WORKDIR/..
  if [ "$bname" = "mii-sample" ]; then
    doCommand -c rm -fr ./mii-sample
  else
    doCommand -c rm -fr ./model-in-image-sample-work-dir
  fi
  doCommand  "\$SRCDIR/src/integration-tests/bash/cleanup.sh"
  doCommand -c mkdir -p \$WORKDIR
  doCommand -c cp -r \$MIISAMPLEDIR/* \$WORKDIR

  # delete model images, if any, and dangling images
  for m_image in \
    "${MODEL_IMAGE_NAME:-model-in-image}:${WDT_DOMAIN_TYPE}-v1" \
    "${MODEL_IMAGE_NAME:-model-in-image}:${WDT_DOMAIN_TYPE}-v2" 
  do
    if [ ! "$DRY_RUN" = "true" ]; then
      if [ ! -z "$(docker images -q $m_image)" ]; then
        trace "Info: Forcing model image rebuild by removing old docker image '$m_image'!"
        docker image rm $m_image
      fi
    else
      echo "dryrun: [ ! -z "$(docker images -q $m_image)" ] && docker image rm $m_image"
    fi
  done
fi

# 
# Env var pre-reqs
#

doCommand -c set -e
doCommand -c SRCDIR=$SRCDIR
doCommand -c TESTDIR=$TESTDIR
doCommand -c MIISAMPLEDIR=$MIISAMPLEDIR
doCommand -c MIIWRAPPERDIR=$MIIWRAPPERDIR
doCommand -c DBSAMPLEDIR=$DBSAMPLEDIR
doCommand -c source \$TESTDIR/test-env.sh
doCommand -c export WORKDIR=$WORKDIR
doCommand -c export WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE
doCommand -c export DOMAIN_NAMESPACE=$DOMAIN_NAMESPACE

#
# Build pre-req (operator)
#

if [ "$DO_OPER" = "true" ]; then
  doCommand  "\$TESTDIR/build-operator.sh" 
fi

#
# Deploy pre-reqs (db, traefik, operator)
#

if [ "$DO_DB" = "true" ]; then
  # TBD note that start-db (and maybe stop-db) seem to alter files right inside the source tree - 
  #     this should be fixed to have a WORKDIR or similar, and means that they aren't suitable for multi-user/multi-ns environments
  doCommand  "\$DBSAMPLEDIR/stop-db-service.sh -n \$DB_NAMESPACE"
  if [ ! -z "$DB_IMAGE_PULL_SECRET" ]; then
    doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n \$DB_NAMESPACE -i \$DB_IMAGE_NAME:\$DB_IMAGE_TAG -p \$DB_NODE_PORT -s \$DB_IMAGE_PULL_SECRET"
  else
    doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n \$DB_NAMESPACE -i \$DB_IMAGE_NAME:\$DB_IMAGE_TAG -p \$DB_NODE_PORT"
  fi
fi

if [ "$DO_OPER" = "true" ]; then
  doCommand  "\$TESTDIR/deploy-operator.sh"
fi

if [ "$DO_TRAEFIK" = "true" ]; then
  doCommand  "\$TESTDIR/deploy-traefik.sh"
fi

#
# Deploy initial domain, wait for its pods to be ready, and test its cluster app
#


if [ "$DO_INITIAL" = "true" ]; then
  wait_parms="-d $DOMAIN_UID1 -n $DOMAIN_NAMESPACE"

  doCommand  -c export DOMAIN_UID=$DOMAIN_UID1
  doCommand  -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-initial.yaml"
  doCommand  -c "export INCLUDE_CONFIGMAP=false"
  doCommand  "\$MIIWRAPPERDIR/stage-tooling.sh"
  doCommand  "\$MIIWRAPPERDIR/build-model-image.sh"
  doCommand  "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand  "\$MIIWRAPPERDIR/create-secrets.sh"
  doCommand  "\$MIIWRAPPERDIR/stage-and-create-ingresses.sh"
  doCommand  "\$MIIWRAPPERDIR/create-domain-resource.sh -predelete"

  doCommand  "\$WORKDIR/utils/wl-pod-wait.sh -p 3 $wait_parms"

  # Cheat to speedup a subsequent roll/shutdown.
  [ ! "$DRY_RUN" = "true" ] && diefast

  [ ! "$DRY_RUN" = "true" ] && testapp internal cluster-1 "Hello World!"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  cluster-1 "Hello World!"

  # TBD add JRF specific testing
  # if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  #   export wallet
  #   import wallet to wallet secret 
  #   set env var to tell creat-domain-resource to uncomment wallet secret
  #   doCommand  "\$MIIWRAPPERDIR/create-domain-resource.sh -predelete"
  #   doCommand  -c "\$WORKDIR/utils/wl-pod-wait.sh -p 3 $wait_parms -q"
  # fi
  # Cheat to speedup a subsequent roll/shutdown.
  # diefast

fi


#
# Add datasource to the running domain, patch its
# restart version, wait for its pods to roll, and use
# the test app to verify that the datasource deployed.
#

if [ "$DO_UPDATE1" = "true" ]; then
  wait_parms="-d $DOMAIN_UID1 -n $DOMAIN_NAMESPACE"

  # JRF specific testing
  # if [ "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  #   import wallet to wallet secret again
  #   set env var to tell creat-domain-resource to uncomment wallet secret
  # fi

  doCommand  -c export DOMAIN_UID=$DOMAIN_UID1
  doCommand  -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-update1.yaml"
  doCommand  -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand  "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand  "\$MIIWRAPPERDIR/create-secrets.sh"
  doCommand  "\$MIIWRAPPERDIR/create-model-configmap.sh"
  doCommand  "\$MIIWRAPPERDIR/create-domain-resource.sh"
  doCommand  "\$WORKDIR/utils/patch-restart-version.sh $wait_parms"

  doCommand  "\$WORKDIR/utils/wl-pod-wait.sh -p 3 $wait_parms"

  # Cheat to speedup a subsequent roll/shutdown.
  [ ! "$DRY_RUN" = "true" ] && diefast

  [ ! "$DRY_RUN" = "true" ] && testapp internal cluster-1 "mynewdatasource"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  cluster-1 "mynewdatasource"

fi

#
# Deploy a second domain to the same ns similar to the
# update1 ns, wait for it to start, and use the test
# app to verify its up. Also verify that the original
# domain is responding to its calls.
#

if [ "$DO_UPDATE2" = "true" ]; then
  wait_parms="-d $DOMAIN_UID2 -n $DOMAIN_NAMESPACE"

  # JRF specific testing?

  doCommand -c export DOMAIN_UID=$DOMAIN_UID2
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-update2.yaml"
  doCommand -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand -c export CUSTOM_DOMAIN_NAME=domain2
  doCommand  "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand  "\$MIIWRAPPERDIR/create-secrets.sh"
  doCommand  "\$MIIWRAPPERDIR/stage-and-create-ingresses.sh"
  doCommand  "\$MIIWRAPPERDIR/create-model-configmap.sh"
  doCommand  "\$MIIWRAPPERDIR/create-domain-resource.sh"

  doCommand  "\$WORKDIR/utils/wl-pod-wait.sh -p 3 $wait_parms"

  # Cheat to speedup a subsequent roll/shutdown.
  [ ! "$DRY_RUN" = "true" ] && diefast

  [ ! "$DRY_RUN" = "true" ] && testapp internal cluster-1 "name....domain2"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  cluster-1 "name....domain2"

  doCommand -c export DOMAIN_UID=$DOMAIN_UID1

  [ ! "$DRY_RUN" = "true" ] && testapp internal cluster-1 "name....domain1"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  cluster-1 "name....domain1"
fi

#
# Deploy an updated application to the first domain
# using an updated image, wait for it to roll, and 
# test the app to verify the update took effect.
#

if [ "$DO_UPDATE3" = "true" ]; then
  wait_parms="-d $DOMAIN_UID1 -n $DOMAIN_NAMESPACE"

  # JRF specific testing?

  doCommand -c export DOMAIN_UID=$DOMAIN_UID1
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-update3.yaml"
  doCommand -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand -c export CUSTOM_DOMAIN_NAME=domain1
  doCommand -c export MODEL_IMAGE_TAG=${WDT_DOMAIN_TYPE}-v2
  doCommand -c export ARCHIVE_SOURCEDIR=archives/archive-v2

  doCommand  "\$MIIWRAPPERDIR/build-model-image.sh"
  doCommand  "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand  "\$MIIWRAPPERDIR/create-domain-resource.sh"

  doCommand  "\$WORKDIR/utils/wl-pod-wait.sh -p 3 $wait_parms"

  # Cheat to speedup a subsequent roll/shutdown.
  [ ! "$DRY_RUN" = "true" ] && diefast

  [ ! "$DRY_RUN" = "true" ] && testapp internal cluster-1 "v2"
  [ ! "$DRY_RUN" = "true" ] && testapp traefik  cluster-1 "v2"
fi

trace "Woo hoo! Finished without errors! Total runtime $SECONDS seconds."
