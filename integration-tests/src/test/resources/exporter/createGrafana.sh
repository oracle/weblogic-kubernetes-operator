#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
grafanaVersionArgs=$3
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end
CHARTNS=monitortestns
CHARTNAME=grafana
HELM_VERSION=$(helm version --short --client)

function checkPod() {
  max=20
  count=0
  echo "Checking pods $CHARTNAME"
  grafanapod=$(kubectl get po -n $CHARTNS | grep $CHARTNAME | awk '{print $1 }')
  while test $count -lt $max; do
    if test "$(kubectl get po -n $CHARTNS | grep $CHARTNAME | awk '{ print $2 }')" = 1/1; then
      echo "$CHARTNAME  pod is running now."
      echo "Finished - [createGrafana.sh] ..."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: $CHARTNAME pod failed to start."
  echo "createGrafana.sh returned: 1"
  kubectl describe pod/${grafanapod} -n $CHARTNS
  kubectl logs pod/${grafanapod} -n $CHARTNS -c init-chown-data
  kubectl logs pod/${grafanapod} -n $CHARTNS -c grafana
  kubectl describe pod/${grafanapod} -n $CHARTNS
  exit 1
}

helm repo update
export appname=grafana
for p in `kubectl get po -l app.kubernetes.io/name=$appname -o name -n $CHARTNS `;do echo deleting $p; kubectl delete ${p} -n $CHARTNS --force --grace-period=0 --ignore-not-found; done

kubectl -n $CHARTNS create secret generic grafana-secret --from-literal=username=admin --from-literal=password=12345678
sed -i "s/monitoring/${CHARTNS}/g" ${monitoringExporterEndToEndDir}/grafana/persistence.yaml
sed -i "s/pv-grafana/pv-testgrafana/g" ${monitoringExporterEndToEndDir}/grafana/persistence.yaml

kubectl apply -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml
echo "Detected Helm Version [${HELM_VERSION}]"
if [[ "$HELM_VERSION" =~ "v3" ]]; then
   helm install  $CHARTNAME --namespace $CHARTNS --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version ${grafanaVersionArgs}
   script_status=$?
    echo "status $script_status "
    if [ $script_status != 0 ]; then
       echo "createGrafana.sh returned: $script_status"
       echo "ERROR: createGrafana.sh failed"
       kubectl get events -n $CHARTNS  --sort-by='.metadata.creationTimestamp'
       helm uninstall $CHARTNAME --namespace $CHARTNS
       exit $script_status
    fi
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi
echo "Wait until $CHARTNAME pod is running."
checkPod
