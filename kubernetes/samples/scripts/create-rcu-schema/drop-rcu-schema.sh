#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# Drop the RCU schema based on schemaPreifix and Database URL

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

function usage {
  echo "usage: ${script} -s <schemaPrefix> -d <dburl> -n <nameSpace> -q <sysPassword> -r <schemaPassword> [-h]"
  echo "  -s RCU Schema Prefix (needed)"
  echo "  -t RCU Schema Type (optional)"
  echo "      (supported values: fmw(default),soa,osb,soaosb,soaess,soaessosb) "
  echo "  -d Oracle Database URL"
  echo "      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) "
  echo "  -n Namespace where rcu pod is deployed (optional)"
  echo "      (default: default) "
  echo "  -q password for SYSDBA user, must be specified.(optional)"
  echo "      (default: Oradoc_db1)"
  echo "  -r password for schema owner (regular user), must be specified.(optional)"
  echo "      (default: Oradoc_db1)"
  echo "  -h Help"
  exit $1
}

while getopts ":h:s:d:t:n:q:r:" opt; do
  case $opt in
    s) schemaPrefix="${OPTARG}"
    ;;
    t) rcuType="${OPTARG}"
    ;;
    d) dburl="${OPTARG}"
    ;;
    n) nameSpace="${OPTARG}"
    ;;
    q) sysPassword="${OPTARG}"
    ;;
    r) schemaPassword="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${schemaPrefix} ]; then
  echo "${script}: -s <schemaPrefix> must be specified."
  usage 1
fi

if [ -z ${dburl} ]; then
  dburl="oracle-db.default.svc.cluster.local:1521/devpdb.k8s"
fi

if [ -z ${rcuType} ]; then
  rcuType="fmw"
fi

if [ -z ${nameSpace} ]; then
  nameSpace="default"
fi

if [ -z ${sysPassword} ]; then
  sysPassword="Oradoc_db1"
fi

if [ -z ${schemaPassword} ]; then
  schemaPassword="Oradoc_db1"
fi

rcupod=`kubectl get po -n ${nameSpace} | grep rcu | cut -f1 -d " " `
if [ -z ${rcupod} ]; then
  echo "RCU deployment pod not found in [$nameSpace] NameSpace"
  exit -2
fi

#fmwimage=`kubectl get pod/rcu  -o jsonpath="{..image}"`

echo "${sysPassword}" > pwd.txt
echo "${schemaPassword}" >> pwd.txt

kubectl -n $nameSpace exec -i rcu -- bash -c 'cat > /u01/oracle/dropRepository.sh' < ${scriptDir}/common/dropRepository.sh
kubectl -n $nameSpace exec -i rcu -- bash -c 'cat > /u01/oracle/pwd.txt' < pwd.txt
rm -rf dropRepository.sh pwd.txt

kubectl -n $nameSpace  exec -it rcu /bin/bash /u01/oracle/dropRepository.sh ${dburl} ${schemaPrefix} ${rcuType} ${sysPassword}
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not drop the RCU Repository based on dburl[${dburl}] schemaPrefix[${schemaPrefix}]  ";
 echo "######################";
 exit -3;
fi

kubectl delete pod rcu -n ${nameSpace}
checkPodDelete rcu ${nameSpace}
