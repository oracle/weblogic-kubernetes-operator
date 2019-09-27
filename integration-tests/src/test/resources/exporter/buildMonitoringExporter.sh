#!/bin/bash -x
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
monitoringExporterBranch=${3:-master}
monitoringExporterVersion=${4:-1.1.0}
monitoringExporterSrcDir=${monitoringExporterDir}/src
monitoringExporterWar=${monitoringExporterDir}/apps/monitoringexporter/wls-exporter.war


if [ -d "$monitoringExporterDir" ]; then
    rm -rf $monitoringExporterDir
fi
mkdir $monitoringExporterDir
echo "Installing monitoring exporter files to ${monitoringExporterDir}..."
cd ${monitoringExporterDir}
git clone  -b ${monitoringExporterBranch} https://github.com/oracle/weblogic-monitoring-exporter.git $monitoringExporterSrcDir

echo "Building monitoring exporter files to ${monitoringExporterDir}..."
cd ${monitoringExporterDir}
echo "Download webapp from ://github.com/oracle/weblogic-monitoring-exporter/releases/download/v${monitoringExporterVersion}/get${monitoringExporterVersion}.sh..."
wget https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v${monitoringExporterVersion}/get${monitoringExporterVersion}.sh
bash get${monitoringExporterVersion}.sh ${resourceExporterDir}/rest_webapp.yml
#mvn clean install --log-file output.txt
#cd ${monitoringExporterSrcDir}/webapp
#mvn package -Dconfiguration=${resourceExporterDir}/rest_webapp.yml
cd ${monitoringExporterSrcDir}/config_coordinator
docker build -t config_coordinator .
mkdir ${monitoringExporterDir}/apps
mkdir ${monitoringExporterDir}/apps/monitoringexporter
cp ${monitoringExporterDir}/wls-exporter.war ${monitoringExporterWar}
echo "Run the script [buildMonitoringExporter.sh] ..."
