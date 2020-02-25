#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS1=$3
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end


#create database
sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/mysql/persistence.yaml
sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/mysql/mysql.yaml

kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml

sleep 30

POD_NAME=$(kubectl get pod -l app=mysql -o jsonpath="{.items[0].metadata.name}" -n ${domainNS1} )
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -p123456 -e "CREATE DATABASE domain1;"
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -p123456 -e "CREATE USER 'wluser1' IDENTIFIED BY 'wlpwd123';"
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -p123456 -e "GRANT ALL ON domain1.* TO 'wluser1';"
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -u wluser1 -pwlpwd123 -D domain1 -e "show tables;"

echo "Finished - [createMySql.sh] ..."
