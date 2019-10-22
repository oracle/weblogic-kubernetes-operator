#!/bin/bash
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Configure RCU schema based on schemaPreifix and rcuDatabaseURL

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/common/utility.sh

function usage {
  echo "usage: ${script} -s <schemaPrefix> -t <schemaType> -d <dburl> -i <image> -p <docker-store> [-h]"
  echo "  -s RCU Schema Prefix (needed)"
  echo "  -t RCU Schema Type (optional)"
  echo "      (supported values: fmw(default),soa,osb,soaosb,soaess,soaessosb) "
  echo "  -d RCU Oracle Database URL (optional) "
  echo "      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) "
  echo "  -p FMW Infrastructure ImagePull Secret (optional) "
  echo "      (default: docker-store) "
  echo "  -i FMW Infrastructure Image (optional) "
  echo "      (default: container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3) "
  echo "  -h Help"
  exit $1
}

while getopts ":h:s:d:p:i:t:" opt; do
  case $opt in
    s) schemaPrefix="${OPTARG}"
    ;;
    t) rcuType="${OPTARG}"
    ;;
    d) dburl="${OPTARG}"
    ;;
    p) pullsecret="${OPTARG}"
    ;;
    i) fmwimage="${OPTARG}"
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

if [ -z ${pullsecret} ]; then
  pullsecret="docker-store"
fi

if [ -z ${fmwimage} ]; then
 fmwimage="container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3"
fi

echo "ImagePullSecret[$pullsecret] Image[${fmwimage}] dburl[${dburl}] rcuType[${rcuType}]"

dbpod=`kubectl get po | grep oracle | cut -f1 -d " " `
if [ -z ${dbpod} ]; then
  echo "Oracle deployment pod not found in [default] namespace"
  echo "Execute the script start-db-service.sh"
  exit -2
fi

# Make sure the DB deployment Pod is RUNNING
checkPod ${dbpod} default
checkPodState ${dbpod} default "1/1"

#kubectl run rcu --generator=run-pod/v1 --image ${jrf_image} -- sleep infinity
# Modify the ImagePullSecret based on input
sed -i -e '$d' ${scriptDir}/common/rcu.yaml
echo '           - name: docker-store' >> ${scriptDir}/common/rcu.yaml
sed -i -e "s?name: docker-store?name: ${pullsecret}?g" ${scriptDir}/common/rcu.yaml
sed -i -e "s?image:.*?image: ${fmwimage}?g" ${scriptDir}/common/rcu.yaml
kubectl apply -f ${scriptDir}/common/rcu.yaml

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

kubectl exec -it rcu /bin/bash /u01/oracle/createRepository.sh ${dburl} ${schemaPrefix} ${rcuType}
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not create the RCU Repository";
 echo "######################";
 exit -3;
fi

echo "[INFO] Modify the domain.input.yaml to use [$dburl] as rcuDatabaseURL and [${schemaPrefix}] as rcuSchemaPrefix "
