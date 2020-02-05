#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
samplesDir=${monitoringExporterDir}/src/samples/kubernetes/deployments

kubectl delete -f ${samplesDir}/alertmanager-deployment.yaml
kubectl delete -f ${monitoringExporterDir}/webhook/webhook-deployment.yaml
kubectl delete -f ${monitoringExporterDir}/webhook/crossrbac_monitoring.yaml


echo "Run the script [deleteWebHookAlertManager.sh] ..."