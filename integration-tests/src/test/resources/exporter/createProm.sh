#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$4
domainNS1=$5
resourceExporterDir=$2
promVersionArgs=$3
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end


sed -i "s/default;domain1/${domainNS};${domainNS}/g" ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml

kubectl create ns monitoring

kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
kubectl get pv -n monitoring
kubectl get pvc -n monitoring

HELM_VERSION=$(helm version --short --client)

helm repo update

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

if [[ "$HELM_VERSION" =~ "v2" ]]; then
  helm install --wait --name prometheus --namespace monitoring --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus  --version ${promVersionArgs}
elif [[ "$HELM_VERSION" =~ "v3" ]]; then
  helm install prometheus --wait --namespace monitoring --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus  --version ${promVersionArgs}
POD_NAME=$(kubectl get pod -l app=prometheus -n monitoring -o jsonpath="{.items[0].metadata.name}")
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi
echo "Finished - [createProm.sh] ..."
