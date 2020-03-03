#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
monitoringExporterDir=$1
domainNS=$5
domainNS1=$6
resourceExporterDir=$2
promVersionArgs=$3
grafanaVersionArgs=$4
monitoringExporterEndToEndDir=${monitoringExporterDir}/src/samples/kubernetes/end2end


#create database
sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/mysql/persistence.yaml
sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/mysql/mysql.yaml
sed -i "s/default/${domainNS1}/g"  ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/scripts/simple-topology.yaml
sed -i "s/3306\/@@PROP:DOMAIN_NAME@@/3306\/domain1/g" ${monitoringExporterEndToEndDir}/demo-domains/domainBuilder/scripts/simple-topology.yaml
cp ${resourceExporterDir}/promvalues.yaml ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml

sed -i "s/default;domain1/${domainNS};${domainNS}/g" ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/mysql/mysql.yaml

sleep 30

POD_NAME=$(kubectl get pod -l app=mysql -o jsonpath="{.items[0].metadata.name}" -n ${domainNS1} )
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -p123456 -e "CREATE DATABASE domain1;"
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -p123456 -e "CREATE USER 'wluser1' IDENTIFIED BY 'wlpwd123';"
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -p123456 -e "GRANT ALL ON domain1.* TO 'wluser1';"
kubectl exec -it $POD_NAME -n $domainNS1 -- mysql -u wluser1 -pwlpwd123 -D domain1 -e "show tables;"

kubectl create ns monitoring

kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/persistence.yaml
kubectl apply -f ${monitoringExporterEndToEndDir}/prometheus/alert-persistence.yaml
kubectl get pv -n monitoring
kubectl get pvc -n monitoring

helm repo update
export appname=grafana
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

export appname=prometheus
for p in `kubectl get po -l app=$appname -o name -n monitoring `;do echo $p; kubectl delete ${p} -n monitoring --force --grace-period=0 --ignore-not-found; done

helm install --debug --wait --name prometheus --namespace monitoring --values  ${monitoringExporterEndToEndDir}/prometheus/promvalues.yaml stable/prometheus  --version ${promVersionArgs}

helm list --all

POD_NAME=$(kubectl get pod -l app=prometheus -n monitoring -o jsonpath="{.items[0].metadata.name}")
kubectl describe $POD_NAME -n monitoring


kubectl --namespace monitoring create secret generic grafana-secret --from-literal=username=admin --from-literal=password=12345678


kubectl apply -f ${monitoringExporterEndToEndDir}/grafana/persistence.yaml
helm install --wait --name grafana --namespace monitoring --values  ${monitoringExporterEndToEndDir}/grafana/values.yaml stable/grafana --version ${grafanaVersionArgs}

cd ${resourceExporterDir}
cp coordinator.yml coordinator_${domainNS}.yaml
sed -i "s/default/$domainNS/g"  coordinator_${domainNS}.yaml

cd ${monitoringExporterEndToEndDir}
docker build ./webhook -t webhook-log:1.0;
if [ ${SHARED_CLUSTER} = "true" ]; then
    docker login $REPO_REGISTRY -u $REPO_USERNAME -p $REPO_PASSWORD
    echo "tag image " $REPO_REGISTRY/$REPO_USERNAME/webhook-log:1.0
    docker tag webhook-log:1.0 $REPO_REGISTRY/weblogick8s/webhook-log:1.0
    docker push $REPO_REGISTRY/weblogick8s/webhook-log:1.0
    if [ ! "$?" = "0" ] ; then
       echo "Error: Could not push the image to $REPO_REGISTRY".
      #exit 1
    fi
    sed -i "s/webhook-log:1.0/$REPO_REGISTRY\/weblogick8s\/webhook-log:1.0/g"  ${resourceExporterDir}/server.yaml
    sed -i "s/config_coordinator/$REPO_REGISTRY\/weblogick8s\/config_coordinator/g"  ${resourceExporterDir}/coordinator_${domainNS}.yaml
    sed -i "s/IfNotPresent/Always/g"  ${resourceExporterDir}/server.yaml
    sed -i "s/IfNotPresent/Always/g"  ${resourceExporterDir}/coordinator_${domainNS}.yaml
fi
echo 'docker list images for webhook'
docker images | grep webhook
kubectl create ns webhook
if [ ${SHARED_CLUSTER} = "true" ]; then
    kubectl create secret docker-registry ocirsecret -n webhook \
                        --docker-server=$REPO_REGISTRY \
                        --docker-username=$REPO_USERNAME \
                        --docker-password=$REPO_PASSWORD \
                        --docker-email=$REPO_EMAIL  \
                        --dry-run -o yaml | kubectl apply -f -
fi

kubectl apply -f ${resourceExporterDir}/server.yaml --validate=false
echo "Getting info about webhook"
kubectl get pods -n webhook

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
echo "Run the script [createPromGrafanaMySqlCoordWebhook.sh] ..."
