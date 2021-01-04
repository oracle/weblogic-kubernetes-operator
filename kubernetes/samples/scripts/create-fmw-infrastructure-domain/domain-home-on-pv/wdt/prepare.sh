#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# Perform preparation based on the specified cluster type

function usage {
  echo usage: ${script} -i file -t type [-h]
  echo "  -i Directory, must be specified."
  echo "  -t Domain Type [JRF or RestrictedJRF] , must be specified."
  echo "  -h Help"
  exit $1
}

while getopts "h:i:t:" opt; do
  case $opt in
    i) externalFilesTmpDir="${OPTARG}"
    ;;
    t) domain_type="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

echo Preparing the model script
echo "fmwDomainType is [${domain_type}]"
# JRF does not support Dynamic Cluster Model 
if [ "${domain_type}" == "JRF" ]; then 
   cp ${externalFilesTmpDir}/wdt_model_configured.yaml ${externalFilesTmpDir}/wdt_model.yaml
fi 

if [ "${domain_type}" == "RestrictedJRF" ]; then 
   cp ${externalFilesTmpDir}/wdt_model_restricted_jrf_configured.yaml ${externalFilesTmpDir}/wdt_model.yaml
fi 
