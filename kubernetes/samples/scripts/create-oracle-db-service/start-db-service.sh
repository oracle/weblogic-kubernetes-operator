#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Bring up Oracle DB Instance in [default] NameSpace with a NodePort Service 

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

function usage {
  echo "usage: ${script} -p <nodeport> -i <image> -s <pullsecret> -n <namespace>  [-h]"
  echo "  -i  Oracle DB Image (optional)"
  echo "      (default: container-registry.oracle.com/database/enterprise:12.2.0.1-slim ) "
  echo "  -p DB Service NodePort (optional)"
  echo "      (default: 30011) "
  echo "  -s DB Image PullSecret (optional)"
  echo "      (default: docker-store) "
  echo "  -n Configurable Kubernetes NameSpace for Oracle DB Service (optional)"
  echo "      (default: default) "
  echo "  -h Help"
  exit $1
}

while getopts ":h:p:s:i:n:" opt; do
  case $opt in
    p) nodeport="${OPTARG}"
    ;;
    s) pullsecret="${OPTARG}"
    ;;
    i) dbimage="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${nodeport} ]; then
  nodeport=30011
fi

if [ -z ${pullsecret} ]; then
  pullsecret="docker-store"
fi

if [ -z ${namespace} ]; then
  namespace="default"
fi

echo "Checking Status for NameSpace [$namespace]"
domns=`kubectl get ns ${namespace} | grep ${namespace} | awk '{print $1}'`
if [ -z ${domns} ]; then
 echo "Adding NameSpace[$namespace] to Kubernetes Cluster"
 kubectl create namespace ${namespace}
 sleep 5
else
 echo "Skipping the NameSpace[$namespace] Creation ..."
fi

if [ -z ${dbimage} ]; then
  dbimage="container-registry.oracle.com/database/enterprise:12.2.0.1-slim"
fi

echo "NodePort[$nodeport] ImagePullSecret[$pullsecret] Image[${dbimage}] NameSpace[${namespace}]"

# Modify ImagePullSecret and DatabaseImage based on input
sed -i -e '$d' ${scriptDir}/common/oracle.db.yaml
echo '           - name: docker-store' >> ${scriptDir}/common/oracle.db.yaml
sed -i -e "s?name: docker-store?name: ${pullsecret}?g" ${scriptDir}/common/oracle.db.yaml
sed -i -e "s?image:.*?image: ${dbimage}?g" ${scriptDir}/common/oracle.db.yaml
sed -i -e "s?namespace:.*?namespace: ${namespace}?g" ${scriptDir}/common/oracle.db.yaml
kubectl apply -f ${scriptDir}/common/oracle.db.yaml

# Modify the NodePort based on input 
sed -i -e "s?nodePort:.*?nodePort: ${nodeport}?g" ${scriptDir}/common/oracle.db.yaml
kubectl apply -f ${scriptDir}/common/oracle.db.yaml
dbpod=`kubectl get po -n ${namespace} | grep oracle-db | cut -f1 -d " " `
checkPod ${dbpod} ${namespace}
checkPodState ${dbpod} ${namespace} "1/1"
kubectl get po -n ${namespace}
kubectl get service -n ${namespace}

kubectl cp ${scriptDir}/common/checkDbState.sh -n ${namespace} ${dbpod}:/home/oracle/
kubectl exec -it ${dbpod} -n ${namespace} /bin/bash /home/oracle/checkDbState.sh
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not create Oracle DB Service";
 echo "######################";
 exit -3;
fi

echo "Oracle DB Service is RUNNING with NodePort [${nodeport}]"
echo "Oracle DB Service URL [oracle-db.${namespace}.svc.cluster.local:1521/devpdb.k8s]"
