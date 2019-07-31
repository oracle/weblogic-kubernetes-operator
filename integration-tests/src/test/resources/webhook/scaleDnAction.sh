#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upload
echo scale down  action
MASTER=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT
echo Kubernetes master is $MASTER

sh /var/scripts/scalingAction.sh --action=scaleDown --domain_uid=test5 --cluster_name=cluster-1 --kubernetes_master=$MASTER --wls_domain_namespace=test5 --operator_namespace=weblogic-operator5  2>&1 | tee -a scaleUpAction.log