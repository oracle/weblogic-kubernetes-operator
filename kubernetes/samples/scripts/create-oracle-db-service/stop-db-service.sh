#!/bin/bash
# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Drop the DB Service created by start-db-service.sh

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

usage() {
  echo "usage: ${script} -n namespace  [-h]"
  echo " -n Kubernetes NameSpace for Oracle DB Service to be Stopped (optional)"
  echo "     (default: default) "
  echo " -h Help"
  exit $1
}

namespace=default

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

dbpod=`${KUBERNETES_CLI:-kubectl} get po -n ${namespace}  | grep oracle-db | cut -f1 -d " " `
${KUBERNETES_CLI:-kubectl} delete -f ${scriptDir}/common/oracle.db.${namespace}.yaml  --ignore-not-found
rm ${scriptDir}/common/oracle.db.${namespace}.yaml  --force

if [ -z "${dbpod}" ]; then
  echo "Couldn't find oracle-db pod in namespace [${namespace}]."
else
  checkPodDelete ${dbpod} ${namespace}
  ${KUBERNETES_CLI:-kubectl} delete svc/oracle-db -n ${namespace}  --ignore-not-found
fi
