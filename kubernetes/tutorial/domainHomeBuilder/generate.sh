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

checkPV
docker run --rm  \
  -v $MYDIR/scripts:/scripts \
  -v $PV_ROOT/shared:/u01/oracle/user-projects \
  -e "DOMAIN_HOME=/u01/oracle/user-projects/domains/$1" \
  -e "DOMAIN_NAME=$1" \
  -e "ADMIN_USER=$2" \
  -e "ADMIN_PWD=$3" \
  store/oracle/weblogic:12.2.1.3  /scripts/create-domain.sh
