#!/bin/bash
# Copyright (c) 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

command -v opm
if [ $? -ne 0 ] ; then
   echo "opm is required but not found. Download from https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp" && exit 1
fi

command -v podman
if [ $? -ne 0 ] ; then
   echo "podman is required but not found. Install podman" && exit 1
fi
VER=$1

if [ -z "$VER" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

REGISTRY="<TO DO>"
VARIANTS=("full" "webhook-only" "operator-only" "dedicated")

# Array to collect bundle images
BUNDLE_IMAGES=()

# Build and push each bundle
for variant in "${VARIANTS[@]}"; do
  echo "Building and pushing $variant..."
  
  podman build -t ${REGISTRY}:olm${VER}-${variant} ./bundles/${variant}/
  podman push ${REGISTRY}:olm${VER}-${variant}
  
  # Add to array
  BUNDLE_IMAGES+=("${REGISTRY}:olm${VER}-${variant}")
done

# Build ONE index with ALL bundles
echo "Building catalog index with all bundles..."
BUNDLE_LIST=$(IFS=,; echo "${BUNDLE_IMAGES[*]}")

opm index add \
  --bundles ${BUNDLE_LIST} \
  --tag ${REGISTRY}:olm${VER}index

podman push ${REGISTRY}:olm${VER}index

echo ""
echo "✅ Complete!"
echo "Catalog: ${REGISTRY}:olm${VER}index"
echo "Bundles:"
for variant in "${VARIANTS[@]}"; do
  echo "  - ${REGISTRY}:olm${VER}-${variant}"
done
