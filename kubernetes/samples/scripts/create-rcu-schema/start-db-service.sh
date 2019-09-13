#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Bring up Oracle DB Instance in [default] NameSpace with a NodePort Service 

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} -p <nodeport> [-h]"
  echo "  -p DBService External NodePort"
  echo "  -h Help"
  exit $1
}

while getopts "h:p:" opt; do
  case $opt in
    p) nodeport="${OPTARG}"
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


ocr_pfx=container-registry.oracle.com
db_image=${ocr_pfx}/database/enterprise:12.2.0.1-slim
docker pull ${db_image}

kubectl apply -f ${scriptDir}/common/oradb.yaml
# Modify the NodePort based on input 
sed -i -e "s?nodePort:.*?nodePort: ${nodeport}?g" ${scriptDir}/common/orasvc.yaml
kubectl apply -f ${scriptDir}/common/orasvc.yaml

dbpod=`kubectl get po | grep oracle-db | cut -f1 -d " " `
sh ${scriptDir}/common/checkPodReady.sh ${dbpod} default

kubectl get po
kubectl get service

echo "Oracle DB service is RUNNING with external NodePort [${nodeport}]"
