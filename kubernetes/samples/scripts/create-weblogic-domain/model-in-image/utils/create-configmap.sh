# !/bin/sh
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

function usage() {

  cat << EOF
  
  This is a helper script for creating and labeling a Kubernetes configmap.
  The configmap is labeled with the specified domain-uid.
  
  Usage:
  
  $(basename $0) -c configmapname \\
                [-n mynamespace]  \\
                [-d mydomainuid]  \\
                [-f filename_or_dir] [-f filename_or_dir] ...
  
  -d <domain_uid>     : Defaults to 'sample-domain1'.

  -n <namespace>      : Defaults to 'sample-domain1-ns' otherwise.

  -c <configmap-name> : Name of configmap. Required.

  -f <filename_or_dir>: File or directory location. Can be specified
                        more than once. Key will be the file-name(s),
                        value will be file contents. Required.

  -dry kubectl        : Show the kubectl commands (prefixed with 'dryun:')
                        but do not perform them.

  -dry yaml           : Show the yaml (prefixed with 'dryun:')
                        but do not execute it.

EOF
}

set -e
set -o pipefail

DOMAIN_UID="sample-domain1"
DOMAIN_NAMESPACE="sample-domain1-ns"
CONFIGMAP_NAME=""
FILENAMES=""
DRY_RUN=""

while [ ! "$1" = "" ]; do
  if [ ! "$1" = "-?" ] && [ "$2" = "" ]; then
    echo "Syntax Error. Pass '-?' for help."
    exit 1
  fi
  case "$1" in
    -c)   CONFIGMAP_NAME="${2}" ;;
    -n)   DOMAIN_NAMESPACE="${2}" ;;
    -d)   DOMAIN_UID="${2}" ;;
    -f)   FILENAMES="${FILENAMES}--from-file=${2} " ;;
    -dry) DRY_RUN="${2}"
          case "$DRY_RUN" in
            kubectl|yaml) ;;
            *) echo "Error: Syntax Error. Pass '-?' for usage."
               exit 1
               ;;
          esac
          ;;
    -?)   usage ; exit 1 ;;
    *)    echo "Syntax Error. Pass '-?' for help." ; exit 1 ;;
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

set -eu

if [ "$DRY_RUN" = "kubectl" ]; then

cat << EOF
dryrun:kubectl -n $DOMAIN_NAMESPACE delete configmap $CONFIGMAP_NAME --ignore-not-found
dryrun:kubectl -n $DOMAIN_NAMESPACE create configmap $CONFIGMAP_NAME $FILENAMES
dryrun:kubectl -n $DOMAIN_NAMESPACE label  configmap $CONFIGMAP_NAME weblogic.domainUID=$DOMAIN_UID
EOF

elif [ "$DRY_RUN" = "yaml" ]; then

  echo "dryrun:---"
  echo "dryrun:"

  # don't change indent of the sed append commands - the spaces are significant
  #   (we use an ancient form of sed append to stay compatible with old bash on mac)
  kubectl -n $DOMAIN_NAMESPACE \
    create configmap $CONFIGMAP_NAME $FILENAMES \
    --dry-run -o yaml \
  \
  | sed -e '/ name:/a\
  labels:' \
  | sed -e '/labels:/a\
    weblogic.domainUID:' \
  | sed "s/domainUID:/domainUID: $DOMAIN_UID/" \
  | grep -v creationTimestamp \
  | sed "s/^/dryrun:/"

else

  set -x

  kubectl -n $DOMAIN_NAMESPACE delete configmap $CONFIGMAP_NAME --ignore-not-found
  kubectl -n $DOMAIN_NAMESPACE create configmap $CONFIGMAP_NAME $FILENAMES
  kubectl -n $DOMAIN_NAMESPACE label  configmap $CONFIGMAP_NAME weblogic.domainUID=$DOMAIN_UID

fi

