#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

usage() {
cat << EOF

Usage: buildDockerImage.sh [-t tag]
Builds a container image for the Oracle WebLogic Kubernetes Operator.

Parameters:
   -t: image name and tag in 'name:tag' format

Copyright (c) 2020, 2021, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

EOF
exit 0
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
      usage
      ;;
  esac
done

IMAGE_NAME=${name:-ghcr.io/oracle/weblogic-kubernetes-operator:3.2.4}
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
# BUILDING THE IMAGE #
# ################## #
echo "Building image '$IMAGE_NAME' ..."

# BUILD THE IMAGE (replace all environment variables)
BUILD_START=$(date '+%s')
docker build $PROXY_SETTINGS -t $IMAGE_NAME -f $SCRIPTPATH/Dockerfile $SCRIPTPATH || {
  echo "There was an error building the image."
  exit 1
}
BUILD_END=$(date '+%s')
BUILD_ELAPSED=`expr $BUILD_END - $BUILD_START`

echo ""

if [ $? -eq 0 ]; then
cat << EOF
  WebLogic Kubernetes Operator Docker Image is ready:

    --> $IMAGE_NAME

  Build completed in $BUILD_ELAPSED seconds.

EOF
else
  echo "WebLogic Kubernetes Operator container image was NOT successfully created. Check the output and correct any reported problems with the docker build operation."
fi