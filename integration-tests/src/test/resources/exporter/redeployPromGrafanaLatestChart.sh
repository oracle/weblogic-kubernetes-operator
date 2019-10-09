#!/bin/bash -x
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

helm delete prometheus --purge
helm delete grafana --purge

helm install --wait --name prometheus --namespace monitoring --values  ${resourceExporterDir}/promvalues.yaml stable/prometheus

helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana
echo "Run the script [redeployPromGrafanaLatestChart.sh] ..."
