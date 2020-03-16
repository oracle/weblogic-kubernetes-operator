# !/bin/sh
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Helper script for creating and labeling a Kubernetes secret.  The secret
# is labeled with the specified domain-uid.
#
# Usage:
#
# ./create_secret.sh [-n mynamespace] [-d mydomainuid] -s mysecretname [-l key1=val1] [-l key2=val2] ...
# 
# -d <domain_uid>     : Defaults to $DOMAIN_UID if DOMAIN_UID is set, 'sample-domain1' otherwise.
# -n <namespace>      : Defaults to $DOMAIN_NAMESPACE if DOMAIN_NAMESPACE is set, 'DOMAIN_UID-ns' otherwise.
# -s <secret-name>    : Name of secret. Required.
# -l <key-value-pair> : Secret 'literal' key/value pair. Can be specified more than once. 
#                       This script doesn't support spaces in the key value pair.
# -f <filename>       : Secret 'file-name'. Can be specified more than once. 
#                       Key will be the file-name, value will be file contents.
# -fk <key> <filename>: Secret 'file-name' key/value pair. Can be specified more than once.
#                       Key will be the key-name, value will be file contents.

set -e

DOMAIN_UID="${DOMAIN_UID:-sample-domain1}"
NAMESPACE="${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}"
SECRET_NAME=""
LITERALS=""
FILENAMES=""

while [ ! "$1" = "" ]; do
  if [ "$2" = "" ]; then
    echo Syntax Error
    exit 1
  fi
  case "$1" in
    -s) SECRET_NAME="${2}"
        ;;
    -n) NAMESPACE="${2}"
        ;;
    -d) DOMAIN_UID="${2}"
        ;;
    -l) LITERALS="${LITERALS} --from-literal=${2}"
        ;;
    -f) FILENAMES="${FILENAMES} --from-file=${2}"
        ;;
    -fk) FILENAMES="${FILENAMES} --from-file=${2}=${3}"
        shift
        ;;
    *)  echo Syntax Error
        exit 1
        ;;
  esac
  shift
  shift
done

set -eu

kubectl -n $NAMESPACE delete secret         $SECRET_NAME --ignore-not-found
kubectl -n $NAMESPACE create secret generic $SECRET_NAME $LITERALS $FILENAMES
kubectl -n $NAMESPACE label  secret         $SECRET_NAME weblogic.domainUID=$DOMAIN_UID

