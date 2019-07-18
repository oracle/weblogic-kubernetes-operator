#!/bin/bash
echo scale up action > scaleUpAction.log
MASTER=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT
echo Kubernetes master is $MASTER >> scaleUpAction.log

sh /var/scripts/scalingAction.sh --action=scaleUp --domain_uid=test5 --cluster_name=cluster-1 --kubernetes_master=$MASTER --wls_domain_namespace=test5 2>&1 | tee -a scaleUpAction.log
