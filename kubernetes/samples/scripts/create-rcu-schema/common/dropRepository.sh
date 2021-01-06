#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

. /u01/oracle/wlserver/server/bin/setWLSEnv.sh

echo "Check if the DB Service is ready to accept request "
connectString=${1:-oracle-db.default.svc.cluster.local:1521/devpdb.k8s}
schemaPrefix=${2:-domain1}
rcuType=${3:-fmw}
sysPassword=${4:-Oradoc_db1}

echo "DB Connection String [$connectString] schemaPrefix [${schemaPrefix}] rcuType[${rcuType}]"

max=20
counter=0
while [ $counter -le ${max} ]
do
 java utils.dbping ORACLE_THIN "sys as sysdba" ${sysPassword} ${connectString} > dbping.err 2>&1 
 [[ $? == 0 ]] && break;
 ((counter++))
 echo "[$counter/${max}] Retrying the DB Connection ..."
 sleep 10
done

if [ $counter -gt ${max} ]; then 
 echo "[ERROR] Oracle DB Service is not ready after [${max}] iterations ..."
 exit -1
else 
 java utils.dbping ORACLE_THIN "sys as sysdba" ${sysPassword} ${connectString}
fi 

# SOA needs extra component(s) SOAINFRA ESS (optional)
# SOA needs variables param(s) SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO

case $rcuType in
 fmw)
   extComponents=""
   extVariables=""
   echo "Dropping RCU Schema for FMW Domain ..."
   ;;
 soa|soaosb|osb)
   extComponents="-component SOAINFRA"
   extVariables="-variables SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO"
   echo "Dropping RCU Schema for SOA Domain ..."
   ;;
 soaess|soaessosb)
    extComponents="-component SOAINFRA -component ESS"
    extVariables="-variables SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO"
    echo "Dropping RCU Schema for SOA Domain with ESS ..."
  ;;
 * )
    echo "[ERROR] Unknown RCU Schema Type [$rcuType]"
    echo "Supported values: fmw(default),soa,osb,soaosb,soaess,soaessosb"
    exit -1
  ;;
esac

echo "Extra RCU Schema Component(s) Choosen[${extComponents}]" 
echo "Extra RCU Schema Variable(s)  Choosen[${extVariables}]" 

/u01/oracle/oracle_common/bin/rcu -silent -dropRepository \
 -databaseType ORACLE -connectString ${connectString} \
 -dbUser sys  -dbRole sysdba \
 -selectDependentsForComponents true \
 -schemaPrefix ${schemaPrefix} ${extComponents} ${extVariables}  \
 -component MDS -component IAU -component IAU_APPEND -component IAU_VIEWER \
 -component OPSS  -component WLS -component STB < /u01/oracle/pwd.txt
