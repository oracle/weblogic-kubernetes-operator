#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Builds a Docker Image for the Oracle WebLogic Kubernetes Operator.
# Push the image to Repo Registry.
#
SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
PROJECT_ROOT="$SCRIPTPATH/.."
IMAGE_NAME_OPERATOR=${IMAGE_NAME_OPERATOR:-oracle/weblogic-kubernetes-operator}
REPO_NAME=${REPO_NAME:-phx.ocir.io/weblogick8s/}
# SKIP_BUILD can be used for testing when there is no need to build Operator every time the tests are run
# or when there is need to run the tests using the existing/pre-created operator image
SKIP_BUILD=${SKIP_BUILD:-false}

# Get Branch Name
if [ -z "$BRANCH_NAME" ]; then
  export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
  if [ ! "$?" = "0" ] ; then
    echo "Error: Could not determine branch.  Run script from within a git repo".
    exit 1
  fi
fi
# use branch name in tag for non-Jenkins runs, branch name and build id for Jenkisn runs
IMAGE_TAG_OPERATOR=${IMAGE_TAG_OPERATOR:-`echo "${BRANCH_NAME}${BUILD_ID}" | sed "s#/#_#g"`}
#IMAGE_TAG_OPERATOR_BRANCH=`echo "${BRANCH_NAME}" | sed "s#/#_#g"`
#IMAGE_TAG_OPERATOR_BRANCH_TS=`echo "${BRANCH_NAME}"-date '+%Y%m%d%H%M%S' | sed "s#/#_#g"`

if [ -z "$BUILD_ID" ]; then
  IMAGE="${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"
else
  IMAGE="${REPO_NAME}${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"
fi

if [ "$SKIP_BUILD" = false ] ; then
  cd $PROJECT_ROOT
  if [ ! "$?" = "0" ] ; then
    echo "Could not change to $PROJECT_ROOT dir"
    exit 1
  fi
  echo "Build a Docker Image for the Oracle WebLogic Kubernetes Operator"
  $PROJECT_ROOT/buildDockerImage.sh -t $IMAGE || exit 1

  # Check REPO env variables and push image to REPO_REGISTRY
  if [ -z "$REPO_REGISTRY" ] || [ -z "$REPO_USERNAME" ] || [ -z "$REPO_PASSWORD" ] || [ -z "$REPO_EMAIL" ]; then
    echo "Can not push the Operator image as docker login details REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD & REPO_EMAIL env variables are not provided."
  else
    echo "Login to registry and push the Operator image ${IMAGE}"
    docker login $REPO_REGISTRY -u $REPO_USERNAME -p $REPO_PASSWORD
    docker push $IMAGE
    if [ ! "$?" = "0" ] ; then
        echo "Error: Could not push the image to $REPO_REGISTRY".
        exit 1
    fi

  fi
else
  echo "Skipping Operator build and push, checking if the Docker image $IMAGE exists"
	if [ -z $(docker images -q $IMAGE) ]; then
		echo "Image $IMAGE doesn't exist locally, set SKIP_BUILD to false to build the image"
		# exit 1
	fi
fi

echo "Deleting dangling images if any"
docker images --quiet --filter=dangling=true | xargs --no-run-if-empty docker rmi  -f