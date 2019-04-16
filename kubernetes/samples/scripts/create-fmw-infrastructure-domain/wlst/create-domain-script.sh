#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export DOMAIN_HOME=${DOMAIN_HOME_DIR}

echo 'Executing wlst.sh -skipWLSModuleScanning '
echo '          /u01/weblogic/createFMWDomain.py '
echo '          -oh /u01/oracle '
echo '          -jh /usr/java/latest '
echo "          -parent ${DOMAIN_ROOT_DIR} "
echo "          -name ${CUSTOM_DOMAIN_NAME} "
echo "          -user " `cat /weblogic-operator/secrets/username`
echo "          -password " `cat /weblogic-operator/secrets/password`
echo "          -rcuDb ${CUSTOM_CONNECTION_STRING} "
echo "          -rcuPrefix ${CUSTOM_RCUPREFIX} "
echo "          -rcuSchemaPwd " `cat /weblogic-operator/rcu-secrets/password`
echo "          -adminListenPort ${CUSTOM_ADMIN_LISTEN_PORT} "
echo "          -adminName ${CUSTOM_ADMIN_NAME} "
echo "          -managedNameBase ${CUSTOM_MANAGED_BASE_NAME} "
echo "          -managedServerPort ${CUSTOM_MANAGEDSERVER_PORT} "
echo "          -prodMode ${CUSTOM_PRODUCTION_MODE} "
echo "          -managedCount ${CUSTOM_MANAGED_SERVER_COUNT} "
echo "          -clusterName ${CUSTOM_CLUSTER_NAME} "

# Create the domain
wlst.sh -skipWLSModuleScanning \
        /u01/weblogic/createFMWDomain.py \
        -oh /u01/oracle \
        -jh /usr/java/latest \
        -parent ${DOMAIN_ROOT_DIR} \
        -name ${CUSTOM_DOMAIN_NAME} \
        -user `cat /weblogic-operator/secrets/username` \
        -password `cat /weblogic-operator/secrets/password` \
        -rcuDb ${CUSTOM_CONNECTION_STRING} \
        -rcuPrefix ${CUSTOM_RCUPREFIX} \
        -rcuSchemaPwd `cat /weblogic-operator/rcu-secrets/password` \
        -adminListenPort ${CUSTOM_ADMIN_LISTEN_PORT} \
        -adminName ${CUSTOM_ADMIN_NAME} \
        -managedNameBase ${CUSTOM_MANAGED_BASE_NAME} \
        -managedServerPort ${CUSTOM_MANAGEDSERVER_PORT} \
        -prodMode ${CUSTOM_PRODUCTION_MODE} \
        -managedCount ${CUSTOM_MANAGED_SERVER_COUNT} \
        -clusterName ${CUSTOM_CLUSTER_NAME}