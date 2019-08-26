#!/bin/bash
echo ">>>> Creating domain namespace"
kubectl create namespace sample-domain1-ns
echo ">>>> Creating weblogic domain secret"
kubectl -n sample-domain1-ns create secret generic sample-domain1-weblogic-credentials \
  --from-literal=username=weblogic \
  --from-literal=password=welcome1
kubectl label secret sample-domain1-weblogic-credentials -n sample-domain1-ns weblogic.domainUID=sample-domain1 weblogic.domainName=sample-domain1
echo ">>>> Creating sample wdt configmap (optional)"
kubectl create configmap wdt-config-map -n sample-domain1-ns --from-file=cm
echo ">>>> Applying domain resource yaml"
kubectl apply -f domain.yaml
echo "Done setting up domain"

