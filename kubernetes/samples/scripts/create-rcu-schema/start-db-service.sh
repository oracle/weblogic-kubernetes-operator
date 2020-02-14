#!/bin/bash
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Bring up Oracle DB Instance in [default] NameSpace with a NodePort Service 

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/common/utility.sh

function usage {
  echo "usage: ${script} -p <nodeport> -i <image> -s <docker-store> [-h]"
  echo "  -i  Oracle DB Image (optional)"
  echo "      (default: container-registry.oracle.com/database/enterprise:12.2.0.1-slim ) "
  echo "  -p DB Service NodePort (optional)"
  echo "      (default: 30011) "
  echo "  -s DB Image PullSecret  (optional)"
  echo "      (default: docker-store) "
  echo "  -h Help"
  exit $1
}

while getopts ":h:p:s:i:" opt; do
  case $opt in
    p) nodeport="${OPTARG}"
    ;;
    s) pullsecret="${OPTARG}"
    ;;
    i) dbimage="${OPTARG}"
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

if [ -z ${cenodeport} ]; then
  nodeport=30011
fi

if [ -z ${dbimage} ]; then
  dbimage="container-registry.oracle.com/database/enterprise:12.2.0.1-slim"
fi

echo "NodePort[$nodeport] ImagePullSecret[$pullsecret] Image[${dbimage}]"

# Modify ImagePullSecret and DatabaseImage based on input
sed -i -e '$d' ${scriptDir}/common/oracle.db.yaml
echo '           - name: docker-store' >> ${scriptDir}/common/oracle.db.yaml
sed -i -e "s?name: docker-store?name: ${pullsecret}?g" ${scriptDir}/common/oracle.db.yaml
sed -i -e "s?image:.*?image: ${dbimage}?g" ${scriptDir}/common/oracle.db.yaml
kubectl apply -f ${scriptDir}/common/oracle.db.yaml

# Modify the NodePort based on input 
sed -i -e "s?nodePort:.*?nodePort: ${nodeport}?g" ${scriptDir}/common/oracle.db.yaml
kubectl apply -f ${scriptDir}/common/oracle.db.yaml
dbpod=`kubectl get po | grep oracle-db | cut -f1 -d " " `
checkPod ${dbpod} default
checkPodState ${dbpod} default "1/1"
kubectl get po
kubectl get service

echo "Oracle DB service is RUNNING with NodePort [${nodeport}]"
