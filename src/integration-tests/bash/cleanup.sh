#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# -----------------
# Summary and Usage
# -----------------
#
# This script does a best-effort delete of acceptance test k8s artifacts, the
# local test tmp directory, and the potentially remote domain pv directories.
#
# This script accepts two optional env var overrides:
#
#   RESULT_ROOT  The root directory of the test temporary files.
#
#   PV_ROOT      The root directory on the kubernetes cluster
#                used for persistent volumes.
#
#   LEASE_ID     Set this if you want cleanup to release the 
#                given lease on a failure.
#
# See 'run.sh' for a detailed description of RESULT_ROOT and PV_ROOT.
#
# --------------------
# Detailed Description
# --------------------
#
# The test runs in 4 phases:
#
#   Phase 1:  Delete domain resources with label 'weblogic.domainUID'.
#
#   Phase 2:  Delete wls operator with lable 'weblogic.operatorName' and
#             delete voyager controller.
#
#   Phase 3:  Use a kubernetes job to delete the PV directories
#             on the kubernetes cluster.
#
#   Phase 4:  Delete the local test output directory.
#
#   Phase 5:  If we own a lease, then release it on a failure
#             see LEASE_ID above.
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
# deleteResWithLabel outputfile
#
function deleteResWithLabel {
  # clean the output file first
  if [ -e $1 ]; then
    rm $1
  fi

  getResWithLabel $1
  # delete namespaced types
  cat $1 | awk '{ print $4 }' | grep -v "^$" | sort -u | while read line; do
    if [ "$test_mode" = "true" ]; then
      echo kubectl -n $line delete $NAMESPACED_TYPES -l "$LABEL_SELECTOR"
    else
      kubectl -n $line delete $NAMESPACED_TYPES -l "$LABEL_SELECTOR"
    fi
  done

  # delete non-namespaced types
  local no_namespace_count=`grep -c -v " -n " $1`
  if [ ! "$no_namespace_count" = "0" ]; then
    if [ "$test_mode" = "true" ]; then
      echo kubectl delete $NOT_NAMESPACED_TYPES -l "$LABEL_SELECTOR"
    else
      kubectl delete $NOT_NAMESPACED_TYPES -l "$LABEL_SELECTOR"
    fi
  fi
}

#
# deleteWLSOperators
#
function deleteWLSOperators {
  local tempfile="/tmp/$(basename $0).tmp.$$"  # == /tmp/[script-file-name].tmp.[pid]
  # delete wls operator resources
  LABEL_SELECTOR="weblogic.operatorName"
  #getResWithLabel $tempfile
  deleteResWithLabel $tempfile
}

#
# deleteVoyagerController
#
function deleteVoyagerController {
  curl -fsSL https://raw.githubusercontent.com/appscode/voyager/6.0.0/hack/deploy/voyager.sh \
      | bash -s -- --provider=baremetal --namespace=voyager --uninstall --purge
}

echo @@ Starting cleanup.
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$(dirname "${script}")" > /dev/null 2>&1 ; pwd -P)"

echo "@@ RESULT_ROOT=$RESULT_ROOT TMP_DIR=$TMP_DIR RESULT_DIR=$RESULT_DIR PROJECT_ROOT=$PROJECT_ROOT"

mkdir -p $TMP_DIR || fail No permision to create directory $TMP_DIR

NAMESPACED_TYPES="pod,job,deploy,rs,service,pvc,ingress,cm,serviceaccount,role,rolebinding,secret"
NOT_NAMESPACED_TYPES="pv,crd,clusterroles,clusterrolebindings"

# Delele domain resources.
${scriptDir}/../../../kubernetes/delete-weblogic-domain-resources.sh -d all

# Delete wls operator
deleteWLSOperators

# Delete voyager controller
deleteVoyagerController

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

