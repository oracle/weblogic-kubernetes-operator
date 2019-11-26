#!/bin/bash
# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Drop the DB Service created by start-db-service.sh

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/common/utility.sh

dbpod=`kubectl get po | grep oracle-db | cut -f1 -d " " `
kubectl delete -f ${scriptDir}/common/oracle.db.yaml  --ignore-not-found

if [ -z ${dbpod} ]; then
  echo "Couldn't find oarcle-db pod in [default] namesapce"
else
  checkPodDelete  ${dbpod} default
fi
