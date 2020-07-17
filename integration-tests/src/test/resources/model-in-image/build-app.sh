#!/usr/bin/env bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home in docker image, and generates the domain resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The WDT sample requires that JAVA_HOME is set to a java JDK version 1.8 or greater
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#    * If logHomeOnPV is enabled, the kubernetes persisitent volume must already be created
#    * If logHomeOnPV is enabled, the kubernetes persisitent volume claim must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDirr="$( cd "$( dirname "${script}" )" && pwd )"
echo "scriptDirr,,, is ${scriptDirr}"

outputDir=$1
domainUID=$2
scriptDir=$3
domainOutputDir="${outputDir}/weblogic-domains/${domainUID}"
miiWorkDir="${domainOutputDir}/miiWorkDir"
modelDir="${miiWorkDir}/models"

echo "scriptDir is ${scriptDir}"
echo "domainOutputDir is ${domainOutputDir}"
echo "miiWorkDir is ${miiWorkDir}"
echo "modelDir is ${modelDir}"

cp -r ${scriptDir}/ds_app ${miiWorkDir}
cd ${miiWorkDir}/ds_app/wlsdeploy/applications
rm -f ds_app.ear
jar cvfM ds_app.ear *

cd ../..
rm -f ${modelDir}/archive.zip
pwd
zip ${modelDir}/archive.zip wlsdeploy/applications/ds_app.ear wlsdeploy/config/amimemappings.properties