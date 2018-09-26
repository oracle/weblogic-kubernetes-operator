#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

function clean_jenkins {
    echo "Cleaning."
    /usr/local/packages/aime/ias/run_as_root "${PROJECT_ROOT}/src/integration-tests/bash/clean_docker_k8s.sh -y"
}

function setup_jenkins {
    echo "Setting up."
    /usr/local/packages/aime/ias/run_as_root "sh ${PROJECT_ROOT}/src/integration-tests/bash/install_docker_k8s.sh -y -u wls -v ${K8S_VERSION}"
    set +x
    . ~/.dockerk8senv
    set -x
    id

    docker login -u teamsldi_us@oracle.com -p $docker_pass  wlsldi-v2.docker.oraclecorp.com
    docker images

	pull_tag_images

    export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
    # create a docker image for the operator code being tested
    docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"  --build-arg VERSION=$JAR_VERSION --no-cache=true .
	docker tag "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" wlsldi-v2.docker.oraclecorp.com/weblogic-operator:latest
	
    docker images
    
    echo "Helm installation starts" 
    wget -q -O  /tmp/helm-v2.8.2-linux-amd64.tar.gz https://kubernetes-helm.storage.googleapis.com/helm-v2.8.2-linux-amd64.tar.gz
    mkdir /tmp/helm
    tar xzf /tmp/helm-v2.8.2-linux-amd64.tar.gz -C /tmp/helm
    chmod +x /tmp/helm/linux-amd64/helm
    /usr/local/packages/aime/ias/run_as_root "cp /tmp/helm/linux-amd64/helm /usr/bin/"
    rm -rf /tmp/helm
    helm init
    echo "Helm is configured."
}

function setup_wercker {
    echo "Perform setup for running in wercker"
	echo "Install tiller"
	kubectl create serviceaccount --namespace kube-system tiller
	kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller
	
	# Note: helm init --wait would wait until tiller is ready, and requires helm 2.8.2 or above 
	helm init --service-account=tiller --wait
	
	helm version
	
	kubectl get po -n kube-system
	
	echo "Existing helm charts "
	helm ls
	echo "Deleting installed helm charts"
	helm list --short | xargs -L1 helm delete --purge
	echo "After helm delete, list of installed helm charts is: "
	helm ls

    echo "Completed setup_wercker"
}

function pull_tag_images {

    echo "Pull and tag the images we need"
    docker pull wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest store/oracle/weblogic:12.2.1.3

    docker pull wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest store/oracle/serverjre:8

    docker pull wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest store/oracle/apache:12.2.1.3

}


function create_image_pull_secret_jenkins {

    echo "Creating Secret"
    kubectl create secret docker-registry wlsldi-secret  \
    --docker-server=wlsldi-v2.docker.oraclecorp.com \
    --docker-username=teamsldi_us@oracle.com \
    --docker-password=$docker_pass \
    --docker-email=teamsldi_us@oracle.com 

    echo "Checking Secret"
    local SECRET="`kubectl get secret wlsldi-secret | grep wlsldi | wc | awk ' { print $1; }'`"
    if [ "$SECRET" != "1" ]; then
        echo 'secret wlsldi-secret was not created successfully'
        exit 1
    fi

}

export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."
export RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
export PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
echo "RESULT_ROOT$RESULT_ROOT PV_ROOT$PV_ROOT"
export BRANCH_NAME="${BRANCH_NAME:-$WERCKER_GIT_BRANCH}"
    
if [ -z "$BRANCH_NAME" ]; then
  export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
  if [ ! "$?" = "0" ] ; then
  	echo "Error: Could not determine branch.  Run script from within a git repo".
  	exit 1
  fi
fi
export IMAGE_TAG_OPERATOR=${IMAGE_TAG_OPERATOR:-`echo "test_${BRANCH_NAME}" | sed "s#/#_#g"`}
export IMAGE_NAME_OPERATOR=${IMAGE_NAME_OPERATOR:-wlsldi-v2.docker.oraclecorp.com/weblogic-operator}

cd $PROJECT_ROOT
if [ $? -ne 0 ]; then
    echo "Couldn't change to $PROJECT_ROOT dir"
    exit 1
fi

