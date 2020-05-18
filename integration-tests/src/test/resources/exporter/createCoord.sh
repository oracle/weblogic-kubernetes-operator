#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$3
resourceExporterDir=$2
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end
monitoringExporterSrcDir=${monitoringExporterDir}/src

cd ${monitoringExporterSrcDir}/config_coordinator
docker build -t config_coordinator .

cd ${resourceExporterDir}
cp coordinator.yml coordinator_${domainNS}.yaml
sed -i "s/default/$domainNS/g"  coordinator_${domainNS}.yaml

if [ ${SHARED_CLUSTER} = "true" ]; then
    tag=`date +"%Y-%m-%d-%s"`
    sed -i "s/config_coordinator/$REPO_REGISTRY\/weblogick8s\/config_coordinator:$tag/g"  ${resourceExporterDir}/coordinator_${domainNS}.yaml
    sed -i "s/IfNotPresent/Always/g"  ${resourceExporterDir}/coordinator_${domainNS}.yaml
    docker login $REPO_REGISTRY -u $REPO_USERNAME -p $REPO_PASSWORD
    docker tag config_coordinator:latest $REPO_REGISTRY/weblogick8s/config_coordinator:$tag
    docker push $REPO_REGISTRY/weblogick8s/config_coordinator:$tag
    if [ ! "$?" = "0" ] ; then
       echo "Error: Could not push the image to $REPO_REGISTRY".
    fi
    kubectl create secret docker-registry ocirsecret -n ${domainNS} \
                    --docker-server=$REPO_REGISTRY \
                    --docker-username=$REPO_USERNAME \
                    --docker-password=$REPO_PASSWORD \
                    --docker-email=$REPO_EMAIL  \
                    --dry-run -o yaml | kubectl apply -f -

fi

kubectl apply -f ${resourceExporterDir}/coordinator_${domainNS}.yaml
echo "Finished - [createCoord.sh] ..."
