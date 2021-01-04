# !/bin/sh
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

function usage() {

  cat << EOF

  This is a helper script for creating and labeling a Kubernetes secret.
  The secret is labeled with the specified domain-uid.
 
  Usage:
 
  $(basename $0) [-n mynamespace] [-d mydomainuid] \\
    -s mysecretname [-l key1=val1] [-l key2=val2] [-f key=fileloc ]...
  
  -d <domain_uid>     : Defaults to 'sample-domain1' otherwise.

  -n <namespace>      : Defaults to 'sample-domain1-ns' otherwise.

  -s <secret-name>    : Name of secret. Required.

  -l <key-value-pair> : Secret 'literal' key/value pair, for example
                        '-l password=abc123'. Can be specified more than once. 

  -f <key-file-pair>  : Secret 'file-name' key/file pair, for example
                        '-l walletFile=./ewallet.p12'.
                        Can be specified more than once. 

  -dry kubectl        : Show the kubectl commands (prefixed with 'dryun:')
                        but do not perform them. 

  -dry yaml           : Show the yaml (prefixed with 'dryun:') but do not
                        execute it. 

  -?                  : This help.

  Note: Spaces are not supported in the '-f' or '-l' parameters.
   
EOF
}

set -e
set -o pipefail

DOMAIN_UID="sample-domain1"
NAMESPACE="sample-domain1-ns"
SECRET_NAME=""
LITERALS=""
FILENAMES=""
DRY_RUN="false"

while [ ! "${1:-}" = "" ]; do
  if [ ! "$1" = "-?" ] && [ "${2:-}" = "" ]; then
    echo "Syntax Error. Pass '-?' for usage."
    exit 1
  fi
  case "$1" in
    -s)   SECRET_NAME="${2}" ;;
    -n)   NAMESPACE="${2}" ;;
    -d)   DOMAIN_UID="${2}" ;;
    -l)   LITERALS="${LITERALS} --from-literal=${2}" ;;
    -f)   FILENAMES="${FILENAMES} --from-file=${2}" ;;
    -dry) DRY_RUN="${2}"
          case "$DRY_RUN" in
            kubectl|yaml) ;;
            *) echo "Error: Syntax Error. Pass '-?' for usage."
               exit 1
               ;;
          esac
          ;;
    -?)   usage ; exit 1 ;;
    *)    echo "Syntax Error. Pass '-?' for usage." ; exit 1 ;;
  esac
  shift
  shift
done

if [ -z "$SECRET_NAME" ]; then
  echo "Error: Syntax Error. Must specify '-s'. Pass '-?' for usage."
  exit 1
fi

if [ -z "${LITERALS}${FILENAMES}" ]; then
  echo "Error: Syntax Error. Must specify at least one '-l' or '-f'. Pass '-?' for usage."
  exit
fi

set -eu

if [ "$DRY_RUN" = "kubectl" ]; then

cat << EOF
dryrun:
dryrun:echo "@@ Info: Setting up secret '$SECRET_NAME'."
dryrun:
dryrun:kubectl -n $NAMESPACE delete secret \\
dryrun:  $SECRET_NAME \\
dryrun:  --ignore-not-found
dryrun:kubectl -n $NAMESPACE create secret generic \\
dryrun:  $SECRET_NAME \\
dryrun:  $LITERALS $FILENAMES
dryrun:kubectl -n $NAMESPACE label  secret \\
dryrun:  $SECRET_NAME \\
dryrun:  weblogic.domainUID=$DOMAIN_UID
dryrun:
EOF

elif [ "$DRY_RUN" = "yaml" ]; then

  echo "dryrun:---"
  echo "dryrun:"

  # don't change indent of the sed '/a' commands - the spaces are significant
  # (we use an old form of sed append to stay compatible with old bash on mac)

  kubectl -n $NAMESPACE \
  \
  create secret generic \
  $SECRET_NAME $LITERALS $FILENAMES \
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

  kubectl -n $NAMESPACE delete secret         $SECRET_NAME --ignore-not-found
  kubectl -n $NAMESPACE create secret generic $SECRET_NAME $LITERALS $FILENAMES
  kubectl -n $NAMESPACE label  secret         $SECRET_NAME weblogic.domainUID=$DOMAIN_UID

fi
