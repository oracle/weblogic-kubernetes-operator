#!/bin/bash
#  Copyright 2017, Oracle Corporation and/or affiliates.  All rights reserved.

#IMAGE="container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest"
# for early access builds:
IMAGE="quay.io/markxnelson/weblogic-kubernetes-operator:latest"

echo 'Building Docker image...'
docker build -t $IMAGE --no-cache=true .

echo 'Pushing Docker image...'
docker push $IMAGE
