#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS1=$3
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end


sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/scripts/simple-topology.yaml
sed -i "s/3306\/@@PROP:DOMAIN_NAME@@/3306\/domain1/g" ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/scripts/simple-topology.yaml

echo "Run the script [createWLSImage.sh] ..."
