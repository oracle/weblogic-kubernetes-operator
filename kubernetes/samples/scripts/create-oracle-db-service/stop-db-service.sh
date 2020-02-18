#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Drop the DB Service created by start-db-service.sh

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

function usage {
  echo "usage: ${script} -n namespace  [-h]"
  echo " -n Kubernetes NameSpace for Oracle DB Service to be Stopped (optional)"
  echo "     (default: default) "
  echo " -h Help"
  exit $1
}

while getopts ":h:n:" opt; do
  case $opt in
    n) namespace="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done


if [ -z ${namespace} ]; then
  namespace=default
fi


dbpod=`kubectl get po -n ${namespace}  | grep oracle-db | cut -f1 -d " " `
kubectl delete -f ${scriptDir}/common/oracle.db.yaml  --ignore-not-found

if [ -z ${dbpod} ]; then
  echo "Couldn't find oracle-db pod in [${namespace}] namesapce"
else
  checkPodDelete ${dbpod} ${namespace}
  kubectl delete svc/oracle-db -n ${namespace}  --ignore-not-found
fi
