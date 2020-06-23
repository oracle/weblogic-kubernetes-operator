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

# create /u01/application
mkdir /u01/application
cd /u01/application
#unzip the source application archive
unzip /u01/$ZIP_FILE

# call ant to build
pwd
ls
echo "ant ${sysprops} ${targets}"
ant ${sysprops} ${targets}
