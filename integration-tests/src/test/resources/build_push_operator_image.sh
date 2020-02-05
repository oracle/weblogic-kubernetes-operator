#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


function build_push_operator_image {
	if [ -z "$REPO_REGISTRY" ] || [ -z "$REPO_USERNAME" ] || [ -z "$REPO_PASSWORD" ]; then
		echo "Provide Docker login details using REPO_REGISTRY, REPO_USERNAME & REPO_PASSWORD env variables to push the Operator image to the repository."
		exit 1
	fi
	
	echo "Build and push Operator image to $REPO_REGISTRY"
	
  	# create a docker image for the operator code being tested
  	docker build --build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy --build-arg no_proxy=$no_proxy -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"  --build-arg VERSION=$JAR_VERSION --no-cache=true .
  	
	docker login $REPO_REGISTRY -u $REPO_USERNAME -p $REPO_PASSWORD	
	docker push ${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}
	if [ ! "$?" = "0" ] ; then
	    echo "Error: Could not push the image to $REPO_REGISTRY".
	    #exit 1
  	fi
	
  
}

export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."

if [ -z "$BRANCH_NAME" ]; then
  export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
  if [ ! "$?" = "0" ] ; then
    echo "Error: Could not determine branch.  Run script from within a git repo".
    exit 1
  fi
fi
export IMAGE_TAG_OPERATOR=${IMAGE_TAG_OPERATOR:-`echo "test_${BRANCH_NAME}" | sed "s#/#_#g"`}
export IMAGE_NAME_OPERATOR=${IMAGE_NAME_OPERATOR:-weblogic-kubernetes-operator}

cd $PROJECT_ROOT
if [ ! "$?" = "0" ] ; then
  echo "Could not change to $PROJECT_ROOT dir"
  exit 1
fi

mvn install
if [ ! "$?" = "0" ] ; then
  echo "mvn install failed"
  exit 1
fi


export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
echo IMAGE_NAME_OPERATOR $IMAGE_NAME_OPERATOR IMAGE_TAG_OPERATOR $IMAGE_TAG_OPERATOR JAR_VERSION $JAR_VERSION

build_push_operator_image