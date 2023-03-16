#!/bin/bash
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

usage() {
cat >&2 << EOF

Usage: buildAndPushImage.sh -t tag
Builds and pushes a container image for the Oracle WebLogic Kubernetes Operator.

Parameters:
   -t: image name and tag in 'name:tag' format

Copyright (c) 2020, 2023, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

EOF
}

# WebLogic Kubernetes Operator Image Name
name=""

# Parameters
while getopts "t:" optname; do
  case ${optname} in
    t )
      name="$OPTARG"
      ;;
    \? )
      usage; exit 0
      ;;
  esac
done

if [ ! "$name" ]; then
  echo "-t must be provided"
  usage; exit 1
fi
SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

# Proxy settings
PROXY_SETTINGS=""
if [ "${http_proxy}" != "" ]; then
  PROXY_SETTINGS="$PROXY_SETTINGS --build-arg http_proxy=${http_proxy}"
fi

if [ "${https_proxy}" != "" ]; then
  PROXY_SETTINGS="$PROXY_SETTINGS --build-arg https_proxy=${https_proxy}"
fi

if [ "${ftp_proxy}" != "" ]; then
  PROXY_SETTINGS="$PROXY_SETTINGS --build-arg ftp_proxy=${ftp_proxy}"
fi

if [ "${no_proxy}" != "" ]; then
  PROXY_SETTINGS="$PROXY_SETTINGS --build-arg no_proxy=${no_proxy}"
fi

if [ "$PROXY_SETTINGS" != "" ]; then
  echo "Proxy settings were found and will be used during build."
fi

# ################## #
# BUILDING AND PUSHING THE IMAGE #
# ################## #
echo "Building image '$name' ..."

# BUILD AND PUSH THE IMAGE (replace all environment variables)
BUILD_START=$(date '+%s')
${WLSIMG_BUILDER:-docker} build $PROXY_SETTINGS --pull --platform linux/amd64 --tag $name-amd64 -f $SCRIPTPATH/Dockerfile $SCRIPTPATH || {
  echo "There was an error building the amd64 image."
  exit 1
}
${WLSIMG_BUILDER:-docker} push $PROXY_SETTINGS $name-amd64 || {
  echo "There was an error pushing the amd64 image."
  exit 1
}
${WLSIMG_BUILDER:-docker} build $PROXY_SETTINGS --pull --platform linux/arm64 --tag $name-aarch64 -f $SCRIPTPATH/Dockerfile $SCRIPTPATH || {
  echo "There was an error building the aarch64 image."
  exit 1
}
${WLSIMG_BUILDER:-docker} push $PROXY_SETTINGS $name-aarch64 || {
  echo "There was an error pushing the aarch64 image."
  exit 1
}
${WLSIMG_BUILDER:-docker} manifest create $PROXY_SETTINGS $name --amend $name-amd64 --amend $name-aarch64 || {
  echo "There was an error building the manifest."
  exit 1
}
${WLSIMG_BUILDER:-docker} manifest push $PROXY_SETTINGS $name || {
  echo "There was an error pushing the manifest."
  exit 1
}
BUILD_END=$(date '+%s')
BUILD_ELAPSED=`expr $BUILD_END - $BUILD_START`

echo ""

if [ $? -eq 0 ]; then
cat << EOF
  WebLogic Kubernetes Operator Image is ready:

    --> $name

  Build and push completed in $BUILD_ELAPSED seconds.

EOF
else
  echo "WebLogic Kubernetes Operator container image was NOT successfully created. Check the output and correct any reported problems with the ${WLSIMG_BUILDER:-docker} build operation."
fi
