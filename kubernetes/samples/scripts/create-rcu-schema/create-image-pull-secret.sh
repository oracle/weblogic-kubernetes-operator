#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Create ImagePullSecret to pull Oracle DB and FMW Infrastructure Image

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

function usage {
  echo "usage: ${script} -u <username> -p <password> -e <email> -s <secret>  [-h]"
  echo "  -u Oracle Container Registry User Name (needed)"
  echo "  -p Oracle Container Registry Password (needed)"
  echo "  -e email (needed)"
  echo "  -s Generated Secret (optional) "
  echo "      (default: docker-store) "
  echo "  -h Help"
  exit $1
}

while getopts ":u:p:s:e:" opt; do
  case $opt in
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    e) email="${OPTARG}"
    ;;
    s) secert="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${username} ]; then
  echo "${script}: -u <username> must be specified."
  usage 1
fi

if [ -z ${password} ]; then
  echo "${script}: -p <password> must be specified."
  usage 1
fi

if [ -e ${email} ]; then
  echo "${script}: -p <email> must be specified."
  usage 1
fi

if [ -z ${secret} ]; then
  secret="docker-store"
fi

kubectl delete secret/${secret} --ignore-not-found
echo "Creating ImagePullSecret on container-registry.oracle.com"
kubectl create secret docker-registry ${secret} --docker-server=container-registry.oracle.com --docker-username=${username} --docker-password=${password} --docker-email=${email}
