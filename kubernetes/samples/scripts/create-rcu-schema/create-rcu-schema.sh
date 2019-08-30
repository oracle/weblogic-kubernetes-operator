#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Configure RCU schema based on schemaPreifix and rcuDatabaseURL

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/common/utility.sh

function usage {
  echo "usage: ${script} -s <schemaPrefix> -d <dburl>  [-h]"
  echo "  -s RCU Schema Prefix (needed)"
  echo "  -d RCU Oracle Database URL (optional) "
  echo "      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) "
  echo "  -h Help"
  exit $1
}

while getopts ":h:s:d:" opt; do
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
docker pull ${jrf_image}
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not pull ${jrf_image}";
 echo "Please run  [ docker login ${ocr_pfx} ]  and "
 echo "Check-out the fmw-infrastructure:12.2.1.3 image (if needed)"
 echo "######################";
 exit -1;
fi

dbpod=`kubectl get po | grep oracle | cut -f1 -d " " `
if [ -z ${dbpod} ]; then
  echo "Oracle deployment pod not found in [default] namespace"
  echo "Execute the script create-db-service.sh"
  exit -2
fi

# Make sure the DB deployment Pod is RUNNING
checkPod ${dbpod} default
checkPodState ${dbpod} default "1/1"

kubectl run rcu --generator=run-pod/v1 --image ${jrf_image} -- sleep infinity
# Make sure the rcu deployment Pod is RUNNING
checkPod rcu default
checkPodState rcu default "1/1"
sleep 5
kubectl get po/rcu 

# Generate the default password files for rcu command
echo "Oradoc_db1" > pwd.txt
echo "Oradoc_db1" >> pwd.txt

kubectl cp ${scriptDir}/common/createRepository.sh  rcu:/u01/oracle
kubectl cp pwd.txt rcu:/u01/oracle
rm -rf createRepository.sh pwd.txt

kubectl exec -it rcu /bin/bash /u01/oracle/createRepository.sh ${dburl} ${schemaPrefix}
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not create the RCU Repository";
 echo "######################";
 exit -3;
fi

echo "[INFO] Modify the domain.input.yaml to use [$dburl] as rcuDatabaseURL and [${schemaPrefix}] as rcuSchemaPrefix "
