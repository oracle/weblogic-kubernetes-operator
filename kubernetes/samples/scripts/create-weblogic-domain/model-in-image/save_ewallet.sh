#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: save_ewallet.sh
#
#

# TBD 
#   - refactor - and move wallet to a dedicated secret
#   - advance script to have both save and restore options (allow specifying both)
#   - for save option,

set -e
SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}
DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
SAVE=${SAVE:-ewallet.p12}
RESTORE=${RESTORE:-ewallet.p12}
SECRET=${DOMAIN_UID}-opss-walletfile-secret

usage_exit() {
cat << EOF

  Usage: $(basename $0) ...options...

    Save or restore the OPSS key wallet file from the introspector configmap

  Parameters:

    -r  Restore the OPSS key wallet file from the introspector configmap will be saved as ewallet.p12 in current directory
    -s  Save the OPSS key wallet file for reusing the same infrastructure database for deployment

    You can specify -f, -m, and -s more than once.

  Sample usage:

    Restore the OPSS key wallet
      $(basename $0) -r

    Save the OPSS key wallet (Need to have the
      $(basename $0) -s

EOF

  exit 1
}

getopts_get_optional_argument() {
  eval next_token=\${$OPTIND}
  if [[ -n $next_token && $next_token != -* ]]; then
    OPTIND=$((OPTIND + 1))
    OPTARG=$next_token
  else
    OPTARG=""
  fi
}


SAVE_WALLET=0
RESTORE_WALLET=0

if [ $# -eq 0 ] ; then
  SAVE_WALLET=1
  RESTORE_WALLET=1
else

  while getopts srh OPT
  do
    case $OPT in
    s)
      getopts_get_optional_argument $@
      if [ ! -z ${OPTARG} ] ; then
        SAVE=${OPTARG}
      fi
      SAVE_WALLET=1
       ;;
    r)
      getopts_get_optional_argument $@
      if [ ! -z ${OPTARG} ] ; then
        RESOTRE=${OPTARG}
      fi
      RESTORE_WALLET=1
       ;;
    h) usage_exit
       ;;
    *) usage_exit
       ;;
    esac
  done
  shift $(($OPTIND - 1))
  [ ! -z "$1" ] && usage_exit

fi

if [ ${RESTORE_WALLET} -eq 1 ] ; then
  kubectl -n ${DOMAIN_NAMESPACE} get configmap ${DOMAIN_UID}-weblogic-domain-introspect-cm \
        -o jsonpath='{.data.ewallet\.p12}' > ${RESTORE}
fi

if [ ${SAVE_WALLET} -eq 1 ] ; then
  $SCRIPTDIR/create_secret.sh -s ${SECRET} \
    -fk WalletFile ${SAVE}
fi
