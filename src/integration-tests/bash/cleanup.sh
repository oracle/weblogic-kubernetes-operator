#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# -----------------
# Summary and Usage
# -----------------
#
# This script does a best-effort delete of acceptance test k8s artifacts, the
# local test tmp directory, and the potentially remote domain pv directories.
#
# This script accepts optional env var overrides:
#
#   RESULT_ROOT  The root directory of the test temporary files.
#
#   PV_ROOT      The root directory on the kubernetes cluster
#                used for persistent volumes.
#
#   LEASE_ID     Set this if you want cleanup to release the 
#                given lease on a failure.
#
#   WERCKER      Set this to true if you want cleanup to delete tiller
#
#   DELETE_FILES Delete local test files, and launch a job to delete PV 
#                hosted test files (default true).
#
#   FAST_DELETE  Set to "--grace-period=1 --timeout=1" to speedup
#                deletes and skip phase 2.
#
# --------------------
# Detailed Description
# --------------------
#
# The test runs in phases:
#
#   Phase 0:  If helm is installed, helm delete all helm charts.
#             Possibly also delete tiller (see WERCKER env var above.)
#
#   Phase 1:  Delete test kubernetes artifacts with labels.
#
#   Phase 2:  Wait 15 seconds to see if previous phase succeeded, and
#             if not, repeatedly search for all test related kubectl
#             artifacts and try delete them directly for up to 60 more
#             seconds. 
#
#   Phase 3:  Use a kubernetes job to delete the PV directories
#             on the kubernetes cluster.
#
#   Phase 4:  Delete the local test output directory.
#
#   Phase 5:  If we own a lease, then release it on a failure.
#             (See optional LEASE_ID env var above.)
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
PROJECT_ROOT="$SCRIPTPATH/../../.."
RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
USER_PROJECTS_DIR="$RESULT_DIR/user-projects"
TMP_DIR="$RESULT_DIR/cleanup_tmp"
JOB_NAME="weblogic-command-job"

function fail {
  echo @@ cleanup.sh: Error "$@"
  exit 1
}

#!/bin/bash
#
# Usage:
# getResWithLabel outfilename
#
function getResWithLabel {

  # first, let's get all namespaced types with -l $LABEL_SELECTOR
  kubectl get $NAMESPACED_TYPES \
          -l "$LABEL_SELECTOR" \
          -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}{end}' \
          --all-namespaces=true >> $1

  # now, get all non-namespaced types with -l $LABEL_SELECTOR
  kubectl get $NOT_NAMESPACED_TYPES \
          -l "$LABEL_SELECTOR" \
          -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{"\n"}{end}' \
          --all-namespaces=true >> $1
}

