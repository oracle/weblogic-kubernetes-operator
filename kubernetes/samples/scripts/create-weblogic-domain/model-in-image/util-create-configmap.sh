# !/bin/sh
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

function usage() {

  cat << EOF
  
  This is a helper script for creating and labeling a Kubernetes configmap.  The configmap
  is labeled with the specified domain-uid.
  
  Usage:
  
  $(basename $0) -c configmapname \\
                [-n mynamespace]  \\
                [-d mydomainuid]  \\
                [-f filename_or_dir] [-f filename_or_dir] ...
  
  -d <domain_uid>     : Defaults to \$DOMAIN_UID if set, 'sample-domain1' otherwise.
  -n <namespace>      : Defaults to \$DOMAIN_NAMESPACE if set, 'sample-domain1-ns' otherwise.
  -c <configmap-name> : Name of configmap. Required.
  -f <filename_or_dir>: File or directory location. Can be specified more than once. 
                        Key will be the file-name(s), value will be file contents. Required.
EOF
}

set -e
set -o pipefail

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
[ -e "$WORKDIR/env-custom.sh" ] && source $WORKDIR/env-custom.sh

DOMAIN_UID="${DOMAIN_UID:-sample-domain1}"
DOMAIN_NAMESPACE="${DOMAIN_NAMESPACE:-sample-domain1-ns}"
CONFIGMAP_NAME=""
FILENAMES=""

while [ ! "$1" = "" ]; do
  if [ ! "$1" = "-?" ] && [ "$2" = "" ]; then
    echo "Syntax Error. Pass '-?' for help."
    exit 1
  fi
  case "$1" in
    -c) CONFIGMAP_NAME="${2}"
        ;;
    -n) DOMAIN_NAMESPACE="${2}"
        ;;
    -d) DOMAIN_UID="${2}"
        ;;
    -f) FILENAMES="${FILENAMES} --from-file=${2}"
        ;;
    -?) usage
        exit 1
        ;;
    *)  echo "Syntax Error. Pass '-?' for help."
        exit 1
        ;;
  esac
  shift
  shift
done

if [ -z "$CONFIGMAP_NAME" ]; then
  echo "Error: Missing '-c' argument. Pass '-?' for help."
  exit 1
fi

if [ -z "$FILENAMES" ]; then
  echo "Error: Missing '-f' argument. Pass '-?' for help."
  exit 1
fi

set -eux

kubectl -n $DOMAIN_NAMESPACE delete configmap $CONFIGMAP_NAME --ignore-not-found
kubectl -n $DOMAIN_NAMESPACE create configmap $CONFIGMAP_NAME $FILENAMES
kubectl -n $DOMAIN_NAMESPACE label  configmap $CONFIGMAP_NAME weblogic.domainUID=$DOMAIN_UID

