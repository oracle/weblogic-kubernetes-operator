#!/bin/bash
echo scale down  action
MASTER=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT
echo Kubernetes master is $MASTER
source /var/scripts/scalingAction.sh --action=scaleDown --domain_uid=domain1 --cluster_name=DockerCluster --kubernetes_master=$MASTER --wls_domain_namespace=weblogic-domain
