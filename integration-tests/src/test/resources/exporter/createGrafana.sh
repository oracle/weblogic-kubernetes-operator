#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
grafanaVersionArgs=$3
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

HELM_VERSION=$(helm version --short --client)

helm repo update
export appname=grafana
for p in `kubectl get po -l app=$appname -o name -n monitortestns `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

kubectl --namespace monitortestns create secret generic grafana-secret --from-literal=username=admin --from-literal=password=12345678
if [[ ! $OKE_CLUSTER ] || [ ${OKE_CLUSTER} = "false" ]]; then
  kubectl apply -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml
fi
echo "Detected Helm Version [${HELM_VERSION}]"
if [[ "$HELM_VERSION" =~ "v2" ]]; then
   helm install --wait --name grafana --namespace monitortestns --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version ${grafanaVersionArgs}
elif [[ "$HELM_VERSION" =~ "v3" ]]; then
   helm install  grafana --wait --namespace monitortestns --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version ${grafanaVersionArgs}
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi
echo "Finished - [createGrafana.sh] ..."
