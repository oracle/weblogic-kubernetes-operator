#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$3
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end

cd ${resourceExporterDir}
cp coordinator.yml coordinator_${domainNS}.yaml
sed -i "s/default/$domainNS/g"  coordinator_${domainNS}.yaml

if [ ${SHARED_CLUSTER} = "true" ]; then
    sed -i "s/config_coordinator/$REPO_REGISTRY\/weblogick8s\/config_coordinator/g"  ${resourceExporterDir}/coordinator_${domainNS}.yaml
    sed -i "s/IfNotPresent/Always/g"  ${resourceExporterDir}/coordinator_${domainNS}.yaml
fi

#create coordinator
if [ ${SHARED_CLUSTER} = "true" ]; then
    kubectl create secret docker-registry ocirsecret -n ${domainNS} \
                        --docker-server=$REPO_REGISTRY \
                        --docker-username=$REPO_USERNAME \
                        --docker-password=$REPO_PASSWORD \
                        --docker-email=$REPO_EMAIL  \
                        --dry-run -o yaml | kubectl apply -f -
fi

kubectl apply -f ${resourceExporterDir}/coordinator_${domainNS}.yaml
echo "Run the script [createCoord.sh] ..."
