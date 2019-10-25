#!/bin/bash -x
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

POD_NAME=$(kubectl get pod -l app=grafana -n monitoring -o jsonpath="{.items[0].metadata.name}")
POD_NAME1=$(kubectl get pod -l app=prometheus -n monitoring -o jsonpath="{.items[0].metadata.name}")
helm delete prometheus --purge
helm delete grafana --purge
kubectl delete $POD_NAME --force --grace-period=0 --ignore-not-found
kubectl delete $POD_NAME1 --force --grace-period=0 --ignore-not-found
helm install --wait --name prometheus --namespace monitoring --values  ${resourceExporterDir}/promvalues.yaml stable/prometheus

#remove version after https://github.com/helm/charts/issues/18215 will be fixed
helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version=3.12.0
echo "Run the script [redeployPromGrafanaLatestChart.sh] ..."
