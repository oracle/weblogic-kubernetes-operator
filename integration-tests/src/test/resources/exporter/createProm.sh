#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$4
resourceExporterDir=$2
promVersionArgs=$3
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end
CHARTNAME=prometheus
CHARTNS=monitortestns

function checkPod() {
  max=15
  count=0
  echo "Checking pods $CHARTNAME-server and $CHARTNAME-alertmanager"
  serverpod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME-server | awk '{print $1 }')
  alertpod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME-alertmanager | awk '{print $1 }')
  while test $count -lt $max; do
    kubectl get po -n $CHARTNS | grep $CHARTNAME-server
    kubectl get po -n $CHARTNS | grep $CHARTNAME-alertmanager
    kubectl logs ${alertpod} -n $CHARTNS -c prometheus-alertmanager
    if test "$(kubectl get po -n $CHARTNS | grep $CHARTNAME-server | awk '{ print $2 }')" = 2/2; then
      echo "$CHARTNAME-server  pod is running now."
      if test "$(kubectl get po -n $CHARTNS | grep $CHARTNAME-alertmanager | awk '{ print $2 }')" = 2/2; then
        echo "$CHARTNAME-alertmanager  pod is running now."
        echo "Finished - [createProm.sh] ..."
        exit 0;
      fi
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: $CHARTNAME pods failed to start."
  echo
  kubectl describe pod/${alertpod} -n $CHARTNS
  kubectl describe pod/${serverpod} -n $CHARTNS
  kubectl get events -n $CHARTNS  --sort-by='.metadata.creationTimestamp'
  kubectl logs ${alertpod} -n $CHARTNS -c prometheus-alertmanager
  kubectl logs ${prompod} -n $CHARTNS -c prometheus-server
  kubectl logs ${alertpod} -n $CHARTNS -c prometheus-alertmanager-configmap-reload
  kubectl logs ${prompod} -n $CHARTNS -c prometheus-server-configmap-reload
  echo "createProm.sh returned: 1"
  exit 1
}
sed -i "s/default;domain1/${domainNS};${domainNS}/g" ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml

kubectl create ns $CHARTNS

sed -i "s/monitoring/${CHARTNS}/g" ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
sed -i "s/monitoring/${CHARTNS}/g" ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
sed -i "s/pv-prom/pv-testprom/g" ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
sed -i "s/pv-alert/pv-testalert/g" ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
cat ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
cat ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml


kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
kubectl get pv -n $CHARTNS
kubectl get pvc -n $CHARTNS

HELM_VERSION=$(helm version --short --client)
if [[ "$HELM_VERSION" =~ "v2" ]]; then
  echo "Detected unsupported Helm version [${HELM_VERSION}]"
  echo "createProm.sh returned: 1"
  exit 1
fi

helm repo update

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n $CHARTNS `;do echo "deleting $p "; kubectl delete ${p} -n $CHARTNS --force --grace-period=0 --ignore-not-found; done
sleep 15

echo "Installing $CHARTNAME helm chart."
helm install  $CHARTNAME stable/$CHARTNAME --namespace $CHARTNS \
  --values ${monitoringExporterEndToEndDir}/$CHARTNAME/promvalues.yaml --version ${promVersionArgs}
script_status=$?
echo "status $script_status "
if [ $script_status != 0 ]; then
    echo "createProm.sh returned: $script_status"
    echo "ERROR: createProm.sh failed"
    helm status $CHARTNAME --namespace $CHARTNS
    kubectl get events -n $CHARTNS  --sort-by='.metadata.creationTimestamp'
    helm uninstall $CHARTNAME --namespace $CHARTNS
    exit $script_status
fi
echo "Wait until $CHARTNAME pod is running."
checkPod
