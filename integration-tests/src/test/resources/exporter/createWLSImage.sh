#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS1=$3
resourceExporterDir=$2
imageTag=$4
wlsuser=$5
wlspass=$6
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end


sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/scripts/simple-topology.yaml
sed -i "s/3306\/@@PROP:DOMAIN_NAME@@/3306\/domain1/g" ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/scripts/simple-topology.yaml

sed -i "s/-image:1.0/-image:$imageTag/g"  ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/build.sh
cd ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder && ./build.sh $domainNS1 $wlsuser $wlspass  wluser1 wlpwd123 | tee buidImage.log
if [ ${SHARED_CLUSTER} = "true" ]; then
  cp $resourceExporterDir/domain1.yaml ${monitoringExporterEndToEndDir}/demo-domains/domainInImage.yaml
else
  cp ${monitoringExporterEndToEndDir}/demo-domains/domain1.yaml ${monitoringExporterEndToEndDir}/demo-domains/domainInImage.yaml
fi

sed -i "s/v3/v6/g" ${monitoringExporterEndToEndDir}/demo-domains/domainInImage.yaml
sed -i "s/default/$domainNS1/g" ${monitoringExporterEndToEndDir}/demo-domains/domainInImage.yaml
sed -i "s/domain1/$domainNS1/g" ${monitoringExporterEndToEndDir}/demo-domains/domainInImage.yaml

echo "Finished - [createWLSImage.sh] ..."