#
# Usage:
# deleteResWithLabel outputfile
#
function deleteWithOneLabel {
  echo @@ Delete resources with label $LABEL_SELECTOR.
  # clean the output file first
  if [ -e $1 ]; then
    rm $1
  fi

  echo @@ Deleting resources with label $LABEL_SELECTOR.
  getResWithLabel $1
  # delete namespaced types
  cat $1 | awk '{ print $4 }' | grep -v "^$" | sort -u | while read line; do
    kubectl $FAST_DELETE -n $line delete $NAMESPACED_TYPES -l "$LABEL_SELECTOR"
  done

  # delete non-namespaced types
  local no_namespace_count=`grep -c -v " -n " $1`
  if [ ! "$no_namespace_count" = "0" ]; then
    kubectl $FAST_DELETE delete $NOT_NAMESPACED_TYPES -l "$LABEL_SELECTOR"
  fi

  echo "@@ Waiting for pods to stop running."
  local total=0
  local mstart=`date +%s`
  local mnow=mstart
  local maxwaitsecs=60
  while [ $((mnow - mstart)) -lt $maxwaitsecs ]; do
    pods=($(kubectl get pods --all-namespaces -l $LABEL_SELECTOR -o jsonpath='{range .items[*]}{.metadata.name} {end}'))
    total=${#pods[*]}
    if [ $total -eq 0 ] ; then
        break
    else
      echo "@@ There are $total running pods with label $LABEL_SELECTOR."
    fi
    sleep 3
    mnow=`date +%s`
  done

  if [ $total -gt 0 ]; then
    echo "Warning: after waiting $maxwaitsecs seconds, there are still $total running pods with label $LABEL_SELECTOR."
  fi
}

#
# Usage:
# deleteNamespaces outputfile
#
function deleteNamespaces {
  cat $1 | awk '{ print $4 }' | grep -v "^$" | sort -u | while read line; do
    if [ "$line" != "default" ]; then
      kubectl $FAST_DELETE delete namespace $line --ignore-not-found
    fi
  done

}

function deleteWithLabels {
  NAMESPACED_TYPES="pod,job,deploy,rs,service,pvc,ingress,cm,serviceaccount,role,rolebinding,secret"

  HANDLE_VOYAGER="false"
  VOYAGER_ING_NAME="ingresses.voyager.appscode.com"
  if [ `kubectl get crd $VOYAGER_ING_NAME --ignore-not-found | grep $VOYAGER_ING_NAME | wc -l` = 1 ]; then
    NAMESPACED_TYPES="$VOYAGER_ING_NAME,$NAMESPACED_TYPES"
    HANDLE_VOYAGER="true"
  fi

  DOMAIN_CRD="domains.weblogic.oracle"
  if [ `kubectl get crd $DOMAIN_CRD --ignore-not-found | grep $DOMAIN_CRD | wc -l` = 1 ]; then
    NAMESPACED_TYPES="$DOMAIN_CRD,$NAMESPACED_TYPES"
  fi

  NOT_NAMESPACED_TYPES="pv,crd,clusterroles,clusterrolebindings"

  tempfile="/tmp/$(basename $0).tmp.$$"  # == /tmp/[script-file-name].tmp.[pid]

  echo @@ Deleting domain resources.
  LABEL_SELECTOR="weblogic.domainUID"
  deleteWithOneLabel "$tempfile-0"

  echo @@ Deleting wls operator resources.
  LABEL_SELECTOR="weblogic.operatorName"
  deleteWithOneLabel "$tempfile-1"

  deleteNamespaces "$tempfile-0"
  deleteNamespaces "$tempfile-1"

  echo @@ Deleting voyager controller.
  if [ "$HANDLE_VOYAGER" = "true" ]; then
    deleteVoyagerOperator
  fi
}

# function genericDelete
#
#   This function is a 'generic kubernetes delete' that takes three arguments:
#
#     arg1:  Comma separated list of types of kubernetes namespaced types to search/delete.
#            example: "all,cm,pvc,ns,roles,rolebindings,secrets"
#
#     arg2:  Comma separated list of types of kubernetes non-namespaced types to search/delete.
#            example: "crd,pv,clusterroles,clusterrolebindings"
#
#     arg3:  '|' (pipe) separated list of keywords.
#            Artifacts with a label or name that contains one
#            or more of the keywords are delete candidates.
#            example:  "logstash|kibana|elastisearch|weblogic|elk|domain"
#
#   It runs in two stages:
#     In the first, wait to see if artifacts delete on their own.
#     In the second, try to delete any leftovers.
#
function genericDelete {

  for iteration in first second; do
    # In the first iteration, we wait to see if artifacts delete.
    # in the second iteration, we try to delete any leftovers.

    if [ "$iteration" = "first" ]; then
      local maxwaitsecs=15
    else
      local maxwaitsecs=60
    fi

    echo "@@ Waiting up to $maxwaitsecs seconds for ${1:?} and ${2:?} artifacts that contain string ${3:?} to delete."

    local artcount_no
    local artcount_yes
    local artcount_total
    local resfile_no
    local resfile_yes

    local mstart=`date +%s`

    while : ; do
      resfile_no="$TMP_DIR/kinv_filtered_nonamespace.out.tmp"
      resfile_yes="$TMP_DIR/kinv_filtered_yesnamespace.out.tmp"

      # leftover namespaced artifacts
      kubectl get $1 \
          -o=jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.kind}{"/"}{.metadata.name}{"\n"}{end}' \
          --all-namespaces=true 2>&1 \
          | egrep -e "($3)" | sort > $resfile_yes 2>&1
      artcount_yes="`cat $resfile_yes | wc -l`"

      # leftover non-namespaced artifacts
      kubectl get $2 \
          -o=jsonpath='{range .items[*]}{.kind}{"/"}{.metadata.name}{"\n"}{end}' \
          --all-namespaces=true 2>&1 \
          | egrep -e "($3)" | sort > $resfile_no 2>&1
      artcount_no="`cat $resfile_no | wc -l`"

      artcount_total=$((artcount_yes + artcount_no))

      mnow=`date +%s`

      if [ $((artcount_total)) -eq 0 ]; then
        echo "@@ No artifacts found."
        return 0
      fi

      if [ "$iteration" = "first" ] && [ "$FAST_DELETE" = "" ]; then
        # in the first iteration we just wait to see if artifacts go away on there own

        echo "@@ Waiting for $artcount_total artifacts to delete.  Wait time $((mnow - mstart)) seconds (max=$maxwaitsecs).  Waiting for:"

        cat $resfile_yes | awk '{ print "n=" $1 " " $2 }'
        cat $resfile_no | awk '{ print $1 }'

      else
        # in the second thirty seconds we try to delete remaining artifacts

        echo "@@ Trying to delete ${artcount_total} leftover artifacts, including ${artcount_yes} namespaced artifacts and ${artcount_no} non-namespaced artifacts, wait time $((mnow - mstart)) seconds (max=$maxwaitsecs)."

        if [ ${artcount_yes} -gt 0 ]; then
          cat "$resfile_yes" | while read line; do
            local args="`echo \"$line\" | awk '{ print "-n " $1 " delete " $2 " --ignore-not-found" }'`"
            echo "kubectl $args"
            kubectl $args
          done
        fi

        if [ ${artcount_no} -gt 0 ]; then
          cat "$resfile_no" | while read line; do
            echo "kubectl $FAST_DELETE delete $line --ignore-not-found"
            kubectl $FAST_DELETE delete $line --ignore-not-found
          done
        fi

      fi

      if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
        if [ "$iteration" = "first" ]; then
          echo "@@ Warning:  ${maxwaitsecs} seconds reached.   Will try deleting unexpected resources via kubectl delete."
        else
          echo "@@ Error:  ${maxwaitsecs} seconds reached and possibly ${artcount_total} artifacts remaining.  Giving up."
        fi
        break
      fi

      sleep 5
    done
  done
  return 1
}

