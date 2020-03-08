#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
domainNS1=$3
domainNS2=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

HELM_VERSION=$(helm version --short --client)
if [[ "$HELM_VERSION" =~ "v2" ]]; then
   helm delete --purge grafana
   helm delete --purge prometheus
elif [[ "$HELM_VERSION" =~ "v3" ]]; then
   helm uninstall grafana  --namespace monitoring
   helm uninstall prometheus  --namespace monitoring
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi

export appname=grafana
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

sed -i "s/${domainNS2};${domainNS2}/${domainNS1};${domainNS1}/g" ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml
if [[ "$HELM_VERSION" =~ "v2" ]]; then
  helm install --wait --name prometheus --namespace monitoring --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus
  helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version=3.12.0
elif [[ "$HELM_VERSION" =~ "v3" ]]; then
  helm install prometheus --wait --namespace monitoring --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus
  helm install grafana --wait --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version=3.12.0
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi


echo "Run the script [redeployPromGrafanaLatestChart.sh] ..."
