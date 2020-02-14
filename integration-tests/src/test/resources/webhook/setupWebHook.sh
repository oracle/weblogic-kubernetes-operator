#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
webhookDir=$1
webhookResourceDir=$2
operatorNS=$3
if [ ! -d "$webhookDir" ]; then
    echo "Installing webhook files ${webhookDir}..."
    mkdir ${webhookDir}
    cp -rf ${webhookResourceDir}/* ${webhookDir}/.
    cp ${webhookResourceDir}/../../../../../src/scripts/scaling/scalingAction.sh ${webhookDir}
    echo "cloning webhook executable"
    cd ${webhookDir}
    wget  https://github.com/adnanh/webhook/releases/download/2.6.9/webhook-linux-amd64.tar.gz
    tar -xvf webhook-linux-amd64.tar.gz
    cp webhook-linux-amd64/webhook ${webhookDir}
fi
cd ${webhookDir}
docker build . -t webhook:latest
# Here we need internal Certificate
operator_cert_data=`kubectl get cm -n ${operatorNS} weblogic-operator-cm -o jsonpath='{.data.internalOperatorCert}'`
echo " operator cert  ${operator_cert_data}"
chmod +w ${webhookDir}/webhook-deployment.yaml
sed -i -e "s:@INTERNAL_OPERATOR_CERT@:${operator_cert_data}:g" ${webhookDir}/webhook-deployment.yaml
cat ${webhookDir}/webhook-deployment.yaml
echo "deploying webhook"
kubectl apply -f ${webhookDir}/webhook-deployment.yaml
echo "after deploying webhook"
sleep 15
kubectl get pods -n monitoring
kubectl apply -f ${webhookDir}/crossrbac_monitoring.yaml
echo "Run the script [setupWebHook.sh] ..."