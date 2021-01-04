#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

logfile="/home/oracle/setup/log/setupDB.log"
max=30
counter=0
while [ $counter -le ${max} ]
do
 grep "Done ! The database is ready for use ." $logfile
 [[ $? == 0 ]] && break;
 ((counter++))
 echo "[$counter/${max}] Retrying for Oracle Database Availability..."
 sleep 10
done

if [ $counter -gt ${max} ]; then
 echo "[ERRORR] Oracle DB Service is not ready after [${max}] iterations ..."
 exit -1
fi
