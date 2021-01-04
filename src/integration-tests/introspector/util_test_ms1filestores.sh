#!/bin/sh
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Description:
# ------------
#
# This helper utility tests the override locations of both default
# and custom file store locations. We also verify a server's DOMAIN_HOME 
# 'data' directory (managed-server1) as a symbolic link or standard
# directory (admin-server). 
# It's intended to be run on an image with an oracle install
# and assumes the ORACLE_HOME env var has been set.
#

traceFile=/weblogic-operator/scripts/utils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

test_checkManagedServerFileStores()
{
  local testout="/tmp/unit_test_checkManagedServerFileStores"
  rm -f $testout
  (
    if [ ! -d ${DOMAIN_HOME}/servers/${SERVER_NAME}/data ]; then
      echo "ERROR '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' does NOT exist as a directory"
    else
      echo "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' exists as a directory"
    fi

    if [ -L ${DOMAIN_HOME}/servers/${SERVER_NAME}/data ]; then
      echo "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' is a symbolic link."
    else
      echo "'ERROR ${DOMAIN_HOME}/servers/${SERVER_NAME}/data' is a standard directory and NOT a symbolic link. Expected a symbolic link"
    fi

    if [ ! -f ${DATA_HOME}/_WLS_MANAGED-SERVER1000000.DAT ]; then
      echo "ERROR '${DATA_HOME}/_WLS_MANAGED-SERVER1000000.DAT' default file store does NOT exist" 
    else
      echo "'${DATA_HOME}/_WLS_MANAGED-SERVER1000000.DAT' default file store exists" 
    fi

  ) 2>&1 > $testout 2>&1
  cat $testout
  grep --silent -i ERROR $testout && return 1
  rm -f $testout
  return 0
}

test_checkManagedServerFileStores || exit 1
trace Test passed.
exit 0
