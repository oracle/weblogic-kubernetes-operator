#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -o errexit

script="${BASH_SOURCE[0]}"

function usage {
  echo "usage: ${script}"
  exit $1
}

MW_HOME=/u01/oracle
WL_HOME=${MW_HOME}/wlserver

if [ -z ${targets+x} ]; then 
targets=""
fi

if [ -z ${sysprops+x} ]; then 
sysprops=""
fi

# source the WLS env
echo "Setting up build environment"
source ${WL_HOME}/server/bin/setWLSEnv.sh

# call ant all to build
cd /application
ant ${sysprops} ${targets}

chmod -R 777 *
