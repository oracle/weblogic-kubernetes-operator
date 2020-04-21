#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a utility script that waits until a domain's pods have
# all exited, or waits until a domain's pods have all reached
# the ready state plus have have the domain resource's domain
# restart version and the domain resource's image.
# It exits non-zero on a failure. 
# 
# See 'usage()' below for the command line and other details.
#

set -eu
set -o pipefail

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
[ -e "$WORKDIR/env-custom.sh" ] && source $WORKDIR/env-custom.sh

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

expected=0
timeout_secs=600
syntax_error=false
verbose=false
report_interval=10

function usage() {

  cat << EOF

  Usage:

  $(basename $0) [-n mynamespace] [-d mydomainuid] [-p pod_count] [-t timeout_secs ] [-v]

    pod_count > 0: Wait until exactly 'pod_count' WebLogic server pods for a domain
                   are all (a) ready, (b) have the same domainRestartVersion
                   as the current domain resource's domainRestartVersion, (c)
                   have the same image as the current domain resource's image.

    pod_count = 0: Wait until there are no running WebLogic server pods for a domain.
                   Exits non-zero if 'timeout_secs' is reached before a zero

    Exits non-zero if 'timeout_secs' is reached before 'pod_count' is reached.

  Parameters:

    -d <domain_uid>     : Defaults to \$DOMAIN_UID if set, 'sample-domain1' otherwise.
    -n <namespace>      : Defaults to \$DOMAIN_NAMESPACE if set, 'DOMAIN_UID-ns' otherwise.
    -p <pod-count>      : Number of pods to wait for. Default is '$expected'.
    -t <timeout-secs>   : Defaults to '$timeout_secs'.
    -v                  : Verbose. Show wl pods and introspector job state as when they change.
    -?                  : This help.

EOF
}

while [ ! "${1:-}" = "" ]; do
  if [ ! "$1" = "-?" ] && [ ! "$1" = "-v" ] && [ "${2:-}" = "" ]; then
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
    -v) verbose=true
        report_interval=30
        shift
        continue
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

function timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

tempfile() {
  mktemp /tmp/$(basename "$0").$PPID.$(timestamp).XXXXXX
}

tmpfileorig=$(tempfile)
tmpfilecur=$(tempfile)

trap "rm -f $tmpfileorig $tmpfilecur" EXIT

cur_pods=0
reported=0
last_pod_count_secs=$SECONDS
origRV="--not-known--"
origImage="--not-known--"

# be careful! if changing jpath, then it must correspond with the regex below
jpath=''
jpath+='{range .items[*]}'
  jpath+='{" name="}'
  jpath+='{.metadata.name}'
  jpath+='{" domainRestartVersion="}'
  jpath+='{.metadata.labels.weblogic\.domainRestartVersion}'
  jpath+='{" image="}'
  jpath+='{.status.containerStatuses[?(@.name=="weblogic-server")].image}'
  jpath+='{" ready="}'
  jpath+='{.status.containerStatuses[?(@.name=="weblogic-server")].ready}'
  jpath+='{" phase="}'
  jpath+='{.status.phase}'
  jpath+='{"\n"}'
jpath+='{end}'


# Loop until we reach the desired pod count for pods at the desired restart version, or
# until we reach the timeout.

while [ 1 -eq 1 ]; do

  #
  # Get the current domain resource's spec.restartVersion. If this fails, then
  # assume the domain resource isn't deployed and that the restartVersion is "".
  #

  set +e
  currentRV=$(kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.restartVersion}' 2>&1)
  if [ $? -ne 0 ]; then
    if [ $expected -ne 0 ]; then
      echo "@@ Error: Could not obtain 'spec.restartVersion' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed?"
      exit 1
    else
      currentRV=''
    fi
  fi

  currentImage=$(kubectl -n sample-domain1-ns get domain sample-domain1 -o=jsonpath='{.spec.image}' 2>&1)
  if [ $? -ne 0 ]; then
    if [ $expected -ne 0 ]; then
      echo "@@ Error: Could not obtain 'spec.image' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed?"
      exit 1
    else
      currentImage=''
    fi
  fi
  set -e

  #
  # Force new reporting for the rare case where domain resource RV or 
  # image changed since we last reported.
  #

  if [ ! "$origRV" = "$currentRV" ] || [ ! "$origImage" = "$currentImage" ]; then
    [ "$reported" = "1" ] && echo
    reported=0
    origRV="$currentRV"
    origImage="$currentImage"
  fi

  #
  # If 'expected' = 0, get the current number of pods regardless of their
  # restart version, image, or ready state. 
  # 
  # If "expected != 0" get the number of ready pods with the current domain
  # resource restart version and image. 
  #
  # (Note that grep returns non-zero if it doesn't find anything (sigh), 
  # so we disable error checking and cross-fingers...)
  #

  if [ "$expected" = "0" ]; then

    cur_pods=$( kubectl -n ${DOMAIN_NAMESPACE} get pods \
        -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
        -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
        | wc -l ) 

    out_str="Waiting for WebLogic server pod count to reach '0'"

  else

    regex="domainRestartVersion=$currentRV"
    regex+=" image=$currentImage"
    regex+=" ready=true"

    set +e # disable error checks as grep returns non-zero when it finds nothing (sigh)
    cur_pods=$( kubectl -n ${DOMAIN_NAMESPACE} get pods \
        -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
        -o=jsonpath="$jpath" \
        | grep "$regex" | wc -l )
    set -e

    out_str="Waiting for exactly '$expected' WebLogic server pods to reach ready='true', image='$currentImage', and domainRestartVersion='$currentRV'"

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

  if [ "$verbose" = "false" ]; then
    if [ $reported -eq 0 ]; then
      echo -n "@@ Info: $out_str:"
      reported=1
      echo -n " $cur_pods"
      last_pod_count_secs=$SECONDS
  
    elif [ $((SECONDS - last_pod_count_secs)) -gt $report_interval ] \
         || [ $cur_pods -eq $expected ]; then
      echo -n " $cur_pods"
      last_pod_count_secs=$SECONDS

    fi
  else

    kubectl -n ${DOMAIN_NAMESPACE} get pods \
      -l weblogic.domainUID="${DOMAIN_UID}" \
      -o=jsonpath="$jpath" > $tmpfilecur

    set +e
    diff -q $tmpfilecur $tmpfileorig 2>&1 > /dev/null
    diff_res=$?
    set -e
    if [ ! $diff_res -eq 0 ] \
       || [ $((SECONDS - last_pod_count_secs)) -gt $report_interval ] \
       || [ $cur_pods -eq $expected ]; then
      echo
      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: $out_str. Current Weblogic server and introspector pods:"
      echo
      cat $tmpfilecur
      cp $tmpfilecur $tmpfileorig
      last_pod_count_secs=$SECONDS
    fi
  fi

  if [ $cur_pods -eq $expected ]; then
    if [ ! "$verbose" = "true" ]; then
      echo -n ". "
    else
      echo -n "@@ "
    fi
    echo "Success! Total seconds=$SECONDS."
    exit 0
  fi

  if [ $SECONDS -ge $timeout_secs ]; then
    echo
    echo "@@ Error: Timeout waiting $out_str."
    exit 1
  fi

  sleep 1
done
