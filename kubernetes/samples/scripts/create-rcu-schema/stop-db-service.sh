#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Drop the DB Service created by start-db-service.sh

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

dbpod=`kubectl get po | grep oracle-db | cut -f1 -d " " `
kubectl delete -f ${scriptDir}/common/oradb.yaml --ignore-not-found
kubectl delete -f ${scriptDir}/common/orasvc.yaml --ignore-not-found

if [ -z ${dbpod} ]; then
  echo "Couldn't find oarcle-db pod in [default] namesapce"
else
  sh ${scriptDir}/common/checkPodDelete.sh ${dbpod} default
fi
