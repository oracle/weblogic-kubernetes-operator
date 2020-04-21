#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a helper script that can save an OPSS key wallet from a
# running domain's introspector configmap to a file, and/or restore
# an OPSS key wallet file to a Kubernetes secret for use by a domain
# that you're about to run.
#
# For command line details, pass '-?' or see 'usage_exit()' below.
#
# Defaults can optionally be changed via env vars:
#
#   DOMAIN_UID       : sample-domain1
#   DOMAIN_NAMESPACE : DOMAIN_UID-ns
#   WALLET_FILE      : ./ewallet.p12
#   WALLET_SECRET    : DOMAIN_UID-opss-walletfile-secret
#

set -e
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
[ -e "$WORKDIR/env-custom.sh" ] && source $WORKDIR/env-custom.sh

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}
WALLET_FILE=${WALLET_FILE:-./ewallet.p12}
WALLET_SECRET=${WALLET_SECRET:-${DOMAIN_UID}-opss-walletfile-secret}

usage_exit() {
cat << EOF

  Usage: 

    $(basename $0) -s [-wf wallet-file-name] 
    $(basename $0) -r [-wf wallet-file-name] [-ws wallet-file-secret]
    $(basename $0) -s -r [-wf wallet-file-name] [-ws wallet-file-secret]

    Save an OPSS key wallet from a running domain's introspector configmap
    to a file, and/or restore an OPSS key wallet file to a Kubernetes secret
    for use by a domain that you're about to run.

  Parameters:

    -d   <domain-uid>   Domain UID. 
                        Default is '\$DOMAIN_UID' if set, '-sample-domain1' otherwise.

    -n   <namespace>    Kubernetes namespace. 
                        Default is '\$DOMAIN_NAMESPACE' if set, 'DOMAIN_UID-ns' otherwise.

    -s                  Save an OPSS wallet file from an introspector
                        configmap to a file. (See also '-wf'.)
                        Default is '\$WALLET_SECRET' if set, 
                        'DOMAIN_UID-opss-walletfile-secret' otherwise.

    -r                  Restore an OPSS wallet file to a Kubernetes secret.
                        (See also '-wf' and '-ws').

    -wf  <wallet-file>  Name of OPSS wallet file on local file system.
                        Default is '\$WALLET_FILE' if set, './ewallet.p12' otherwise.

    -ws  <secret-name>  Name of Kubernetes secret to create from the OPSS wallet file.
                        This must match the 'configuration.opss.walletFileSecret'
                        configured in your domain resource.
                        Ignored if '-r' not specified. 
                        Default is '\$WALLET_SECRET' if set, 
                        and 'DOMAIN_UID-opss-walletfile-secret' otherwise.

    -?                  Output this help message.

  Examples:

    Save an OPSS key wallet from a running domain:
      $(basename $0) -s

    Restore the OPSS key wallet to a secret for use by a domain you're about to run:
      $(basename $0) -r

EOF

  exit 0
}

syntax_error_exit() {
  echo "@@ Syntax error: Use '-?' for usage."
  exit 1
}

SAVE_WALLET=0
RESTORE_WALLET=0

while [ ! "$1" = "" ]; do
  case "$1" in
    -n)  [ -z "$2" ] && syntax_error_exit
         DOMAIN_NAMESPACE="${2}"
         shift
         ;;
    -d)  [ -z "$2" ] && syntax_error_exit
         DOMAIN_UID="${2}"
         shift
         ;;
    -s)  SAVE_WALLET=1
         ;;
    -r)  RESTORE_WALLET=1
         ;;
    -ws) [ -z "$2" ] && syntax_error_exit
         WALLET_SECRET="${2}"
         shift
         ;;
    -wf) [ -z "$2" ] && syntax_error_exit
         WALLET_FILE="${2}"
         shift
         ;;
    -?)  usage_exit
         ;;
    *)   syntax_error_exit
         ;;
  esac
  shift
done

[ ${SAVE_WALLET} -eq 0 ] && [ ${RESTORE_WALLET} -eq 0 ] && syntax_error_exit

set -eu

if [ ${SAVE_WALLET} -eq 1 ] ; then
  echo "@@ Info: Saving wallet from from configmap '${DOMAIN_UID}-weblogic-domain-introspect-cm' in namespace '${DOMAIN_NAMESPACE}' to file '${WALLET_FILE}'."
  kubectl -n ${DOMAIN_NAMESPACE} \
    get configmap ${DOMAIN_UID}-weblogic-domain-introspect-cm \
    -o jsonpath='{.data.ewallet\.p12}' \
    > ${WALLET_FILE}
fi

if [ ! -f "$WALLET_FILE" ]; then
  echo "@@ Error: Wallet file '$WALLET_FILE' not found."
  exit 1
fi

FILESIZE=$(du -k "$WALLET_FILE" | cut -f1)
if [ $FILESIZE = 0 ]; then
  echo "@@ Error: Wallet file '$WALLET_FILE' is empty. Is this a JRF domain? The wallet file will be empty for a non-RCU/non-JRF domain."
  exit 1
fi

if [ ${RESTORE_WALLET} -eq 1 ] ; then
  echo "@@ Info: Creating secret '${WALLET_SECRET}' in namespace '${DOMAIN_NAMESPACE}' for wallet file '${WALLET_FILE}', domain uid '${DOMAIN_UID}'."
  $SCRIPTDIR/util-create-secret.sh \
    -n ${DOMAIN_NAMESPACE} \
    -d ${DOMAIN_UID} \
    -s ${WALLET_SECRET} \
    -f walletFile=${WALLET_FILE}
fi
