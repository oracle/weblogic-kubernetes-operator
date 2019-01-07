#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Perform preparation based on the specified cluster type

function usage {
  echo usage: ${script} -t clusterTpe -i file [-h]
  echo "  -i Directory, must be specified."
  echo "  -t Cluster type, CONFIGURED or DYNAMIC, default is DYNAMIC."
  echo "  -h Help"
  exit $1
}


while getopts "hi:t:" opt; do
  case $opt in
    i) externalFilesTmpDir="${OPTARG}"
    ;;
    t) clusterType="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

echo Preparing the scripts with clusterType $clusterType

if [ "${clusterType}" = "CONFIGURED" ]; then
  source_model=${externalFilesTmpDir}/wdt_model_configured.yaml
else
  source_model=${externalFilesTmpDir}/wdt_model_dynamic.yaml
fi

cp $source_model ${externalFilesTmpDir}/wdt_model.yaml

