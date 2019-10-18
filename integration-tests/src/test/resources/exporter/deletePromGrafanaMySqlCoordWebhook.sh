#!/bin/bash -x
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$3
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end
#delete webhook 
kubectl delete -f ${monitoringExporterEndToEndDir}/webhook/server.yaml --ignore-not-found
kubectl delete ns webhook

#delete grafana
helm delete --purge grafana
kubectl -n monitoring delete secret grafana-secret --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml --ignore-not-found

POD_NAME=$(kubectl get pod -l app=grafana -n monitoring -o jsonpath="{.items[0].metadata.name}")
kubectl delete $POD_NAME --force --grace-period=0 --ignore-not-found

#delete prometheus
helm delete --purge prometheus
kubectl delete -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml --ignore-not-found

POD_NAME=$(kubectl get pod -l app=prometheus -n monitoring -o jsonpath="{.items[0].metadata.name}")
kubectl delete $POD_NAME --force --grace-period=0 --ignore-not-found

#delete coordinator
kubectl delete -f ${resourceExporterDir}/coordinator_${domainNS}.yaml

#delete database
kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml --ignore-not-found

kubectl delete -f ${monitoringExporterEndToEndDir}/util/curl.yaml
echo "Run the script [deletePromGrafanaMySqlCoordWebhook.sh] ..."
