#!/bin/bash -x
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

function pull_tag_build_images {
  docker images

  if [ "$JRF_ENABLED" = true ] ; then
	pull_tag_images_jrf	
  else
  	pull_tag_images
  fi

  export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
  # create a docker image for the operator code being tested
  docker build --build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy --build-arg no_proxy=$no_proxy -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"  --build-arg VERSION=$JAR_VERSION --no-cache=true .
  docker tag "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" weblogic-kubernetes-operator:latest
  
  docker images

}

function docker_login {

  set +x 
  if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_PASSWORD" ]; then
        echo "DOCKER_USERNAME and DOCKER_PASSWORD not set !!!"
	exit 1
  fi
  
  if [ -n "$DOCKER_USERNAME" ] && [ -n "$DOCKER_PASSWORD" ]; then  
	  echo "Creating Docker Secret"
	  
	  kubectl create secret docker-registry $IMAGE_PULL_SECRET_WEBLOGIC  \
	    --docker-server=index.docker.io/v1/ \
	    --docker-username=$DOCKER_USERNAME \
	    --docker-password=$DOCKER_PASSWORD \
            --dry-run -o yaml | kubectl apply -f -
	  
	  echo "Checking Secret"
	  SECRET="`kubectl get secret $IMAGE_PULL_SECRET_WEBLOGIC | grep $IMAGE_PULL_SECRET_WEBLOGIC | wc | awk ' { print $1; }'`"
	  if [ "$SECRET" != "1" ]; then
	    echo "secret $IMAGE_PULL_SECRET_WEBLOGIC was not created successfully"
	    exit 1
	  fi
	  # below docker pull is needed to get wlthint3client.jar from image to put in the classpath
	  docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD
  fi
  set -x

}

function setup_shared_cluster {
  echo "Perform setup for running on shared cluster"
  echo "Install tiller"
  kubectl create serviceaccount --namespace kube-system tiller
  kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller
  
  # Note: helm init --wait would wait until tiller is ready, and requires helm 2.8.2 or above 
  helm init --service-account=tiller --wait
  
  helm version
  
  kubectl get po -n kube-system
  
  echo "Existing helm charts "
  helm ls --all
  echo "Deleting installed helm charts"
  helm list --short --all | xargs -L1 helm delete --purge
  echo "After helm delete, list of installed helm charts is: "
  helm ls

  echo "Completed setup_shared_cluster"
}

function clean_shared_cluster {
	echo "Clean shared cluster"
	${PROJECT_ROOT}/src/integration-tests/bash/cleanup.sh
}

function pull_tag_images {

  set +x 
  if [ -z "$OCR_USERNAME" ] || [ -z "$OCR_PASSWORD" ]; then
	if [ -z $(docker images -q $IMAGE_NAME_WEBLOGIC:$IMAGE_TAG_WEBLOGIC) ]; then
		echo "Image $IMAGE_NAME_WEBLOGIC:$IMAGE_TAG_WEBLOGIC doesn't exist. Provide Docker login details using env variables OCR_USERNAME and OCR_PASSWORD to pull the image."
	  	exit 1
	fi
  fi
  
  if [ -n "$OCR_USERNAME" ] && [ -n "$OCR_PASSWORD" ]; then  
	  echo "Creating Docker Secret"
	  
	  kubectl create secret docker-registry $IMAGE_PULL_SECRET_WEBLOGIC  \
	    --docker-server=${OCR_SERVER}/ \
	    --docker-username=$OCR_USERNAME \
	    --docker-password=$OCR_PASSWORD \
            --dry-run -o yaml | kubectl apply -f -
	  
	  echo "Checking Secret"
	  SECRET="`kubectl get secret $IMAGE_PULL_SECRET_WEBLOGIC | grep $IMAGE_PULL_SECRET_WEBLOGIC | wc | awk ' { print $1; }'`"
	  if [ "$SECRET" != "1" ]; then
	    echo "secret $IMAGE_PULL_SECRET_WEBLOGIC was not created successfully"
	    exit 1
	  fi
	  # below docker pull is needed to get wlthint3client.jar from image to put in the classpath
          # echo "docker login -u $OCR_USERNAME -p $OCR_PASSWORD ${OCR_SERVER}"
	  docker login -u $OCR_USERNAME -p $OCR_PASSWORD ${OCR_SERVER}
          # echo "docker pull $IMAGE_NAME_WEBLOGIC:$IMAGE_TAG_WEBLOGIC"
   	  docker pull $IMAGE_NAME_WEBLOGIC:$IMAGE_TAG_WEBLOGIC
  fi
  set -x
}

