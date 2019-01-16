#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Perform preparation based on the specified cluster type

function usage {
  echo usage: ${script} -i file [-h]
  echo "  -i Directory, must be specified."
  echo "  -h Help"
  exit $1
}


while getopts "hi:" opt; do
  case $opt in
    i) externalFilesTmpDir="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

echo Preparing the model script

cp ${externalFilesTmpDir}/wdt_model_dynamic.yaml ${externalFilesTmpDir}/wdt_model.yaml

