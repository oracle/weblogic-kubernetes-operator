#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This is an example of how to setup the WebLogic Kubernetes Cluster
#
# Expects the following env vars to already be set:
#
#   WORKDIR - working directory for the sample with at least 10g of space
#   WDT_DOMAIN_TYPE - WLS, RestrictedJRF, or JRF
#

set -eu

cd ${WORKDIR}

echo "@@ Info: Deleting weblogic domain 'domain1' if it already exists"
kubectl -n sample-domain1-ns \
  delete domain domain1 --ignore-not-found

echo "@@ Info: Creating weblogic domain secret"
./create_weblogic_domain_secret.sh

echo "@@ Info: Creating rcu access secret (ignored unless domain type is JRF)"
./create_rcu_access_secret.sh

echo "@@ Info: Creating OPSS passphrase secret (ignored unless domain type is JRF)"
./create_opss_key_secret.sh

echo "@@ Info: Creating sample wdt configmap (optional)"

kubectl -n sample-domain1-ns \
  delete configmap domain1-wdt-config-map --ignore-not-found

kubectl -n sample-domain1-ns \
  create configmap domain1-wdt-config-map \
  --from-file=model1.20.properties

kubectl -n sample-domain1-ns \
  label  configmap domain1-wdt-config-map \
  weblogic.domainUID=domain1

echo "@@ Info: Creating 'k8s-domain.yaml' from 'k8s-domain.yaml.template' and setting its Domain Type"

if [ ! "${WDT_DOMAIN_TYPE}" == "WLS" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] \
   && [ ! "${WDT_DOMAIN_TYPE}" == "JRF" ]; then
  echo "Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1
fi

cp k8s-domain.yaml.template k8s-domain.yaml
sed -i s/@@DOMTYPE@@/${WDT_DOMAIN_TYPE}/ k8s-domain.yaml

# TBD Can we just remove the sed and leave this always uncommented in the template? The sample always deploys the secret even for non-JRF domains.  Or even simpler, maintain 3 (nearly identical) templates.  
if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
  sed -i 's/\#opss:/opss:/' k8s-domain.yaml
  sed -i 's/\#walletPasswordSecret: sample-domain1-opss-key-passphrase-secret/walletPasswordSecret: sample-domain1-opss-key-passphrase-secret/' k8s-domain.yaml
fi

echo "@@ Info: Applying domain resource yaml 'k8s-domain.yaml'"

kubectl apply -f k8s-domain.yaml

echo "@@ Info: Getting pod status - ctrl-c when all is running and ready to exit"

kubectl get pods -n sample-domain1-ns --watch
