#!/bin/bash
#  Copyright 2017, 2018, Oracle Corporation and/or affiliates.  All rights reserved.

IMAGE="container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest"

echo 'Building Docker image...'
docker build -t $IMAGE --no-cache=true .

echo 'Pushing Docker image...'
docker push $IMAGE
