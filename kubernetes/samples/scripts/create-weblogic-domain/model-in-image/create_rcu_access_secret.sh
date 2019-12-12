# !/bin/sh
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script can be used to create an RCU access secret for a model-in-image operator image
# with an image type of 'JRF' or 'RestrictedJRF'.
#

set -eu

kubectl -n sample-domain1-ns delete secret sample-domain1-rcu-access --ignore-not-found

kubectl -n sample-domain1-ns \
  create secret generic sample-domain1-rcu-access \
  --from-literal=rcu_prefix=FMW1 \
  --from-literal=rcu_schema_password=Oradoc_db1 \
  --from-literal=rcu_admin_password=Oradoc_db1 \
  --from-literal=rcu_db_conn_string=oracle-db.default.svc.cluster.local:1521/devpdb.k8s

#
# Set the weblogic.domainUID label. This is optional, but useful during cleanup
# and inventorying of resources specific to a particular domain.
#

kubectl -n sample-domain1-ns \
  label secret sample-domain1-rcu-access \
  weblogic.domainUID=domain1

