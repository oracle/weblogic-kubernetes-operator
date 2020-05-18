#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a utility script that waits until a domain's pods have all exited,
# or waits until a domain's pods have all reached the ready state plus have
# have the same domain restart version and image as the pod's domain resource.
# 
# See 'usage()' below for  details.
#

set -eu
set -o pipefail

timeout_secs_def=600

function usage() {

  cat << EOF

  Usage:

    $(basename $0) [-n mynamespace] [-d mydomainuid] \\
       [-p expected_pod_count] \\
       [-t timeout_secs] \\
       [-q]

    Exits non-zero if 'timeout_secs' is reached before 'pod_count' is reached.

  Parameters:

    -d <domain_uid> : Defaults to 'sample-domain1'.

    -n <namespace>  : Defaults to 'sample-domain1-ns'.

    pod_count > 0   : Wait until exactly 'pod_count' WebLogic server pods for
                      a domain all (a) are ready, (b) have the same 
                      'domainRestartVersion' label value as the
                      current domain resource's 'spec.restartVersion, and
                      (c) have the same image as the current domain
                      resource's image.

    pod_count = 0   : Wait until there are no running WebLogic server pods
                      for a domain. The default.

    -t <timeout>    : Timeout in seconds. Defaults to '$timeout_secs_def'.

    -q              : Quiet mode. Show only a count of wl pods that
                      have reached the desired criteria.

    -?              : This help.

EOF
}

DOMAIN_UID="sample-domain1"
DOMAIN_NAMESPACE="sample-domain1-ns"

expected=0

timeout_secs=$timeout_secs_def

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


# prints a formatted table from the data in file $1, this assumes:
#   - delimiter is 'space'
#   - all rows have same number of columns
#   - first row is column headers
function print_table() {
  file=$1

  rm -f $tmpfiletab

  # first, get the column widths and number of columns
  # we don't use arrays since this needs to work in Mac's ancient bash
  local coltot=0
  cat $file | while read line; do
    local colcur=0
    for token in $line; do
      colcur=$((colcur + 1))
      curvar=colwidth$colcur
      eval "local $curvar=\$((${#token} > ${!curvar:-0} ? ${#token} : ${!curvar:-0}))"
      echo "local $curvar=${!curvar}" >> $tmpfiletab
    done
    echo "local coltot=$colcur" >> $tmpfiletab
  done

  source $tmpfiletab

  # now build the printfexp and separator
  local colcur=1
  local printfexp=""
  local separator=""
  while [ $colcur -le $coltot ]; do
    local curvar=colwidth$colcur
    local width=$(( ${!curvar} ))

    printfexp="${printfexp}%-$((width))s  "

    local pos=0
    while [ $pos -lt $width ]; do
      separator="$separator-"
      pos=$((pos + 1))
    done
    separator="$separator "

    colcur=$((colcur+1))
  done
  printfexp="${printfexp}\n"

  # now print the table
  local row=1
  cat $file | while read line; do
    printf "$printfexp" $line
    if [ $row -eq 1 ]; then
      printf "$printfexp" $separator
    fi
    row=$((row + 1))
  done
}

tmpfileorig=$(tempfile)
tmpfilecur=$(tempfile)
tmpfiletmp=$(tempfile)
tmpfiletab=$(tempfile)

trap "rm -f $tmpfileorig $tmpfilecur $tmpfiletmp $tmpfiletab" EXIT

cur_pods=0
reported=0
last_pod_count_secs=$SECONDS
origRV="--not-known--"
origImage="--not-known--"

# col_headers must line up with the jpath
col_headers="NAME VERSION IMAGE READY PHASE"

# be careful! if changing jpath, then it must
# correspond with the regex below and col_headers above

jpath=''
jpath+='{range .items[*]}'
  jpath+='{" name="}'
  jpath+='{";"}{.metadata.name}{";"}'
  jpath+='{" domainRestartVersion="}'
  jpath+='{";"}{.metadata.labels.weblogic\.domainRestartVersion}{";"}'
  jpath+='{" image="}'
  jpath+='{";"}{.status.containerStatuses[?(@.name=="weblogic-server")].image}{";"}'
  jpath+='{" ready="}'
  jpath+='{";"}{.status.containerStatuses[?(@.name=="weblogic-server")].ready}{";"}'
  jpath+='{" phase="}'
  jpath+='{";"}{.status.phase}{";"}'
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

  currentImage=$(kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.image}' 2>&1)
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

    lead_string="Waiting up to $timeout_secs there to be no (0) WebLogic server pods that match the following criteria:"
    criteria="namespace='$DOMAIN_NAMESPACE' domainUID='$DOMAIN_UID'"

  else

    regex="domainRestartVersion=;$currentRV;"
    regex+=" image=;$currentImage;"
    regex+=" ready=;true;"

    set +e # disable error checks as grep returns non-zero when it finds nothing (sigh)
    cur_pods=$( kubectl -n ${DOMAIN_NAMESPACE} get pods \
        -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
        -o=jsonpath="$jpath" \
        | grep "$regex" | wc -l )
    set -e

    lead_string="Waiting up to $timeout_secs seconds for exactly '$expected' WebLogic server pods to reach the following criteria:"
    criteria="ready='true' image='$currentImage' domainRestartVersion='$currentRV' namespace='$DOMAIN_NAMESPACE' domainUID='$DOMAIN_UID'"

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
      -o=jsonpath="$jpath" > $tmpfilecur

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

      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: '$cur_pods' WebLogic pods currently match all criteria, expecting '$expected'."
      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: Introspector and WebLogic pods with same namespace and domain-uid:"
      echo

      # print results as a table
      #  - first strip out the var= and replace with "val". 
      #  - note that the quotes are necessary so that 'print_table' 
      #    doesn't get confused by col entries that are missing values
      echo $col_headers > $tmpfiletmp
      cat $tmpfilecur | sed "s|[^ ]*=;\([^;]*\);|'\1'|g" >> $tmpfiletmp
      print_table $tmpfiletmp
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
