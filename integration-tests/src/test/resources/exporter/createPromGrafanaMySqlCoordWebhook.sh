#!/bin/bash -x
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=test5
resourceExporterDir=$2
promVersionArgs=$3
grafanaVersionArgs=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end


#create database
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml 
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml

sleep 15

POD_NAME=$(kubectl get pod -l app=mysql -o jsonpath="{.items[0].metadata.name}")
kubectl exec -it $POD_NAME -- mysql -p123456 -e "CREATE DATABASE domain1;"
kubectl exec -it $POD_NAME -- mysql -p123456 -e "CREATE USER 'wluser1' IDENTIFIED BY 'wlpwd123';"
kubectl exec -it $POD_NAME -- mysql -p123456 -e "GRANT ALL ON domain1.* TO 'wluser1';"
kubectl exec -it $POD_NAME -- mysql -u wluser1 -pwlpwd123 -D domain1 -e "show tables;"

kubectl create ns monitoring

kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
kubectl get pv -n monitoring
kubectl get pvc -n monitoring

helm repo update

helm install --wait --name prometheus --namespace monitoring --values  ${resourceExporterDir}/promvalues.yaml stable/prometheus  --version ${promVersionArgs}


POD_NAME=$(kubectl get pod -l app=prometheus -n monitoring -o jsonpath="{.items[0].metadata.name}")
kubectl describe $POD_NAME -n monitoring


kubectl --namespace monitoring create secret generic grafana-secret --from-literal=username=admin --from-literal=password=12345678


kubectl apply -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml
helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version ${grafanaVersionArgs}

cd ${monitoringExporterEndToEndDir}
docker build ./webhook -t webhook-log:1.0;
kubectl create ns webhook
kubectl apply -f ${monitoringExporterEndToEndDir}/webhook/server.yaml

#create coordinator
kubectl apply -f ${resourceExporterDir}/coordinator_${domainNS}.yaml

echo "Run the script [createPromGrafanaMySqlCoordWebhook.sh] ..."
