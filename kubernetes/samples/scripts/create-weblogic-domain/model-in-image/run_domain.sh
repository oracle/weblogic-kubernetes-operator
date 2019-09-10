#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This is an example of how to setup the WebLogic Kubernetes Cluster

set -eu

echo "Creating weblogic domain secret"

kubectl -n sample-domain1-ns \
  create secret generic sample-domain1-weblogic-credentials \
  --from-literal=username=weblogic \
  --from-literal=password=welcome1

kubectl -n sample-domain1-ns \
  label secret sample-domain1-weblogic-credentials \
  weblogic.domainUID=sample-domain1 \
  weblogic.domainName=sample-domain1

echo "Creating rcu access secret (ignored unless domain type is JRF or RestrictedJRF)"

kubectl -n sample-domain1-ns \
  create secret generic sample-domain1-rcu-access \
  --from-literal=rcu_prefix=FMW1 \
  --from-literal=rcu_schema_password=welcome1 \
  --from-literal=rcu_admin_password=Oradoc_db1 \
  --from-literal=rcu_db_conn_string=oracle-db.sample-domain1-ns.svc.cluster.local:1521/pdb1.k8s

kubectl -n sample-domain1-ns \
  label secret sample-domain1-rcu-access \
  weblogic.domainUID=sample-domain1 \
  weblogic.domainName=sample-domain1

echo "Creating sample wdt configmap (optional)"

kubectl -n sample-domain1-ns \
  create configmap wdt-config-map \
  --from-file=model1.20.properties

kubectl -n sample-domain1-ns \
  label  configmap wdt-config-map \
  weblogic.domainUID=sample-domain1 \
  weblogic.domainName=sample-domain1

echo "Applying domain resource yaml"

kubectl apply -f k8s-domain.yaml

echo "Done setting up domain"

echo "Getting pod status - ctrl-c when all is running and ready to exit"

kubectl get pods -n sample-domain1-ns --watch
