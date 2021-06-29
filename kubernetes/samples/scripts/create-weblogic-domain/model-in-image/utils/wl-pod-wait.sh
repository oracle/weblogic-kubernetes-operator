#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

DOMAIN_UID="sample-domain1"
DOMAIN_NAMESPACE="sample-domain1-ns"
timeout_secs=1000

function usage() {

  cat << EOF

  Usage:

    $(basename $0) [-n mynamespace] [-d mydomainuid] \\
       [-p expected_pod_count] [-t timeout_secs] [-q]

  Description:

    This utility script exits successfully when the designated number of
    WebLogic Server pods in the given WebLogic Kubernetes Operator domain
    reach a 'ready' state and have 'restartVersion', 'introspectVersion',
    'spec.image', and 'spec.serverPod.commonMounts.image' values that match
    their corresponding values in their domain resource.

    If the designated number of pods is zero, then this script exits
    successfully when all pods for the given domain have exited.

    This script exits non-zero if a configurable timeout is reached
    before the target pod count is reached (default $timeout_secs
    seconds). It also exists non-zero if the specified domain
    cannot be found and the target pod count is at least one.

  Parameters:

    -d <domain_uid> : WKO Domain UID. Defaults to '$DOMAIN_UID'.

    -n <namespace>  : Kubernetes namespace.
                      Defaults to '$DOMAIN_NAMESPACE'.

    -p 0            : Wait until there are no running WebLogic Server pods
                      for a domain. The default.

    -p <pod_count>  : Wait until all of the following are true
                      for exactly 'pod_count' WebLogic Server pods
                      in the domain:
                      - ready
                      - same 'weblogic.domainRestartVersion' label value as
                        the domain resource's 'spec.restartVersion'
                      - same 'weblogic.introspectVersion' label value as
                        the domain resource's 'spec.introspectVersion'
                      - same image as the domain resource's 'spec.image'
                      - same image(s) as specified in the domain resource's
                        optional 'spec.serverPod.commonMounts.image'

    -t <timeout>    : Timeout in seconds. Defaults to '$timeout_secs'.

    -q              : Quiet mode. Show only a count of wl pods that
                      have reached the desired criteria.

    -?              : This help.

EOF
}

expected=0
syntax_error=false
verbose=true
report_interval=120

while [ ! "${1:-}" = "" ]; do
  if [ ! "$1" = "-?" ] && [ ! "$1" = "-q" ] && [ "${2:-}" = "" ]; then
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
    -q) verbose=false
        report_interval=30
        shift
        continue
        ;;
    -?) usage
        exit 0
        ;;
    *)  syntax_error=true
        break
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

function tempfile() {
  mktemp /tmp/$(basename "$0").$PPID.$(timestamp).XXXXXX
}

function sortlist() {
  # sort a comma or space separated list
  #   - stdin input, stdout output
  #   - spaces replaced with commas
  #   - blank fields ignored
  #   - output removes any trailing comma
  #   - examples: ""->"" "c,b"->"b,c" "c,b,"->"b,c" "c b"->"b,c"
  tr ' ' '\n'    | \
  tr ',' '\n'    | \
  sort -V        | \
  xargs echo -n  | \
  tr ' ' ','
}
function sortCMImages() {
  # sort "cmimages=;im2,im1;" field assuming comma or space sep list
  #   - stdin input, stdout output
  #   - spaces replaced with commas
  #   - input ignores trailing comma, output removes any trailing comma
  #   - examples: see sortCMImagesUnitTest()
  while read line
  do
    echo -n "$line" | sed 's/\(^.*cmimages=;\).*/\1/'
    echo -n "$line" | sed 's/.*cmimages=;\([^;]*\).*/\1/' | sortlist
    echo "$line"    | sed 's/.*cmimages=;[^;]*\(;.*\)/\1/'
  done
}
function _sortCMImagesUnitTest() {
  local res=$(echo "$1" | sortCMImages)
  if [ ! "$res" = "$2" ]; then
    echo "unit test fail"
    echo " input ='$1'"
    echo " expect='$2'"
    echo " actual='$res'"
    exit 1
  fi
}
function sortCMImagesUnitTest() {
  _sortCMImagesUnitTest "foo=;bar; cmimages=;c,b; bar=;foo;"   "foo=;bar; cmimages=;b,c; bar=;foo;"
  _sortCMImagesUnitTest "foo=;bar; cmimages=; c,b,; bar=;foo;" "foo=;bar; cmimages=;b,c; bar=;foo;"
  _sortCMImagesUnitTest "foo=;bar; cmimages=;; bar=;foo;"      "foo=;bar; cmimages=;; bar=;foo;"
  _sortCMImagesUnitTest "foo=;bar; cmimages=;a ; bar=;foo;"    "foo=;bar; cmimages=;a; bar=;foo;"
  _sortCMImagesUnitTest "cmimages=;c b; bar=;foo; foo=;bar;"   "cmimages=;b,c; bar=;foo; foo=;bar;"
  _sortCMImagesUnitTest "bar=;foo; foo=;bar; cmimages=; c b ;" "bar=;foo; foo=;bar; cmimages=;b,c;"
  _sortCMImagesUnitTest "cmimages=;;"                          "cmimages=;;"
  _sortCMImagesUnitTest "cmimages=; ;"                         "cmimages=;;"
  _sortCMImagesUnitTest "cmimages=;,,;"                        "cmimages=;;"
  return 0
}
sortCMImagesUnitTest


