#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

set -exu

cp /u01/scripts/* /tmp/  #copy scripts to tmp folder to garrantee current user has write permission to. 

WDT_HOME="/u01"
SCRIPT_DIR="/tmp"

cd $WDT_HOME && \
jar xf ./weblogic-deploy.zip && \
chmod +x weblogic-deploy/bin/*.sh && \
mkdir -p $DOMAIN_PARENT && \

# update domainName, adminUser, adminPwd to domain.properties
sed -i "s/^DOMAIN_NAME.*/DOMAIN_NAME=$DOMAIN_NAME/g" $SCRIPT_DIR/domain.properties
sed -i "s/^ADMIN_USER.*/ADMIN_USER=$ADMIN_USER/g" $SCRIPT_DIR/domain.properties
sed -i "s/^ADMIN_PWD.*/ADMIN_PWD=$ADMIN_PWD/g" $SCRIPT_DIR/domain.properties

${WDT_HOME}/weblogic-deploy/bin/createDomain.sh \
    -oracle_home $ORACLE_HOME \
    -java_home $JAVA_HOME \
    -domain_parent $DOMAIN_PARENT \
    -domain_type WLS \
    -model_file ${SCRIPT_DIR}/simple-topology.yaml \
    -variable_file ${SCRIPT_DIR}/domain.properties  

