#!/usr/bin/env bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.
#
. /u01/oracle/wlserver/server/bin/setWLSEnv.sh

echo "Check if the DB Service is Ready to accept Connection"
connectString=${1:-infradb.db.svc.cluster.local:1521/InfraPDB1.us.oracle.com}
echo "The DB Connection URL [$connectString]"

max=20
counter=0
while [ $counter -le ${max} ]
do
 #java utils.dbping ORACLE_THIN "sys as sysdba" Oradoc_db1 ${connectString} 
 java utils.dbping ORACLE_THIN scott tiger ${connectString} > dbping.err 2>&1 
 [[ $? == 0 ]] && break;
 ((counter++))
 echo "[$counter/${max}] Retrying the DB Connection. Sleep for 10s more ..."
 sleep 10
done

if [ $counter -gt ${max} ]; then 
 echo "[ERRORR] Oracle DB Service is not ready after [${max}] iterations ..."
 exit -1
else 
 java utils.dbping ORACLE_THIN scott tiger ${connectString} 
fi 
