#!/bin/bash -x
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=test5
resourceExporterDir=$2
promVersionArgs=$3
grafanaVersionArgs=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml

helm install --wait --name prometheus --namespace monitoring --values  ${resourceExporterDir}/promvalues.yaml stable/prometheus  --version ${promVersionArgs}

kubectl --namespace monitoring create secret generic grafana-secret --from-literal=username=admin --from-literal=password=12345678


kubectl apply -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml
helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version ${grafanaVersionArgs}

cd ${monitoringExporterEndToEndDir}
docker build ./webhook -t webhook-log:1.0;
kubectl create ns webhook
kubectl apply -f ${monitoringExporterEndToEndDir}/webhook/server.yaml

#create coordinator
kubectl apply -f ${resourceExporterDir}/coordinator_${domainNS}.yaml

#create database
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml 
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml

echo "Run the script [createPromGrafanaMySqlCoordWebhook.sh] ..."
