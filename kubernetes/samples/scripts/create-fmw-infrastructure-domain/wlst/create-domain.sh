#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export DOMAIN_HOME=${DOMAIN_HOME_DIR}

# Create the domain
wlst.sh -skipWLSModuleScanning \
        /u01/oracle/container-scripts/createInfraDomain.py \
        -oh /u01/oracle \
        -jh /usr/java/latest \
        -parent ${CUSTOM_DOMAIN_ROOT_DIR} \
        -name ${CUSTOM_DOMAIN_NAME} \
        -user ${admin_username} \
        -password ${admin_password} \
        -rcuDb ${CUSTOM_CONNECTION_STRING} \
        -rcuPrefix ${CUSTOM_RCUPREFIX} \
        -rcuSchemaPwd ${rcu_password}

