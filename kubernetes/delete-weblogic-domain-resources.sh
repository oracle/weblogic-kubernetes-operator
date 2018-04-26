#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description:
#   Use this script to delete all kubernetes resources associated
#   with a set of given domains.  Alternatively, run the script
#   in a test mode to show what would be deleted without actually
#   performing the deletes.
#
# Usage:
#   See "function usage" below or call this script with no parameters.
#

function usage {
cat << EOF
  Usage:

    $(basename $0) -d domain-uid,domain-uid,... [-s max-seconds] [-t]
    $(basename $0) -d all [-s max-seconds] [-t]
    $(basename $0) -h

  Perform a best-effort delete of the kubernetes resources for
  the given domain(s), and retry until either max-seconds is reached
  or all resources were deleted (default $default_maxwaitsecs seconds).

  The domains can be specified as a comma-separated list of 
  domain-uids (no spaces), or the keyword 'all'.  The domains can be
  located in any kubernetes namespace.

  Specify '-t' to run the script in a test mode which will
  show kubernetes commands but not actually perform them.

  The script runs in three phases:  

    Phase 1:  Set the startupControl of each domain to NONE if
              it's not already NONE.  This should cause each
              domain's operator to initiate a controlled shutdown
              of the domain.  Immediately proceed to phase 2.

    Phase 2:  Wait up to half of max-seconds for WebLogic
              Server pods to exit normally, and then proceed
              to phase 3.

    Phase 3:  Periodically delete all remaining kubernetes resources
              for the specified domains, including any pods
              leftover from phase 2.  Exit and fail if max-seconds
              is exceeded and there are any leftover kubernetes
              resources.

  This script exits with a zero status on success, and a 
  non-zero status on failure.
EOF
}

#
# getDomainResources domain(s) outfilename
#
# Usage:
#   getDomainResources domainA,domainB,... outfilename
#   getDomainResources all outfilename
#
# Internal helper function
#
# File output is all domain related resources for the given domain uids, one per line,
# in the form:  'kind  name [-n namespace]'.  For example:
#    PersistentVolumeClaim domain1-pv-claim -n default 
#    PersistentVolume domain1-pv 
#
function getDomainResources {
  if [ "$1" = "all" ]; then
    LABEL_SELECTOR="weblogic.domainUID"
  else
    LABEL_SELECTOR="weblogic.domainUID in ($1)"
  fi

  # clean the output file
  if [ -e $2 ]; then
    rm $2
  fi

  # first, let's get all namespaced types with -l $LABEL_SELECTOR
  NAMESPACED_TYPES="pod,job,deploy,rs,service,pvc,ingress,cm,serviceaccount,role,rolebinding,secret"

  # if domain crd exists, look for domains too:
  kubectl get crd domains.weblogic.oracle > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    NAMESPACED_TYPES="domain,$NAMESPACED_TYPES"
  fi

  VOYAGER_ING_NAME="ingresses.voyager.appscode.com"
  if [ `kubectl get crd $VOYAGER_ING_NAME |grep $VOYAGER_ING_NAME | wc -l` = 1 ]; then
    NAMESPACED_TYPES="$VOYAGER_ING_NAME,$NAMESPACED_TYPES"
  fi

  kubectl get $NAMESPACED_TYPES \
          -l "$LABEL_SELECTOR" \
          -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}{end}' \
          --all-namespaces=true >> $2

  # now, get all non-namespaced types with -l $LABEL_SELECTOR

  NOT_NAMESPACED_TYPES="pv,crd,clusterroles,clusterrolebindings"

  kubectl get $NOT_NAMESPACED_TYPES \
          -l "$LABEL_SELECTOR" \
          -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{"\n"}{end}' \
          --all-namespaces=true >> $2
}

