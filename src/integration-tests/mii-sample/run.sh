#!/bin/bash

# TBD doc/copyright
# TBD add verbose mode which doesn't put output into files
# doc new 'DRY_RUN' to

set -eu
set -o pipefail

trap '[ -z "$(jobs -pr)" ] || kill $(jobs -pr)' SIGINT SIGTERM EXIT

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"
MIISAMPLEDIR="${SRCDIR}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image"
DBSAMPLEDIR="${SRCDIR}/kubernetes/samples/scripts/create-oracle-db-service"

source $TESTDIR/env.sh
source $TESTDIR/util-dots.sh
source $TESTDIR/util-misc.sh

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

if [ "${1:-}" = "" ]; then
  DRY_RUN="${DRY_RUN:-}"
elif [ "${1}" = "-dryrun" ]; then
  DRY_RUN="true"
else
  trace "Error: Unrecognized parameter '${1}'."
  exit 1
fi

trace "Running end to end MII sample test."

trace "TESTDIR=$TESTDIR"
trace "SRCDIR=$SRCDIR"
trace "MIISAMPLEDIR=$MIISAMPLEDIR"
trace "DBSAMPLEDIR=$DBSAMPLEDIR"
trace "WORKDIR=$WORKDIR"

# check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature for this test
# (we're going to rm -fr a directory, and rm -fr is safer without it relying on env vars)

if [ ! "$(basename $WORKDIR)" = "model-in-image-sample-work-dir" ]; then
  trace "Error: This test requires WORKDIR to end in 'model-in-image-sample-work-dir'. WORKDIR='$WORKDIR'."
  exit 1
fi

if [ $DRY_RUN ]; then
  cat << EOF
dryrun: set -e
dryrun: TESTDIR=$TESTDIR
dryrun: SRCDIR=$SRCDIR
dryrun: MIISAMPLEDIR=$MIISAMPLEDIR
dryrun: DBSAMPLEDIR=$DBSAMPLEDIR
dryrun: WORKDIR=$WORKDIR
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
  cleanDanglingDockerImages
fi

for mycommand in \
  "\$SRCDIR/src/integration-tests/bash/cleanup.sh"                  \
  "\$DBSAMPLEDIR/stop-db-service.sh -n ${RCUDB_NAMESPACE:-default}" \
  "\$TESTDIR/build-wl-operator.sh"                                  \
  "\$TESTDIR/deploy-wl-operator.sh"                                 \
  "\$TESTDIR/deploy-traefik.sh"                                     \
  "\$MIISAMPLEDIR/stage-workdir.sh"                                 \
  "\$MIISAMPLEDIR/build.sh"                                         \
  "\$MIISAMPLEDIR/run_domain.sh"                                    \
  "\$MIISAMPLEDIR/util-wl-pod-wait.sh 3"
do
  # doCommand is a no-op if DRY_RUN is set
  doCommand $mycommand
done

trace "Woo hoo! Finished without errors!"