function getDomainValue() {
  # get domain value specified by $1 and put in env var named by $2
  #   - if get fails, and global expected is >0, then echo an Error and exit script non-zero
  #   - example: getDomainValue '.spec.introspectVersion' DOM_VERSION
  local attvalue
  local ljpath="{$1}"
  local __retvar=$2
  set +e
  attvalue=$(kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath="$ljpath" 2>&1)
  if [ $? -ne 0 ]; then
    if [ $expected -ne 0 ]; then
      echo "@@ Error: Could not obtain '$1' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed? Err='$attvalue'"
      exit 1
    else
      # We're waiting for 0 pods - domain might have been deleted, and it doesn't matter what the value is
      attvalue='':
    fi
  fi
  eval "$__retvar='$attvalue'"
  set -e
}

function getDomainCMImages() {
  # get list of domain common mount images (if any) and place result in the env var named by $1
  #   - if expected>0 and get fails, then echo an Error and exit script non-zero
  #   - result is a sorted comma separated list
  local attvalue
  local __retvar=$1
  set +e
  attvalue=$(
    kubectl \
      get domain ${DOMAIN_UID} \
      -n ${DOMAIN_NAMESPACE} \
      -o=jsonpath="{range .spec.serverPod.commonMounts[*]}{.image}{','}{end}" \
      2>&1
  )
  if [ $? -ne 0 ]; then
    if [ $expected -ne 0 ]; then
      echo "@@ Error: Could not obtain '.spec.serverPod' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed? Err='$attvalue'"
      exit 1
    else
      # We're waiting for 0 pods - it doesn't matter what the value is
      attvalue='':
    fi
  fi
  set -e
  attvalue=$(echo "$attvalue" | sortlist)
  eval "$__retvar='$attvalue'"
}

tmpfileorig=$(tempfile)
tmpfilecur=$(tempfile)

trap "rm -f $tmpfileorig $tmpfilecur" EXIT

cur_pods=0
reported=0
last_pod_count_secs=$SECONDS
goal_RV_orig="--not-known--"
goal_IV_orig="--not-known--"
goal_image_orig="--not-known--"
goal_cmimages_orig="--not-known--"

# col_headers must line up with the jpath
col_headers1="NAME RVER IVER IMAGE CMIMAGES READY PHASE"
col_headers2="---- ---- ---- ----- -------- ----- -----"

# be careful! if changing jpath, then it must
# correspond with the regex below and col_headers above

jpath=''
jpath+='{range .items[*]}'
  jpath+='{" name="}'
  jpath+='{";"}{.metadata.name}{";"}'
  jpath+='{" domainRestartVersion="}'
  jpath+='{";"}{.metadata.labels.weblogic\.domainRestartVersion}{";"}'
  jpath+='{" introspectVersion="}'
  jpath+='{";"}{.metadata.labels.weblogic\.introspectVersion}{";"}'
  jpath+='{" image="}'
  jpath+='{";"}{.spec.containers[?(@.name=="weblogic-server")].image}{";"}'
  jpath+='{" cmimages="}'
  jpath+='{";"}{.spec.initContainers[?(@.command[0]=="/weblogic-operator/scripts/commonMount.sh")].image}{";"}'
  jpath+='{" ready="}'
  jpath+='{";"}{.status.containerStatuses[?(@.name=="weblogic-server")].ready}{";"}'
  jpath+='{" phase="}'
  jpath+='{";"}{.status.phase}{";"}'
  jpath+='{"\n"}'
jpath+='{end}'

# Loop until we reach the desired pod count for pods at the desired restart version,
# introspect version, and image -- or until we reach the timeout.

