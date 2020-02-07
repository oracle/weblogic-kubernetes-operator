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

#delete grafana
helm delete --purge grafana
kubectl -n monitoring delete secret grafana-secret --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml --ignore-not-found

export appname=grafana
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

#delete prometheus
helm delete --purge prometheus
kubectl delete -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml --ignore-not-found

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

#delete coordinator
kubectl delete -f ${resourceExporterDir}/coordinator_${domainNS}.yaml

#delete database
kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml --ignore-not-found
kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml --ignore-not-found
#kubectl delete -f ${monitoringExporterEndToEndDir}/mysql/mysql-secret.yaml
kubectl delete -f ${monitoringExporterEndToEndDir}/util/curl.yaml
echo "Run the script [deletePromGrafanaMySqlCoordWebhook.sh] ..."
