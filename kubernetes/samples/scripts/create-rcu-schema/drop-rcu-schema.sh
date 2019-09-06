#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Drop the RCU schema based on schemaPreifix and DbUrl

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} -s <schemaPrefix> -d <dburl>  [-h]"
  echo "  -s RCU Schema Prefix, must be specified."
  echo "  -d Oracle Database URL"
  echo "  -h Help"
  exit $1
}

while getopts "h:s:d:" opt; do
  case $opt in
    s) schemaPrefix="${OPTARG}"
    ;;
    d) dburl="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${schemaPrefix} ]; then
  echo "${script}: -s <schemaPrefix> must be specified."
  missingRequiredOption="true"
  usage 1
fi

if [ -z ${dburl} ]; then
  dburl="oracle-db.default.svc.cluster.local:1521/devpdb.k8s"
fi

echo "Oradoc_db1" > pwd.txt
echo "Oradoc_db1" >> pwd.txt

kubectl cp ${scriptDir}/common/dropRepository.sh  rcu:/u01/oracle
kubectl cp pwd.txt rcu:/u01/oracle
rm -rf dropRepository.sh pwd.txt

kubectl exec -it rcu /bin/bash /u01/oracle/dropRepository.sh ${dburl} ${schemaPrefix}

kubectl delete pod rcu
sh ${scriptDir}/common/checkPodDelete.sh rcu default