while [ 1 -eq 1 ]; do

  #
  # Get the current domain resource's spec.restartVersion, spec.introspectVersion,
  # spec.image, and cm images. If any of these fail then these functions
  # fail we assume that domain resource was not found and "exit 1" if goal pods != 0,
  # or return "" if goal pods == 0.
  #

  getDomainValue ".spec.restartVersion"    goal_RV_current
  getDomainValue ".spec.introspectVersion" goal_IV_current
  getDomainValue ".spec.image"             goal_image_current
  getDomainCMImages                        goal_cmimages_current
 
  ret="${goal_RV_current}${goal_IV_current}${goal_image_current}${goal_cmimages_current}^M"
  if [ ! "${ret/Error:/}" = "${ret}" ]; then
    echo $ret
    exit 1
  fi

  #
  # Force new reporting for the rare case where domain resource RV, IV, or 
  # image changed since we last reported.
  #

  if [ ! "$goal_RV_orig" = "$goal_RV_current" ] \
     || [ ! "$goal_IV_orig" = "$goal_IV_current" ] \
     || [ ! "$goal_image_orig" = "$goal_image_current" ] \
     || [ ! "$goal_cmimages_orig" = "$goal_cmimages_current" ]
  then
    [ "$reported" = "1" ] && echo
    reported=0
    goal_IV_orig="$goal_IV_current"
    goal_RV_orig="$goal_RV_current"
    goal_image_orig="$goal_image_current"
    goal_cmimages_orig="$goal_cmimages_current"
  fi

  #
  # If 'expected' = 0, get the current number of pods regardless of their
  # restart version, introspect version, image, or ready state. 
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

    lead_string="Waiting up to $timeout_secs seconds for there to be no (0) WebLogic Server pods that match the following criteria:"
    criteria="namespace='$DOMAIN_NAMESPACE' domainUID='$DOMAIN_UID'"

  else

    regex="domainRestartVersion=;$goal_RV_current;"
    regex+=" introspectVersion=;$goal_IV_current;"
    regex+=" image=;$goal_image_current;"
    regex+=" cmimages=;$goal_cmimages_current;"
    regex+=" ready=;true;"

    set +e # disable error checks as grep returns non-zero when it finds nothing (sigh)
    cur_pods=$( kubectl -n ${DOMAIN_NAMESPACE} get pods \
        -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
        -o=jsonpath="$jpath" \
        | sortCMImages \
        | grep "$regex" | wc -l )
    set -e

    lead_string="Waiting up to $timeout_secs seconds for exactly '$expected' WebLogic Server pods to reach the following criteria:"
    criteria="ready='true'"
    criteria+=" image='$goal_image_current'"
    criteria+=" commonMountImages='$goal_cmimages_current'"
    criteria+=" domainRestartVersion='$goal_RV_current'"
    criteria+=" introspectVersion='$goal_IV_current'"
    criteria+=" namespace='$DOMAIN_NAMESPACE'"
    criteria+=" domainUID='$DOMAIN_UID'"

  fi

  #
  # Report the current state to stdout. Exit 0 if we've reached our
  # goal, exit non-zero if we've reached our time-out.
  #


  if [ "$verbose" = "false" ]; then
    if [ $reported -eq 0 ]; then
      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: $lead_string"
      for criterion in $criteria; do
        echo "@@ [$(timestamp)][seconds=$SECONDS] Info:   $criterion"
      done
      echo -n "@@ [$(timestamp)][seconds=$SECONDS] Info: Current pods that match the above criteria ="
      echo -n " $cur_pods"
      reported=1
      last_pod_count_secs=$SECONDS
  
    elif [ $((SECONDS - last_pod_count_secs)) -gt $report_interval ] \
         || [ $cur_pods -eq $expected ]; then
      echo -n " $cur_pods"
      last_pod_count_secs=$SECONDS

    fi
  else

    kubectl -n ${DOMAIN_NAMESPACE} get pods \
      -l weblogic.domainUID="${DOMAIN_UID}" \
      -o=jsonpath="$jpath" | sortCMImages > $tmpfilecur

    set +e
    diff -q $tmpfilecur $tmpfileorig 2>&1 > /dev/null
    diff_res=$?
    set -e
    if [ ! $diff_res -eq 0 ] \
       || [ $((SECONDS - last_pod_count_secs)) -gt $report_interval ] \
       || [ $cur_pods -eq $expected ]; then

      if [ $reported -eq 0 ]; then
        echo
        echo "@@ [$(timestamp)][seconds=$SECONDS] Info: $lead_string"
        for criterion in $criteria; do
          echo "@@ [$(timestamp)][seconds=$SECONDS] Info:   $criterion"
        done
        echo
        reported=1
      fi

      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: '$cur_pods' WebLogic Server pods currently match all criteria, expecting '$expected'."
      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:"
      echo

      # print results as a table
      #  - first strip out the var= and replace with "val". 
      #  - note that the quotes are necessary so that 'print_table' 
      #    doesn't get confused by col entries that are missing values
      (
        echo $col_headers1
        echo $col_headers2
        cat $tmpfilecur | sed "s|[^ ]*=;\([^;]*\);|'\1'|g"
      ) | column -t
      echo
   
      cp $tmpfilecur $tmpfileorig
      last_pod_count_secs=$SECONDS
    fi
  fi

  if [ $cur_pods -eq $expected ]; then
    if [ ! "$verbose" = "true" ]; then
      echo ". "
    else
      echo
    fi
    echo "@@ [$(timestamp)][seconds=$SECONDS] Info: Success!"
    exit 0
  fi

  if [ $SECONDS -ge $timeout_secs ]; then
    echo
    echo "@@ [$(timestamp)][seconds=$SECONDS] Error: Timeout after waiting more than $timeout_secs seconds."
    exit 1
  fi

  sleep 1
done