function cleanup_tiller {
  kubectl $FAST_DELETE -n kube-system delete deployment tiller-deploy --ignore-not-found=true
  kubectl $FAST_DELETE delete clusterrolebinding tiller-cluster-rule --ignore-not-found=true
  kubectl $FAST_DELETE -n kube-system delete serviceaccount tiller --ignore-not-found=true
}

function fail {
  echo @@ cleanup.sh: Error "$@"
  exit 1
}

echo @@ Starting cleanup.
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$(dirname "${script}")" > /dev/null 2>&1 ; pwd -P)"
source $PROJECT_ROOT/kubernetes/internal/utility.sh

echo "@@ RESULT_ROOT=$RESULT_ROOT TMP_DIR=$TMP_DIR RESULT_DIR=$RESULT_DIR PROJECT_ROOT=$PROJECT_ROOT"

mkdir -p $TMP_DIR || fail No permision to create directory $TMP_DIR

# first, if helm is installed, delete all installed helm charts
if [ -x "$(command -v helm)" ]; then
  echo @@ Deleting installed helm charts
  helm list --short | while read helm_name; do
     helm delete --purge  $helm_name
  done

  # cleanup tiller artifacts
  if [ "$WERCKER" = "true" ]; then
    cleanup_tiller
  fi
fi

# second, try to delete with labels since the conversion is that all created resources need to
# have the proper label(s)
echo @@ Starting deleteWithLabels
deleteWithLabels

# third, try a generic delete in case there are some leftover resources, this runs in two phases:
#   phase 1:  wait to see if artifacts dissappear naturally due to the above 
#   phase 2:  kubectl delete left over artifacts
# arguments
#   arg1 - namespaced kubernetes artifacts
#   arg2 - non-namespaced artifacts
#   arg3 - keywords in deletable artificats

echo @@ Starting genericDelete
genericDelete "all,cm,pvc,roles,rolebindings,serviceaccount,secrets,ingress" "crd,pv,ns,clusterroles,clusterrolebindings" "logstash|kibana|elastisearch|weblogic|elk|domain|traefik|voyager|apache-webtier"
SUCCESS="$?"

if [ "${DELETE_FILES:-true}" = "true" ]; then

  # Delete pv directories using a job (/scratch maps to PV_ROOT on the k8s cluster machines).

  echo @@ Launching job to delete all pv contents.  This runs in the k8s cluster, /scratch mounts PV_ROOT.
  $SCRIPTPATH/job.sh "rm -fr /scratch/acceptance_test_pv"
  [ "$?" = "0" ] || SUCCESS="1"

  # Delete old test files owned by the current user.  

  echo @@ Deleting local $RESULT_DIR contents.
  rm -fr $RESULT_ROOT/acceptance_test_tmp
  [ "$?" = "0" ] || SUCCESS="1"

  echo @@ Deleting /tmp/test_suite.\* files.
  rm -f /tmp/test_suite.*

fi

# Bye

if [ ! "$LEASE_ID" = "" ] && [ ! "$SUCCESS" = "0" ]; then
  # release the lease if we own it
  ${SCRIPTPATH}/lease.sh -d "$LEASE_ID" > /tmp/release_lease.out 2>&1
  if [ "$?" = "0" ]; then
    echo @@ Lease released.
  else
    echo @@ Lease could not be released:
    cat /tmp/release_lease.out
  fi
  rm -f /tmp/release_lease.out
fi

echo @@ Exiting with status $SUCCESS
exit $SUCCESS

