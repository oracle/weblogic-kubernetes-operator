#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Configure RCU schema based on schemaPreifix and rcuDatabaseURL

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} -s <schemaPrefix> -d <dburl>  [-h]"
  echo "  -s RCU Schema Prefix, must be specified."
  echo "  -d RCU Oracle Database URL"
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

ocr_pfx=container-registry.oracle.com
jrf_image=${ocr_pfx}/middleware/fmw-infrastructure:12.2.1.3

dbpod=`kubectl get po | grep oracle | cut -f1 -d " " `
if [ -z ${dbpod} ]; then
  echo "!!!! Oracle Service Pod not found in [default] namespace !!!"
  echo "!!!! Execute the script create-db-service.sh !!!"
  exit -1
fi

sh ${scriptDir}/common/checkPodReady.sh ${dbpod} default

docker pull ${jrf_image}
kubectl run rcu --generator=run-pod/v1 --image ${jrf_image} -- sleep infinity
sh ${scriptDir}/common/checkPodReady.sh rcu default

sleep 5
kubectl get po 

# Generate the default password files for rcu command
echo "Oradoc_db1" > pwd.txt
echo "Oradoc_db1" >> pwd.txt

kubectl cp ${scriptDir}/common/createRepository.sh  rcu:/u01/oracle
kubectl cp pwd.txt rcu:/u01/oracle
rm -rf createRepository.sh pwd.txt

kubectl exec -it rcu /bin/bash /u01/oracle/createRepository.sh ${dburl} ${schemaPrefix}

echo "[INFO] Modify the domain.input.yaml to use [$dburl] as rcuDatabaseURL and [${schemaPrefix}] as rcuSchemaPrefix "
