#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description:
#   Use this script to delete a set of given domains, or all domains.
#
#   Alternatively, run the script in a test mode to show what would
#   be deleted without actually performing the deletes.
#
# Usage:
#   See "function usage" below or call this script with no parameters.
#

# default when to stop retrying (settable via command line)
default_maxwaitsecs=90

# optional test mode that lists what would be deleted without 
# actually deleting (settable via command line)
test_mode=false


function usage {
cat << EOF
  Usage:

    $0 -d domain-uid,domain-uid,... [-s max-seconds] [-t]
    $0 -d domain-uid [-s max-seconds] [-t]
    $0 -d all [-s max-seconds] [-t]
    $0 -h

  Perform a best-effort delete of the k8s artifacts for
  the given domain(s), and retry until either max-seconds is reached
  or all artifacts were deleted (default $default_maxwaitsecs seconds).
  The domains can be specified as a comma-separated list of 
  domain-uids (no spaces), or the keyword 'all'.

  Specify '-t' to run the script in a test mode which will
  show the delete commands without actually performing them.

  This script exits with a zero status on success, and a 
  non-zero status on failure.
EOF
}


#
# getDomain
#   - get all k8s artifacts for domain $1 using label search weblogic.domainUID in $1
#   - if $1 has special value "all" then get the k8s artifacts for all domains
#
function getDomain {
  if [ "$1" = "all" ]; then
    local label_selector="weblogic.domainUID"
  else
    local label_selector="weblogic.domainUID in ($1)"
  fi

  # get all namespaced types with -l $label_selector

  local namespaced_types="pod,job,deploy,rs,service,pvc,ingress,cm,serviceaccount,role,rolebinding,secret"

  # if domain crd exists, look for domains too:
  kubectl get crd domains.weblogic.oracle > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    namespaced_types="domain,$namespaced_types"
  fi

  kubectl get $namespaced_types \
          -l "$label_selector" \
          -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}{end}'

  # get all non-namespaced types with -l $label_selector

  kubectl get pv,crd,clusterroles,clusterrolebindings \
          -l "$label_selector" \
          -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{"\n"}{end}' 
}

#
# deleteDomain
#   - delete all k8s artifacts for domain $1 and retry up to $2 seconds
#   - if $1 has special value "all" then delete the k8s artifacts for all domains
#   - $2 is optional, default is $default_maxwaitsecs
#   - if $test_mode is true, show deletes but don't actually perform them
function deleteDomain {

  if [ "$test_mode" = "true" ]; then
    echo @@ Test mode. Delete commands for kubernetes artifacts with label weblogic.domainUID \'$1\'.
  else
    echo @@ Deleting kubernetes artifacts with label weblogic.domainUID \'$1\'.
  fi

  local maxwaitsecs=${2:-$default_maxwaitsecs}
  local tempfile="/tmp/getdomain.tmp.$1.$$"
  local mstart=`date +%s`

  while : ; do
    getDomain $1 > $tempfile
    local count=`wc -l $tempfile | awk '{ print $1 }'`

    local mnow=`date +%s`

    echo @@ $count objects remaining after $((mnow - mstart)) seconds. Max wait is $maxwaitsecs seconds.
    if [ $count -eq 0 ]; then
      echo @@ Success.
      rm -f $tempfile
      exit 0
    fi

    if [ $((mnow - mstart)) -gt $maxwaitsecs ]; then
      echo @@ Error. Max wait of $maxwaitsecs seconds exceeded with $count objects remaining. giving up. Remaining objects:
      cat $tempfile
      rm -f $tempfile
      exit $count
    fi

    cat $tempfile | while read line; do 
      if [ "$test_mode" = "true" ]; then
        echo kubectl delete $line --ignore-not-found
      else
        kubectl delete $line --ignore-not-found
      fi
    done
    sleep 3
  done
}

domains=""

# parse command line options
while getopts ":d:s:th" opt; do
  case $opt in
    d) domains="${OPTARG}"
       ;;

    s) maxwaitsecs="${OPTARG}"
       ;;

    t) test_mode="true"
       ;;

    h) usage
       exit 0
       ;;

    *) usage
       exit 9999 
       ;;
  esac
done

if [ "$domains" = "" ]; then
  usage
  exit 9999
fi

if [ ! -x "$(command -v kubectl)" ]; then
  echo "@@ Error. kubectl is not installed."
  exit 9999
fi

deleteDomain "${domains}" "${maxwaitsecs}"
