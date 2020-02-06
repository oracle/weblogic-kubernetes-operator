#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
echo scale down  action
MASTER=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT
echo Kubernetes master is $MASTER

sh /var/scripts/scalingAction.sh --action=scaleDown --domain_uid=test5 --cluster_name=cluster-1 --kubernetes_master=$MASTER --wls_domain_namespace=test5 --operator_namespace=weblogic-operator1  2>&1 | tee -a scaleDnAction.log
