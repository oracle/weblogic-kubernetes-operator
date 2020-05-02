#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
domainNS1=$3
domainNS2=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

function checkPod() {
  max=15
  count=0
  CHARTNAME=prometheus
  CHARTNS=monitortestns
  echo "Checking pods $CHARTNAME-server, grafana and $CHARTNAME-alertmanager"
  serverpod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME-server | awk '{print $1 }')
  alertpod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME-alertmanager | awk '{print $1 }')
  grafanapod=$(kubectl get po -n $CHARTNS | grep grafana | awk '{print $1 }')
  isGrafanaStarted=false
  isPromServerStarted=false
  isPromAlertManagerStarted=false
  while test $count -lt $max; do
    if [ "$isGrafanaStarted" = false ]; then
      if test "$(kubectl get po -n $CHARTNS | grep grafana | awk '{ print $2 }')" = 1/1; then
        echo "grafana  pod is running now."
        isGrafanaStarted=true;
      fi
    fi
    if [ "$isPromServerStarted" = false ]; then
      if test "$(kubectl get po -n $CHARTNS | grep prometheus-server | awk '{ print $2 }')" = 2/2; then
        echo "prometheus-server  pod is running now."
        isPromServerStarted=true;
      fi
    fi
    if [ "$isPromAlertManagerStarted" = false ]; then
      if test "$(kubectl get po -n $CHARTNS | grep prometheus-alertmanager | awk '{ print $2 }')" = 2/2; then
        echo "prometheus-alertmanager  pod is running now."
        isPromAlertManagerStarted=true;
      fi
    fi
    if [ "$isGrafanaStarted" = true ] && [ "$isPromServerStarted" = true ] && [ "$isPromAlertManagerStarted" = true ]; then
        echo "Finished redeployPromGrafanaLatestChart.sh"
	exit 0 
    fi
    count=`expr $count + 1`
    sleep 5
  done
  if [ "$isGrafanaStarted" = false ]; then
    echo "ERROR: grafana pod is not running"
    kubectl describe pod/${grafanapod} -n $CHARTNS
    exit 1;
  fi
  if [ "$isPromServerStarted" = false ]; then
    echo "ERROR: prom server pod is not running"
    kubectl describe pod/${prompod} -n $CHARTNS
    exit 1;
  fi
  if [ "$isPromAlertManagerStarted" = false ]; then
    echo "ERROR: prom alertmanager pod is not running"
    kubectl describe pod/${promalertpod} -n $CHARTNS
    exit 1;
  fi
}
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
  helm install prometheus --namespace monitortestns --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus
  script_status=$?
  echo "status $script_status "
  if [ $script_status != 0 ]; then
    echo "install prometheus helm chart returned: $script_status"
    exit $script_status
  fi
  helm install grafana --namespace monitortestns --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana
  script_status=$?
  echo "status $script_status "
  if [ $script_status != 0 ]; then
    echo "install grafana helm chart returned: $script_status"
    exit $script_status
  fi
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi
echo "Checking promeheus, grafana pods status"
checkPod