#
# deleteDomains domain(s) maxwaitsecs
#
# Usage:
#   deleteDomains domainA,domainB,... maxwaitsecs
#   deleteDomains all maxwaitsecs
#
# Internal helper function
#   This function first sets the startupControl of each Domain to NONE
#   and waits up to half of $2 for pods to 'self delete'.  It then deletes
#   all remaining k8s resources for domain $1 (including any remaining pods)
#   and retries up to $2 seconds.
#
#   If $1 has special value "all", it deletes all domains in all namespaces.
#
#   If global $test_mode is true, show candidate actions but don't actually perform them
#
function deleteDomains {

  if [ "$test_mode" = "true" ]; then
    echo @@ Test mode! Displaying commands for deleting kubernetes resources with label weblogic.domainUID \'$1\' without actually deleting them.
  else
    echo @@ Deleting kubernetes resources with label weblogic.domainUID \'$1\'.
  fi

  local maxwaitsecs=${2:-$default_maxwaitsecs}
  local tempfile="/tmp/$(basename $0).tmp.$$"  # == /tmp/[script-file-name].tmp.[pid]
  local mstart=`date +%s`
  local phase=1

  while : ; do
    # get all k8s resources with matching domain-uid labels and put them in $tempfile
    getDomainResources $1 $tempfile

    # get a count of all k8s resources with matching domain-uid labels
    local allcount=`wc -l $tempfile | awk '{ print $1 }'`

    # get a count of all WLS pods (any pod with a matching domain-uid label that doesn't have 'traefik' or 'apache'  embedded in its name)
    local podcount=`grep "^Pod" $tempfile | grep -v traefik | grep -v apache |  wc -l | awk '{ print $1 }'`

    local mnow=`date +%s`

    echo @@ $allcount resources remaining after $((mnow - mstart)) seconds, including $podcount WebLogic Server pods. Max wait is $maxwaitsecs seconds.

    # Exit if all k8s resources deleted or max wait seconds exceeded.

    if [ $allcount -eq 0 ]; then
      echo @@ Success.
      rm -f $tempfile
      exit 0
    elif [ $((mnow - mstart)) -gt $maxwaitsecs ]; then
      echo @@ Error! Max wait of $maxwaitsecs seconds exceeded with $allcount resources remaining, including $podcount WebLogic Server pods. Giving up. Remaining resources:
      cat $tempfile
      rm -f $tempfile
      exit $allcount
    fi

    # In phase 1, set the startupControl of each domain to NONE and then immediately
    # proceed to phase 2.  If there are no domains or WLS pods, we also immediately go to phase 2.

    if [ $phase -eq 1 -a $podcount -gt 0 ]; then
      echo @@ "Setting startupControl to NONE on each domain (this should cause operator(s) to initiate a controlled shutdown of the domain's pods.)"
      cat $tempfile | grep "^Domain" | while read line; do 
        local name="`echo $line | awk '{ print $2 }'`"
        local namespace="`echo $line | awk '{ print $4 }'`"
        if [ "$test_mode" = "true" ]; then
          echo "kubectl patch domain $name -n $namespace -p '{\"spec\":{\"startupControl\":\"NONE\"}}' --type merge"
        else
          kubectl patch domain $name -n $namespace -p '{"spec":{"startupControl":"NONE"}}' --type merge
        fi
      done
    fi
    phase=2

    # In phase 2, wait for the WLS pod count to go down to 0 for at most half
    # of 'maxwaitsecs'.  Otherwise proceed immediately to phase 3.

    if [ $phase -eq 2 ]; then
      if [ $podcount -eq 0 ]; then
        echo @@ All pods shutdown, about to directly delete remaining resources.
      elif [ $((mnow - mstart)) -gt $((maxwaitsecs / 2)) ]; then
        echo @@ Warning! $podcount WebLogic Server pods remaining but wait time exceeds half of max wait seconds.  About to directly delete all remaining resources, including the leftover pods.
      else
        echo @@ "Waiting for operator to shutdown pods (will wait for no more than half of max wait seconds before directly deleting them)."
        sleep 3
        continue
      fi
    fi
    phase=3

    # In phase 3, directly delete all k8s resources for the given domainUids
    # (including any leftover WLS pods from phases 1 & 2).

    # for each namespace with leftover resources, try delete them
    cat $tempfile | awk '{ print $4 }' | grep -v "^$" | sort -u | while read line; do 
      if [ "$test_mode" = "true" ]; then
        echo kubectl -n $line delete $NAMESPACED_TYPES -l "$LABEL_SELECTOR"
      else
        kubectl -n $line delete $NAMESPACED_TYPES -l "$LABEL_SELECTOR"
      fi
    done

    # if there are any non-namespaced types left, try delete them
    local no_namespace_count=`grep -c -v " -n " $tempfile`
    if [ ! "$no_namespace_count" = "0" ]; then
      if [ "$test_mode" = "true" ]; then
        echo kubectl delete $NOT_NAMESPACED_TYPES -l "$LABEL_SELECTOR" 
      else
        kubectl delete $NOT_NAMESPACED_TYPES -l "$LABEL_SELECTOR" 
      fi
    fi

    sleep 3
  done
}

# main entry point

# default when to stop retrying (override via command line)
default_maxwaitsecs=120

# optional test mode that lists what would be deleted without 
# actually deleting (override via command line)
test_mode=false

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
  echo "@@ Error! kubectl is not installed."
  exit 9999
fi

deleteDomains "${domains}" "${maxwaitsecs:-$default_maxwaitsecs}"

