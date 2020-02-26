#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
monitoringExporterBranch=${3:-master}
monitoringExporterVersion=${4:-1.1.1}
monitoringExporterSrcDir=${monitoringExporterDir}/src
monitoringExporterWar=${monitoringExporterDir}/apps/monitoringexporter/wls-exporter.war


if [ -d "$monitoringExporterDir" ]; then
    rm -rf $monitoringExporterDir
fi
mkdir $monitoringExporterDir
echo "Installing monitoring exporter files to ${monitoringExporterDir}..."
cd ${monitoringExporterDir}
git config --global http.sslVerify false
git clone  -b ${monitoringExporterBranch} https://github.com/oracle/weblogic-monitoring-exporter.git $monitoringExporterSrcDir

echo "Building monitoring exporter files to ${monitoringExporterDir}..."
mkdir ${monitoringExporterDir}/apps
mkdir ${monitoringExporterDir}/apps/monitoringexporter
cd ${monitoringExporterDir}/apps/monitoringexporter
echo "Download webapp from ://github.com/oracle/weblogic-monitoring-exporter/releases/download/v${monitoringExporterVersion}/get${monitoringExporterVersion}.sh..."
curl -O -L -k https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v${monitoringExporterVersion}/get${monitoringExporterVersion}.sh
bash get${monitoringExporterVersion}.sh ${resourceExporterDir}/rest_webapp.yml

cd ${monitoringExporterSrcDir}/config_coordinator
docker build -t config_coordinator .
if [ ${SHARED_CLUSTER} = "true" ]; then
    docker login $REPO_REGISTRY -u $REPO_USERNAME -p $REPO_PASSWORD
    docker tag config_coordinator:latest $REPO_REGISTRY/weblogick8s/config_coordinator:latest
    docker push $REPO_REGISTRY/weblogick8s/config_coordinator:latest
    if [ ! "$?" = "0" ] ; then
       echo "Error: Could not push the image to $REPO_REGISTRY".
      #exit 1
    fi
fi
echo "Run the script [buildMonitoringExporter.sh] ..."
