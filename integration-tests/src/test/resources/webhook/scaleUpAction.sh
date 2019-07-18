#!/bin/bash
echo scale up action > scaleUpAction.log
MASTER=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT
echo Kubernetes master is $MASTER >> scaleUpAction.log

sh /var/scripts/scalingAction.sh --action=scaleUp --domain_uid=domain1 --cluster_name=DockerCluster --kubernetes_master=$MASTER --wls_domain_namespace=weblogic-domain 2>&1 | tee -a scaleUpAction.log 
