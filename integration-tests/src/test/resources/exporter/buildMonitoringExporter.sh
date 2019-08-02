#!/bin/bash -x
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
resourceExporterDir=$2
monitoringExporterSrcDir=${monitoringExporterDir}/src
monitoringExporterWar=${monitoringExporterDir}/apps/monitoringexporter/wls-exporter.war


if [ -d "$monitoringExporterDir" ]; then
    rm -rf $monitoringExporterDir
fi
mkdir $monitoringExporterDir
echo "Installing monitoring exporter files to ${monitoringExporterDir}..."
cd ${monitoringExporterDir}
git clone  https://github.com/oracle/weblogic-monitoring-exporter.git $monitoringExporterSrcDir

echo "Building monitoring exporter files to ${monitoringExporterDir}..."
cd ${monitoringExporterSrcDir}
mvn clean install --log-file output.txt
cd ${monitoringExporterSrcDir}/webapp
mvn package -Dconfiguration=${resourceExporterDir}/rest_webapp.yml
cd ${monitoringExporterSrcDir}/config_coordinator
docker build -t config_coordinator .
mkdir ${monitoringExporterDir}/apps
mkdir ${monitoringExporterDir}/apps/monitoringexporter
cp ${monitoringExporterSrcDir}/webapp/target/wls-exporter.war ${monitoringExporterWar}
echo "Run the script [buildMonitoringExporter.sh] ..."