export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"

echo IMAGE_NAME_OPERATOR $IMAGE_NAME_OPERATOR IMAGE_TAG_OPERATOR $IMAGE_TAG_OPERATOR JAR_VERSION $JAR_VERSION

if [ "$WERCKER" = "true" ]; then 

	echo "Test Suite is running locally on Wercker and k8s is running on remote nodes."

	export IMAGE_PULL_SECRET_OPERATOR=$IMAGE_PULL_SECRET_OPERATOR
	export IMAGE_PULL_SECRET_WEBLOGIC=$IMAGE_PULL_SECRET_WEBLOGIC

    echo "Creating Docker Secret"
    kubectl create secret docker-registry $IMAGE_PULL_SECRET_WEBLOGIC  \
    --docker-server=index.docker.io/v1/ \
    --docker-username=$DOCKER_USERNAME \
    --docker-password=$DOCKER_PASSWORD \
    --docker-email=$DOCKER_EMAIL 
    -n default 

    echo "Checking Secret"
    SECRET="`kubectl get secret $IMAGE_PULL_SECRET_WEBLOGIC | grep $IMAGE_PULL_SECRET_WEBLOGIC | wc | awk ' { print $1; }'`"
    if [ "$SECRET" != "1" ]; then
        echo "secret $IMAGE_PULL_SECRET_WEBLOGIC was not created successfully"
        exit 1
    fi

    echo "Creating Registry Secret"
    kubectl create secret docker-registry $IMAGE_PULL_SECRET_OPERATOR  \
    --docker-server=$REPO_REGISTRY \
    --docker-username=$REPO_USERNAME \
    --docker-password=$REPO_PASSWORD \
    --docker-email=$REPO_EMAIL 
    -n default 

    echo "Checking Secret"
    SECRET="`kubectl get secret $IMAGE_PULL_SECRET_OPERATOR | grep $IMAGE_PULL_SECRET_OPERATOR | wc | awk ' { print $1; }'`"
    if [ "$SECRET" != "1" ]; then
        echo "secret $IMAGE_PULL_SECRET_OPERATOR was not created successfully"
        exit 1
    fi
    
    setup_wercker
    
elif [ "$JENKINS" = "true" ]; then

  echo "Test Suite is running on Jenkins and k8s is running locally on the same node."

  # External customizable env vars unique to Jenkins:

  export docker_pass=${docker_pass:?}
  export M2_HOME=${M2_HOME:?}
  export K8S_VERSION=${K8S_VERSION}

  clean_jenkins

  setup_jenkins

  create_image_pull_secret_jenkins

  /usr/local/packages/aime/ias/run_as_root "mkdir -p $PV_ROOT"
  /usr/local/packages/aime/ias/run_as_root "mkdir -p $RESULT_ROOT"

  # 777 is needed because this script, k8s pods, and/or jobs may need access.

  /usr/local/packages/aime/ias/run_as_root "mkdir -p $RESULT_ROOT/acceptance_test_tmp"
  /usr/local/packages/aime/ias/run_as_root "chmod 777 $RESULT_ROOT/acceptance_test_tmp"

  /usr/local/packages/aime/ias/run_as_root "mkdir -p $RESULT_ROOT/acceptance_test_tmp_archive"
  /usr/local/packages/aime/ias/run_as_root "chmod 777 $RESULT_ROOT/acceptance_test_tmp_archive"

  /usr/local/packages/aime/ias/run_as_root "mkdir -p $PV_ROOT/acceptance_test_pv"
  /usr/local/packages/aime/ias/run_as_root "chmod 777 $PV_ROOT/acceptance_test_pv"

  /usr/local/packages/aime/ias/run_as_root "mkdir -p $PV_ROOT/acceptance_test_pv_archive"
  /usr/local/packages/aime/ias/run_as_root "chmod 777 $PV_ROOT/acceptance_test_pv_archive"



else
	pull_tag_images
	  
	#docker rmi -f $(docker images -q -f dangling=true)
	docker images --quiet --filter=dangling=true | xargs --no-run-if-empty docker rmi  -f
	
	
  export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
	docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"  --build-arg VERSION=$JAR_VERSION --no-cache=true .
	
fi

