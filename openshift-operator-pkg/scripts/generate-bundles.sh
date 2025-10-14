#!/bin/bash
# Copyright (c) 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

command -v kustomize
if [ $? -ne 0 ] ; then
   echo "kustomize is required but not found. Install from https://kustomize.io" && exit 1
fi
VER=$1
if [ -z "$VER" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VARIANTS=("full" "webhook-only" "operator-only" "dedicated")

for variant in "${VARIANTS[@]}"; do
  echo "Generating $variant bundle..."
  
  # Generate manifests directory
  mkdir -p bundles/$variant/manifests
  
  # Build with kustomize and split into separate files
  kustomize build overlays/$variant | awk '
    BEGIN { 
      file = 1
      output = ""
    }
    /^---$/ { 
      if (output != "") {
        print output > sprintf("bundles/'$variant'/manifests/%03d-manifest.yaml", file)
        file++
        output = ""
      }
      next
    }
    { 
      output = output $0 "\n"
    }
    END {
      if (output != "") {
        print output > sprintf("bundles/'$variant'/manifests/%03d-manifest.yaml", file)
      }
    }
  '
  
  # Create metadata
  mkdir -p bundles/$variant/metadata
  cat > bundles/$variant/metadata/annotations.yaml <<EOF
annotations:
  operators.operatorframework.io.bundle.mediatype.v1: registry+v1
  operators.operatorframework.io.bundle.manifests.v1: manifests/
  operators.operatorframework.io.bundle.metadata.v1: metadata/
  operators.operatorframework.io.bundle.package.v1: weblogic-operator-${variant}
  operators.operatorframework.io.bundle.channels.v1: stable
  operators.operatorframework.io.bundle.channel.default.v1: stable
EOF

  # Create Dockerfile
  cat > bundles/$variant/Dockerfile <<EOF
FROM scratch
LABEL operators.operatorframework.io.bundle.mediatype.v1=registry+v1
LABEL operators.operatorframework.io.bundle.manifests.v1=manifests/
LABEL operators.operatorframework.io.bundle.metadata.v1=metadata/
COPY manifests/ /manifests/
COPY metadata/ /metadata/
EOF

  echo "$variant bundle generated with $(ls bundles/$variant/manifests/*.yaml | wc -l) manifest files"
done
