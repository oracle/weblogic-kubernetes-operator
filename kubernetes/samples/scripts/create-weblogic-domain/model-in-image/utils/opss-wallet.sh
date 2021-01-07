#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a helper script for JRF type domains that can save an OPSS key
# wallet from a running domain's introspector configmap to a file, and/or
# restore an OPSS key wallet file to a Kubernetes secret for use by a
# domain that you're about to run. 
#
# For command line details, pass '-?' or see 'usage_exit()' below.
#

set -e
set -o pipefail

usage_exit() {
cat << EOF

  Usage: 

    $(basename $0) -s [-d domain-uid] [-n namespace] \\
      [-wf wallet-file-name] 

    $(basename $0) -r [-d domain-uid] [-n namespace] \\
      [-wf wallet-file-name] [-ws wallet-file-secret]

    $(basename $0) -r -s [-d domain-uid] [-n namespace] \\
      [-wf wallet-file-name] [-ws wallet-file-secret]

    Save an OPSS key wallet from a running JRF domain's introspector
    configmap to a file, and/or restore an OPSS key wallet file
    to a Kubernetes secret for use by a domain that you're about to run.

  Parameters:

    -d   <domain-uid>   Domain UID. Default 'sample-domain1'.

    -n   <namespace>    Kubernetes namespace. Default 'sample-domain1-ns'.

    -s                  Save an OPSS wallet file from an introspector
                        configmap to a file. (See also '-wf'.)

    -r                  Restore an OPSS wallet file to a Kubernetes secret.
                        (See also '-wf' and '-ws').

    -wf  <wallet-file>  Name of OPSS wallet file on local file system.
                        Default is './ewallet.p12'.

    -ws  <secret-name>  Name of Kubernetes secret to create from the
                        wallet file. This must match the
                        'configuration.opss.walletFileSecret'
                        configured in your domain resource.
                        Ignored if '-r' not specified. 
                        Default is 'DOMAIN_UID-opss-walletfile-secret'.

    -?                  Output this help message.

  Examples:

    Save an OPSS key wallet from a running domain to file './ewallet.p12':
      $(basename $0) -s

    Restore the OPSS key wallet from file './ewallet.p12' to secret
    'sample-domain1-opss-walletfile-secret' for use by a domain
    you're about to run:
      $(basename $0) -r

EOF

  exit 0
}

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

DOMAIN_UID="sample-domain1"
DOMAIN_NAMESPACE="sample-domain1-ns"
WALLET_FILE="ewallet.p12"
WALLET_SECRET=""

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

WALLET_SECRET=${WALLET_SECRET:-$DOMAIN_UID-opss-walletfile-secret}

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
  $SCRIPTDIR/create-secret.sh \
    -n ${DOMAIN_NAMESPACE} \
    -d ${DOMAIN_UID} \
    -s ${WALLET_SECRET} \
    -f walletFile=${WALLET_FILE}
fi
