#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -o errexit

script="${BASH_SOURCE[0]}"

function usage {
  echo "usage: ${script} [-a <archive>] [-s <script_file>] [-p <properties_file>]"
  echo "  -a Application name "
  echo "  -u Script file "
  echo "  -p Properties file "
  echo "  -h Help"
  exit $1
}

while getopts ":h:a:s:p:" opt; do
  case $opt in
    a) archive="${OPTARG}"
    ;;
    s) script_file="${OPTARG}"
    ;;
    p) properties_file="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

MW_HOME=/u01/oracle
WL_HOME=${MW_HOME}/wlserver


# source the WLS env
echo "Setting up build environment"
source ${WL_HOME}/server/bin/setWLSEnv.sh

# call ant all to build
cd /application
ant all