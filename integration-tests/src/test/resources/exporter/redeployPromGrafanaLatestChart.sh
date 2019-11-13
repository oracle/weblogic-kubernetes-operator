#!/bin/bash -x
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

helm delete prometheus --purge
helm delete grafana --purge
export appname=grafana
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

helm install --wait --name prometheus --namespace monitoring --values  ${resourceExporterDir}/promvalues.yaml stable/prometheus

#remove version after https://github.com/helm/charts/issues/18215 will be fixed
helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version=3.12.0
echo "Run the script [redeployPromGrafanaLatestChart.sh] ..."
