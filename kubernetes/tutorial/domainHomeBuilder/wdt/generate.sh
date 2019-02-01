#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

function checkPV() {
  if [ -z "$PV_ROOT" ] || [ ! -e "$PV_ROOT" ]; then
    echo "PV_ROOT is not set correctly. It needs to point to an existing folder. Currently PV_ROOT is '$PV_ROOT'."
    exit 1
  fi
}

if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

MYDIR="$(dirname "$(readlink -f "$0")")"


# It may be better to do this in the container, but we need to handle proxy settings specifically.
if [ ! -e $MYDIR/weblogic-deploy.zip ] ; then
  echo "weblogic-deploy.zip does not exist. Downloading it from github."  
  wget -P $MYDIR https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-0.11/weblogic-deploy.zip
  echo "download complete"
fi

checkPV
docker run --rm  \
  -v $MYDIR/scripts:/u01/scripts \
  -v $MYDIR/weblogic-deploy.zip:/u01/weblogic-deploy.zip \
  -v $PV_ROOT/shared:/u01/oracle/user-projects \
  -e "DOMAIN_PARENT=/u01/oracle/user-projects/domains" \
  -e "DOMAIN_NAME=$1" \
  -e "ADMIN_USER=$2" \
  -e "ADMIN_PWD=$3" \
  store/oracle/weblogic:12.2.1.3  /u01/scripts/create-domain.sh

#rm $MYDIR/weblogic-deploy.zip