function pull_tag_images_jrf {

  set +x
  # Check if fwm infra image exists
  if [ -z "$OCR_USERNAME" ] || [ -z "$OCR_PASSWORD" ]; then
	if [ -z $(docker images -q $IMAGE_NAME_WEBLOGIC:$IMAGE_TAG_WEBLOGIC) ]; then
		echo "Image $IMAGE_NAME_FMWINFRA:$IMAGE_TAG_FMWINFRA doesn't exist. Provide Docker login details using env variables OCR_USERNAME and OCR_PASSWORD to pull the image."
	  	exit 1
	fi
	if [ -z $(docker images -q $IMAGE_NAME_ORACLEDB:$IMAGE_TAG_ORACLEDB) ]; then
    	echo "Image $IMAGE_NAME_ORACLEDB:$IMAGE_TAG_ORACLEDB doesn't exist. Provide Docker login details using env variables OCR_USERNAME and OCR_PASSWORD to pull the image."
      	exit 1
    fi
  fi

  # reuse the create secret logic
  pull_tag_images

  # pull fmw infra images
  docker pull $IMAGE_NAME_FMWINFRA:$IMAGE_TAG_FMWINFRA

  # pull oracle db image
  docker pull $IMAGE_NAME_ORACLEDB:$IMAGE_TAG_ORACLEDB

  set -x
}

function get_wlthint3client_from_image {
  # Get wlthint3client.jar from image
  id=$(docker create $IMAGE_NAME_WEBLOGIC:$IMAGE_TAG_WEBLOGIC)
  docker cp $id:/u01/oracle/wlserver/server/lib/wlthint3client.jar $SCRIPTPATH
  if [ ! "$?" = "0" ] ; then
  	echo "Docker Copy failed for wlthint3client.jar"
  	exit 1
  fi
  docker rm -v $id
  
}
export OCR_SERVER="${OCR_SERVER:-container-registry.oracle.com}"
export WLS_IMAGE_URI=/middleware/weblogic
export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."
export RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
export PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
echo "RESULT_ROOT$RESULT_ROOT PV_ROOT$PV_ROOT"
export BRANCH_NAME="${BRANCH_NAME:-$SHARED_CLUSTER_GIT_BRANCH}"
export IMAGE_TAG_WEBLOGIC="${IMAGE_TAG_WEBLOGIC:-12.2.1.3}"

if [ "$JRF_ENABLED" = true ] ; then
  export FMWINFRA_IMAGE_URI=/middleware/fmw-infrastructure
  export IMAGE_TAG_FMWINFRA=12.2.1.3
  export DB_IMAGE_URI=/database/enterprise
  export IMAGE_NAME_ORACLEDB="${IMAGE_NAME_ORACLEDB:-`echo ${OCR_SERVER}``echo ${DB_IMAGE_URI}`}"
  export IMAGE_TAG_ORACLEDB="${IMAGE_TAG_ORACLEDB:-12.2.0.1-slim}"
fi
export IMAGE_NAME_WEBLOGIC="${IMAGE_NAME_WEBLOGIC:-`echo ${OCR_SERVER}``echo ${WLS_IMAGE_URI}`}"
export IMAGE_NAME_FMWINFRA="${IMAGE_NAME_FMWINFRA:-`echo ${OCR_SERVER}``echo ${FMWINFRA_IMAGE_URI}`}"
export IMAGE_PULL_SECRET_WEBLOGIC="${IMAGE_PULL_SECRET_WEBLOGIC:-docker-store}"
    
if [ -z "$BRANCH_NAME" ]; then
  export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
  if [ ! "$?" = "0" ] ; then
    echo "Error: Could not determine branch.  Run script from within a git repo".
    exit 1
  fi
