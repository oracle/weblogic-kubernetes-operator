#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# TBD add doc here
#     turn parms into -p count, -t seconds, -n namespace, -d domain_uid
#     note that this script waits until all pods are ready and are at the domain's current restart version
#

set -eu
set -o pipefail

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

expected=${1:-0}
timeout_secs=${2:-240}

cur_pods=0
reported=0

currentRV=`kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.restartVersion}'`

while [ 1 -eq 1 ]; do

  # WL Server pods are the only pods with the weblogic.serverName label

  # grep returns non-zero if it doesn't find anything (sigh), so disable error checking and cross-fingers...
  set +e
  cur_pods=$(
    kubectl -n ${DOMAIN_NAMESPACE} get pods \
      -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}",weblogic.domainRestartVersion="$currentRV" \
      -o=jsonpath='{range .items[*]}{.status.containerStatuses[?(@.name=="weblogic-server")].ready}{"\n"}{end}' \
    | grep "true" | wc -l \
  )
  set -e

  out_str="for ready WebLogic pod count to reach '$expected' for restart version '$currentRV'"
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
