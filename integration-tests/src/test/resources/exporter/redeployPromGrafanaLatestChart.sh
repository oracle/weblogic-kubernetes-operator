#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
domainNS1=$3
domainNS2=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

helm delete prometheus --purge
helm delete grafana --purge
export appname=grafana
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

sed -i "s/${domainNS2};${domainNS2}/${domainNS1};${domainNS1}/g" ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml

helm install --wait --name prometheus --namespace monitoring --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus

#remove version after https://github.com/helm/charts/issues/18215 will be fixed
helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version=3.12.0

echo "Run the script [redeployPromGrafanaLatestChart.sh] ..."
