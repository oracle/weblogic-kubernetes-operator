#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
domainNS1=$3
domainNS2=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

HELM_VERSION=$(helm version --short --client)

if [[ "$HELM_VERSION" =~ "v3" ]]; then
   helm uninstall grafana  --namespace monitortestns
   helm uninstall prometheus  --namespace monitortestns
   sleep 15
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi

export appname=grafana
for p in `kubectl get po -l app.kubernetes.io/name=$appname -o name -n monitortestns `;do echo deleting $p; kubectl delete ${p} -n monitortestns --force --grace-period=0 --ignore-not-found; done

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitortestns `;do echo deleting $p; kubectl delete ${p} -n monitortestns --force --grace-period=0 --ignore-not-found; done

sed -i "s/${domainNS2};${domainNS2}/${domainNS1};${domainNS1}/g" ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml

if [[ "$HELM_VERSION" =~ "v3" ]]; then
  helm install prometheus --wait --debug --namespace monitortestns --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus
  helm install grafana --wait --debug --namespace monitortestns --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi
function checkPod() {
  max=20
  count=0
  CHARTNAME=prometheus
  echo "Checking pods $CHARTNAME-server and $CHARTNAME-alertmanager"
  serverpod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME-server | awk '{print $1 }')
  alertpod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME-alertmanager | awk '{print $1 }')
  grafanapod=$(kubectl get po -n grafana | grep grafana | awk '{print $1 }')
  isGrafanaStarted=false
  while test $count -lt $max; do
    if test "$(kubectl get po -n $CHARTNS | grep grafana | awk '{ print $2 }')" = 1/1; then
      echo "grafana  pod is running now."
      isGrafanaStarted=true;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  if [ "$isGrafanaStarted" = false ]; then
    echo "grafana pod is not running"
    kubectl describe pod/${grafanapod} -n $CHARTNS
    exit 1;
  fi
  while test $count -lt $max; do
    if test "$(kubectl get po -n $CHARTNS | grep $CHARTNAME-server | awk '{ print $2 }')" = 2/2; then
      echo "$CHARTNAME-server  pod is running now."
      if test "$(kubectl get po -n $CHARTNS | grep $CHARTNAME-alertmanager | awk '{ print $2 }')" = 2/2; then
        echo "$CHARTNAME-alertmanager  pod is running now."
        echo "Finished - [redeployPromGrafanaLatestChart.sh] ..."
        exit 0;
      fi
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: $CHARTNAME  pod failed to start."
  echo
  kubectl describe pod/${alertpod} -n $CHARTNS
  kubectl describe pod/${serverpod} -n $CHARTNS
  exit 1
}
checkPod
