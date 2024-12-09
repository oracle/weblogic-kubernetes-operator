#!/bin/bash

# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 'build-wl-operator.sh'
#
# Build and helm install an operator that monitors DOMAIN_NAMESPACE. 
# 
# This script is not necessary if the operator is already running
# and monitoring DOMAIN_NAMESPACE.
#
# This script skips the build if it finds no changes since the last build.
#
# This script always does a helm uninstall/install.

set -eu
set -o pipefail

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

cd ${SRCDIR}

echo "build Operator image"

OPER_IMAGE_TAG=${OPER_IMAGE_TAG:-test}
OPER_IMAGE_NAME=${OPER_IMAGE_NAME:-weblogic-kubernetes-operator}
OPER_JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"

echo "OPER_IMAGE_NAME=$OPER_IMAGE_NAME"
echo "OPER_IMAGE_TAG=$OPER_IMAGE_TAG"
echo "OPER_JAR_VERSION=$OPER_JAR_VERSION"

latest_cksum() {
  # force a rebuild even if only image name/tag/ver changes...
  echo "$OPER_IMAGE_NAME $OPER_IMAGE_TAG $OPER_JAR_VERSION"

  # force a rebuild if the image isn't cached anymore
  ${WLSIMG_BUILDER:-docker} images $OPER_IMAGE_NAME:$OPER_IMAGE_TAG -q

  # force a rebuild if any .java, .sh, or .py file changed
  find "$SRCDIR/operator/src/main" -name "*.[jsp]*" | xargs cat | cksum
  find "$SRCDIR/operator/src/test" -name "*.[jsp]*" | xargs cat | cksum
}

save_cksum() {
  latest_cksum > $SRCDIR/operator/src/main.cksum
}

old_cksum() {
  [ -f $SRCDIR/operator/src/main.cksum ] && cat $SRCDIR/operator/src/main.cksum
}

if [ "$(old_cksum)" = "$(latest_cksum)" ]; then
  echo "@@ Info: Skipping oper build/tag - cksum unchanged."
  exit 0
fi

mvn clean install -DskipTests -Dcheckstyle.skip
#mvn clean install
if [ -n "${http_proxy:-}" ]; then
  HTTP_BUILD_ARG="--build-arg http_proxy=$http_proxy"
fi
if [ -n "${https_proxy:-}" ]; then
  HTTP_BUILD_ARG="$HTTP_BUILD_ARG --build-arg https_proxy=$https_proxy "
fi
if [ -n "${no_proxy:-}" ]; then
  HTTP_BUILD_ARG="$HTTP_BUILD_ARG --build-arg no_proxy=$no_proxy"
fi
${WLSIMG_BUILDER:-docker} build ${HTTP_BUILD_ARG:-} -t "$OPER_IMAGE_NAME:$OPER_IMAGE_TAG"  --build-arg VERSION=$OPER_JAR_VERSION --no-cache=true .

save_cksum

# push to remote repo if cluster is remote
# if [ -z "$REPO_REGISTRY" ] || [ -z "$REPO_USERNAME" ] || [ -z "$REPO_PASSWORD" ]; then
#   echo "Provide container registry login details using REPO_REGISTRY, REPO_USERNAME & REPO_PASSWORD env variables to push the Operator image to the repository."
#   exit 1
# fi
# ${WLSIMG_BUILDER:-docker} login $REPO_REGISTRY -u $REPO_USERNAME -p $REPO_PASSWORD	
# ${WLSIMG_BUILDER:-docker} push ${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}
