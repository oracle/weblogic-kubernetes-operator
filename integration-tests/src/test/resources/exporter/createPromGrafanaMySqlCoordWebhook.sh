#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload

monitoringExporterDir=$1
resourceExporterDir=$2
promVersionArgs=$3
grafanaVersionArgs=$4
domainNS=$5
domainNS1=$6
scriptDir=$monitoringExporterDir/../scripts

cp $resourceExporterDir/create*.sh $scriptDir
chmod 777 $scriptDir/*.sh
echo "Installing Prometheus ..."
sh $scriptDir/createProm.sh $monitoringExporterDir $resourceExporterDir $promVersionArgs $domainNS $domainNS1
script_status=$?
echo "status $script_status "
if [ $script_status != 0 ]; then
    echo "createProm.sh returned: $script_status"
    exit $script_status
fi

echo "Installing mysql"
sh $scriptDir/createMySql.sh $monitoringExporterDir $resourceExporterDir $domainNS1
script_status=$?
if [ $script_status != 0 ]; then
    echo "createMySql.sh returned: $script_status"
    exit $script_status
fi
echo "Installing webhook"
sh $scriptDir/createWebhook.sh $monitoringExporterDir $resourceExporterDir
script_status=$?
if [ $script_status != 0 ]; then
    echo "createWebhook.sh returned: $script_status"
    exit $script_status
fi
echo "Installing coordinator"
sh $scriptDir/createCoord.sh $monitoringExporterDir $resourceExporterDir $domainNS
script_status=$?
if [ $script_status != 0 ]; then
    echo "createCoordinator.sh returned: $script_status"
    exit $script_status
fi
echo "Completed [createPromGrafanaMySqlCoordWebhook.sh]"