fi
export IMAGE_TAG_OPERATOR=${IMAGE_TAG_OPERATOR:-`echo "test_${BRANCH_NAME}" | sed "s#/#_#g"`}
export IMAGE_NAME_OPERATOR=${IMAGE_NAME_OPERATOR:-weblogic-kubernetes-operator}
export IMAGE_PULL_POLICY_OPERATOR=${IMAGE_PULL_POLICY_OPERATOR:-Always}
export IMAGE_PULL_SECRET_OPERATOR=${IMAGE_PULL_SECRET_OPERATOR:-ocir-operator}

cd $PROJECT_ROOT
if [ $? -ne 0 ]; then
  echo "Couldn't change to $PROJECT_ROOT dir"
  exit 1
fi

export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"

echo IMAGE_NAME_OPERATOR $IMAGE_NAME_OPERATOR IMAGE_TAG_OPERATOR $IMAGE_TAG_OPERATOR JAR_VERSION $JAR_VERSION

#docker_login
  
if [ "$SHARED_CLUSTER" = "true" ]; then
  
  echo "Test Suite is running locally on a shared cluster and k8s is running on remote nodes."
  
  clean_shared_cluster
    
  if [ "$JRF_ENABLED" = true ] ; then
	pull_tag_images_jrf
  else
  	export IMAGE_PULL_SECRET_OPERATOR=$IMAGE_PULL_SECRET_OPERATOR
	export IMAGE_PULL_SECRET_WEBLOGIC=$IMAGE_PULL_SECRET_WEBLOGIC  
  
  	if [ "$IMAGE_PULL_POLICY_OPERATOR" = "Always" ]; then 
		if [ -z "$REPO_REGISTRY" ] || [ -z "$REPO_USERNAME" ] || [ -z "$REPO_PASSWORD" ] || [ -z "$REPO_EMAIL" ]; then
			echo "Provide Docker login details using REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD & REPO_EMAIL env variables to push the Operator image to the repository."
			exit 1
	  	fi
	  	
	  	echo "Creating Registry Secret"
	  	kubectl create secret docker-registry $IMAGE_PULL_SECRET_OPERATOR  \
		    --docker-server=$REPO_REGISTRY \
		    --docker-username=$REPO_USERNAME \
		    --docker-password=$REPO_PASSWORD \
		    --docker-email=$REPO_EMAIL  \
                    --dry-run -o yaml | kubectl apply -f -
	
		echo "Checking Secret"
		SECRET="`kubectl get secret $IMAGE_PULL_SECRET_OPERATOR | grep $IMAGE_PULL_SECRET_OPERATOR | wc | awk ' { print $1; }'`"
		if [ "$SECRET" != "1" ]; then
		    echo "secret $IMAGE_PULL_SECRET_OPERATOR was not created successfully"
		    exit 1
		fi
  	fi
	  
	if [ -z "$K8S_NODEPORT_HOST" ]; then
	  	echo "When running in shared cluster option, provide DNS name or IP of a Kubernetes worker node using K8S_NODEPORT_HOST env variable"
	  	exit 1
	fi
        pull_tag_images
	
  fi
  setup_shared_cluster
  get_wlthint3client_from_image
  docker images
    
elif [ "$JENKINS" = "true" ]; then

  echo "Test Suite is running on Jenkins and k8s is running locally on the same node."

  # External customizable env vars unique to Jenkins:

  export docker_pass=${docker_pass:?}
  export M2_HOME=${M2_HOME:?}
  export K8S_VERSION=${K8S_VERSION}

  pull_tag_build_images

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
  
  if [ "$JRF_ENABLED" = false ] && [ "$IMAGE_TAG_WEBLOGIC" != "12.2.1.4-slim" ];  then
  	get_wlthint3client_from_image
  fi
else
  if [ "$JRF_ENABLED" = true ] ; then
	pull_tag_images_jrf	
  else
  	pull_tag_images
  fi
    
  #docker rmi -f $(docker images -q -f dangling=true)
  docker images --quiet --filter=dangling=true | xargs --no-run-if-empty docker rmi  -f
  
  docker images	
	
  export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
  docker build --build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy --build-arg no_proxy=$no_proxy -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}"  --build-arg VERSION=$JAR_VERSION --no-cache=true .
  docker tag "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" weblogic-kubernetes-operator:latest
  
  if [ "$JRF_ENABLED" = false ] && [ "$IMAGE_TAG_WEBLOGIC" != "12.2.1.4-slim" ]; then
  	get_wlthint3client_from_image
  fi
fi
