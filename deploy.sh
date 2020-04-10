#!/bin/bash
# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

IMAGE="container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest"

echo 'Building Docker image...'
docker build -t $IMAGE --no-cache=true .

echo 'Pushing Docker image...'
docker push $IMAGE
