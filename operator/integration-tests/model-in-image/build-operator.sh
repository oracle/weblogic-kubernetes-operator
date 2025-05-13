#!/bin/bash

# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
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
cd "${SRCDIR}"

echo "Building Operator image..."

# Environment and defaults
OPER_IMAGE_TAG=${OPER_IMAGE_TAG:-test}
OPER_IMAGE_NAME=${OPER_IMAGE_NAME:-weblogic-kubernetes-operator}
OPER_JAR_VERSION="$(grep -m1 '<version>' pom.xml | cut -f2 -d'>' | cut -f1 -d'<')"

echo "OPER_IMAGE_NAME=${OPER_IMAGE_NAME}"
echo "OPER_IMAGE_TAG=${OPER_IMAGE_TAG}"
echo "OPER_JAR_VERSION=${OPER_JAR_VERSION}"

# Generate checksum based on content
latest_cksum() {
  # force a rebuild even if only image name/tag/ver changes...
  echo "$OPER_IMAGE_NAME $OPER_IMAGE_TAG $OPER_JAR_VERSION"

  # force a rebuild if the image isn't cached anymore
  ${WLSIMG_BUILDER:-docker} images "$OPER_IMAGE_NAME:$OPER_IMAGE_TAG" -q || true
  find "$SRCDIR/operator/src/main" "$SRCDIR/operator/src/test" -type f \( -name "*.java" -o -name "*.sh" -o -name "*.py" \) -exec cat {} + | cksum
}

save_cksum() {
  latest_cksum > "$SRCDIR/operator/src/main.cksum"
}

old_cksum() {
  [ -f "$SRCDIR/operator/src/main.cksum" ] && cat "$SRCDIR/operator/src/main.cksum"
}

# Avoid rebuild if nothing changed
if [ "$(old_cksum 2>/dev/null)" = "$(latest_cksum)" ]; then
  echo "@@ Info: Skipping Operator image build - no changes detected."
  exit 0
fi

# Clean up target to avoid file lock issues
echo "@@ Info: Cleaning Maven targets..."
find "$SRCDIR/operator" -name target -type d -exec chmod -R u+w {} \; -exec rm -rf {} +

# Create unique temp local Maven repo to avoid .m2 lock collisions
TEMP_M2_REPO="/tmp/m2repo-$RANDOM"

echo "@@ Info: Running Maven build..."
mvn clean install -Dmaven.repo.local="$TEMP_M2_REPO"

# Handle build args for proxy if set
HTTP_BUILD_ARG=""
[ -n "${http_proxy:-}" ] && HTTP_BUILD_ARG+=" --build-arg http_proxy=$http_proxy"
[ -n "${https_proxy:-}" ] && HTTP_BUILD_ARG+=" --build-arg https_proxy=$https_proxy"
[ -n "${no_proxy:-}" ] && HTTP_BUILD_ARG+=" --build-arg no_proxy=$no_proxy"


${WLSIMG_BUILDER:-docker} build ${HTTP_BUILD_ARG:-} \
  -t "${OPER_IMAGE_NAME}:${OPER_IMAGE_TAG}" \
  --build-arg VERSION="${OPER_JAR_VERSION}" \
  --no-cache=true .

save_cksum

# Optional: push image to remote registry
# Uncomment if needed and set REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD
# if [[ -n "${REPO_REGISTRY:-}" && -n "${REPO_USERNAME:-}" && -n "${REPO_PASSWORD:-}" ]]; then
#   echo "@@ Info: Pushing image to ${REPO_REGISTRY}..."
#   ${WLSIMG_BUILDER:-docker} login "$REPO_REGISTRY" -u "$REPO_USERNAME" -p "$REPO_PASSWORD"
#   ${WLSIMG_BUILDER:-docker} push "${OPER_IMAGE_NAME}:${OPER_IMAGE_TAG}"
# else
#   echo "@@ Warning: Skipping ${WLSIMG_BUILDER:-docker} push. Set REPO_REGISTRY, REPO_USERNAME, and REPO_PASSWORD to enable."
# fi

echo "@@ Done: Operator image built and tagged successfully."
