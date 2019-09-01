#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to setup the WebLogic Kubernetes Cluster


#kubectl create namespace sample-domain1-ns
set -e
echo "Creating weblogic domain secret"
kubectl -n sample-domain1-ns create secret generic sample-domain1-weblogic-credentials \
  --from-literal=username=weblogic \
  --from-literal=password=welcome1
kubectl label secret sample-domain1-weblogic-credentials -n sample-domain1-ns weblogic.domainUID=sample-domain1 weblogic.domainName=sample-domain1
echo "Creating sample wdt configmap (optional)"
kubectl create configmap wdt-config-map -n sample-domain1-ns --from-file=cm
echo "Applying domain resource yaml"
kubectl apply -f domain.yaml
echo "Done setting up domain"

echo "Getting pod status - ctrl-c when all is running and ready to exit"
kubectl get pods -n sample-domain1-ns --watch
#


