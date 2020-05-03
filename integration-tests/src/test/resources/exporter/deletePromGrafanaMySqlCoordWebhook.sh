#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$3
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end
#delete webhook 
kubectl delete -f ${resourceExporterDir}/server.yaml --ignore-not-found
kubectl delete ns webhook

HELM_VERSION=$(helm version --short --client)
echo "Detected Helm Version [${HELM_VERSION}]"
#delete grafana, prometheus

if [[ "$HELM_VERSION" =~ "v3" ]]; then
   helm uninstall grafana  --namespace monitortestns
   helm uninstall prometheus  --namespace monitortestns
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi

kubectl -n monitortestns delete secret grafana-secret --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml --ignore-not-found

export appname=grafana
for p in `kubectl get po -l app.kubernetes.io/name=$appname -o name -n monitortestns `;do echo $p; kubectl delete ${p} -n monitortestns --force --grace-period=0 --ignore-not-found; done

kubectl delete -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml --ignore-not-found

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitortestns `;do echo $p; kubectl delete ${p} -n monitortestns --force --grace-period=0 --ignore-not-found; done

#delete coordinator
kubectl delete -f ${resourceExporterDir}/coordinator_${domainNS}.yaml

#delete database
kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/util/curl.yaml
echo "Run the script [deletePromGrafanaMySqlCoordWebhook.sh] ..."
