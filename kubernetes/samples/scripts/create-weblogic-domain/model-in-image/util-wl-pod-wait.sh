#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a utility script for waiting until a domain's pods have
# all exited, or waiting until a domain's pods have all reached
# the ready state plus have reached the domain's restart version.
# It exits non-zero on a failure. See 'usage()' below for details.
#

set -eu
set -o pipefail

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

expected=0
timeout_secs=600
syntax_error=false

function usage() {

  cat << EOF

  Usage:

  $(basename $0) [-n mynamespace] [-d mydomainuid] [-p pod_count] [-t timeout_secs ] 

    pod_count > 0: Wait until exactly 'pod_count' WebLogic pods for a domain
                   are all (a) ready, and (b) have the same domain restartVersion
                   as the current domain resource's restartVersion. Exits non-zero
                   if 'timeout_secs' is reached before 'pod_count' is reached.

    pod_count = 0: Wait until there are no running WebLogic pods for a domain.
                   Exits non-zero if 'timeout_secs' is reached before a zero
                   'pod_count' is reached.

  Parameters:

    -d <domain_uid>     : Defaults to \$DOMAIN_UID if set, 'sample-domain1' otherwise.
    -n <namespace>      : Defaults to \$DOMAIN_NAMESPACE if set, 'DOMAIN_UID-ns' otherwise.
    -p <pod-count>      : Number of pods to wait for. Default is '$expected'.
    -t <timeout-secs>   : Defaults to '$timeout_secs'.
    -?                  : This help.

EOF
}

while [ ! "${1:-}" = "" ]; do
  if [ ! "$1" = "-?" ] && [ "${2:-}" = "" ]; then
    syntax_error=true
    break
  fi
  case "$1" in
    -n) DOMAIN_NAMESPACE="${2}"
        ;;
    -d) DOMAIN_UID="${2}"
        ;;
    -t) timeout_secs="$2"
        case "$2" in
          ''|*[!0-9]*) syntax_error=true ;;
        esac
        ;;
    -p) expected="$2"
        case "$2" in
          ''|*[!0-9]*) syntax_error=true ;;
        esac
        ;;
    -?) usage
        exit 0
        ;;
    *)  syntax_error=true
        ;;
  esac
  shift
  shift
done

if [ "$syntax_error" = "true" ]; then
  echo "@@ Error: Syntax error when calling $(basename $0). Pass '-?' for usage."
  exit 1
fi

cur_pods=0
reported=0
origRV="--not-known--"

# Loop until we reach the desired pod count for pods at the desired restart version, or
# until we reach the timeout.

while [ 1 -eq 1 ]; do

  #
  # Get the current domain resource's spec.restartVersion. If this fails, then
  # assume the domain resource isn't deployed and that the restartVersion is "".
  #

  set +e
  currentRV=$(kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.restartVersion}' 2>&1)
  [ ! $? -eq 0 ] && currentRV=""
  set -e

  #
  # Force new reporting for the rare case where domain resource RV changed since we
  # last reported.
  #

  if [ ! "$origRV" = "$currentRV" ]; then
    [ "$reported" = "1" ] && echo
    reported=0
    origRV="$currentRV"
  fi

  #
  # If 'expected' = 0, get the current number of pods regardless of there
  # restart version or ready state. If "expected != 0" get the number
  # of ready pods at the current domain resource restart version.
  # Note that grep returns non-zero if it doesn't find anything (sigh), 
  # so we disable error checking and cross-fingers...
  #

  set +e
  if [ ! "$expected" = "0" ]; then
    cur_pods=$( kubectl -n ${DOMAIN_NAMESPACE} get pods \
        -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}",weblogic.domainRestartVersion="$currentRV" \
        -o=jsonpath='{range .items[*]}{.status.containerStatuses[?(@.name=="weblogic-server")].ready}{"\n"}{end}' \
        | grep "true" | wc -l ) 
    out_str="for ready WebLogic pod count to reach '$expected' for restart version '$currentRV'"
  else
    cur_pods=$( kubectl -n ${DOMAIN_NAMESPACE} get pods \
        -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
        -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
        | wc -l ) 
    out_str="for WebLogic pod count to reach '0'"
  fi

  #
  # Report the current state to stdout. Exit 0 if we've reached our
  # goal, exit non-zero if we've reached our time-out.
  #

  out_str+=", ns=$DOMAIN_NAMESPACE"
  out_str+=", domainUID=$DOMAIN_UID"
  out_str+=", timeout_secs='$timeout_secs'"
  out_str+=", cur_pods='$cur_pods'"
  out_str+=", cur_seconds='$SECONDS'"

  if [ $reported -eq 0 ]; then
    echo -n "@@ Info: Waiting $out_str:"
    reported=1
  else
    echo -n " $cur_pods"
  fi

  if [ $cur_pods -eq $expected ]; then
    echo ". Total seconds=$SECONDS."
    exit 0
  fi

  if [ $SECONDS -ge $timeout_secs ]; then
    echo
    echo "@@ Error: Timeout waiting $out_str."
    exit 1
  fi

  sleep 3
done
