#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This is an example of how to setup the WebLogic Kubernetes Cluster

set -eu

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF"]; then
  echo "Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1
fi

echo "@@ Info: Setting Domain Type in k8s-domain.yaml"

cp k8s-domain.yaml.template k8s-domain.yaml
sed -i s/@@DOMTYPE@@/${WDT_DOMAIN_TYPE}/ k8s-domain.yaml

echo "@@ Info: Creating weblogic domain secret"

kubectl -n sample-domain1-ns \
  create secret generic sample-domain1-weblogic-credentials \
  --from-literal=username=weblogic \
  --from-literal=password=welcome1

kubectl -n sample-domain1-ns \
  label secret sample-domain1-weblogic-credentials \
  weblogic.domainUID=sample-domain1 \
  weblogic.domainName=sample-domain1

echo "@@ Info: Creating rcu access secret (ignored unless domain type is JRF or RestrictedJRF)"

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

echo "@@ Info: Creating sample wdt configmap (optional)"

kubectl -n sample-domain1-ns \
  create configmap wdt-config-map \
  --from-file=model1.20.properties

kubectl -n sample-domain1-ns \
  label  configmap wdt-config-map \
  weblogic.domainUID=sample-domain1 \
  weblogic.domainName=sample-domain1

echo "@@ Info: Applying domain resource yaml"

kubectl apply -f k8s-domain.yaml

echo "@@ Info: Getting pod status - ctrl-c when all is running and ready to exit"

kubectl get pods -n sample-domain1-ns --watch
