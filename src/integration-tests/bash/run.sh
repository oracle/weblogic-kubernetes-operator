#!/bin/bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# ---------------------------------------------------------
# Oracle WebLogic Kubernetes Operator Acceptance Test Suite
# ---------------------------------------------------------
#
# -----------------
# Summary and Usage
# -----------------
#
# This script builds the operator, runs a series of acceptance tests,
# and archives the results into tar.gz files upon completion.
#
# It currently runs in three modes, "Wercker", "Jenkins",
# and "standalone" Oracle Linux, where the mode is controlled by
# the WERCKER and JENKINS environment variables described below.
# The default is "standalone".
#
# Steps to run this script:
#
#   (1) set the optional env vars described below
#   (2) call "cleanup.sh" (more on cleanup below)
#   (3) call "run.sh" without any parameters
#
# A succesfull run will have a trace statement with the
# words 'Acceptance Test Suite Completed' and have
# an exit status of 0.
#
# A failed run will have a trace statement with the
# word 'FAIL', and a non-zero exit status.
#
# -------------------------
# Test Cleanup (cleanup.sh)
# -------------------------
#
# To cleanup before or after a run, set the RESULT_ROOT and PV_ROOT
# env vars that are described below to the same values that were
# used when calling run.sh, and then call "cleanup.sh".
#
# Cleanup deletes test temporary files, test PV directories, and
# test kubernetes artifacts (such as pods, services, secrets, etc). 
# It will not delete the aforementioned tar.gz archive files
# which run.sh creates after a run completes or fails.
#
# -----------------------
# Logging Levels & Output
# -----------------------
#
# The test has different levels of logging verboseness.  Output that begins with:
#
#   - ##TEST_INFO   -  is very concise (one line per test (either PASS or FAIL)).
#   - [timestamp]   -  is concise.
#   - +             -  is verbose.
#   - Anything else -  is semi-verbose.
#
# By default stand-alone mode copies all output (verbose included)
# to /tmp/test_suite.out, and echos non-verbose output to stdout.  Jenkins
# and Wercker modes similarly only echo non-verbose output to stdout by
# default, but they do not copy output to /tmp/test_suite.out.
#
# To echo verbose output to stdout, set VERBOSE to true (see VERBOSE
# env var setting below).
#
# ------------------------
# Test Settings (Env Vars)
# ------------------------
#
# This script accepts optional env var overrides:
#
#   RESULT_ROOT    The root directory to use for the tests temporary files.
#                  See "Directory Configuration and Structure" below for
#                  defaults and a detailed description of test directories.
#
#   PV_ROOT        The root directory on the kubernetes cluster
#                  used for persistent volumes.
#                  See "Directory Configuration and Structure" below for
#                  defaults and a detailed description of test directories.
#
#   LB_TYPE        Load balancer type. Can be 'TRAEFIK', 'VOYAGER', or 'APACHE'.
#                  Default is 'TRAEFIK'.
#
#   VERBOSE        Set to 'true' to echo verbose tracing to stdout.
#                  Default is 'false'.
#
#   DEBUG_OUT      Set to 'tee' to echo various command output to stdout that
#                  is normally only stored in files.  Set to 'cat' to echo such
#                  commands via 'cat' instead of 'tee'.   Set to 'file' to
#                  only put the output in files.  Default is 'file'.
#
#   QUICKTEST      When set to "true", limits testing to a subset of
#                  of the tests.
#
#   WERCKER        Set to true if invoking from Wercker, set
#                  to false or "" if running stand-alone or from Jenkins.
#                  Default is "".
#
#   JENKINS        Set to true if invoking from Jenkins, set
#                  to false or "" if running stand-alone or from Wercker.
#                  Default is "".
#
#   NODEPORT_HOST  DNS name of a Kubernetes worker node.  
#                  Default is the local host's hostname.
#
#   JVM_ARGS       JVM_ARGS to pass to WebLogic Servers.
#                  Default is "-Dweblogic.StdoutDebugEnabled=false".
#
#   BRANCH_NAME    Git branch name.
#                  Default is determined by calling 'git branch'.
#
#   LEASE_ID       Set to a unique value to (A) periodically renew a lease on
#                  the k8s cluster that indicates that no other
#                  run.sh should attempt to use the cluster, and (B)
#                  delete this lease when run.sh completes.
#                  If "true" the caller must previously
#                  obtain the lease external to the run.sh
#                  using "lease.sh -o $LEASE_ID", where ID corresponds
#                  to a process that's expected to continue
#                  throughout the run (typically the parent 
#                  process) or some uuid potentially generated by uuidgen
#                  command or some-such..
#
# The following additional overrides are currently only used when
# WERCKER=true:
#
#   IMAGE_TAG_OPERATOR   Docker image tag for operator.
#                        Default generated based off the BRANCH_NAME.
#
#   IMAGE_NAME_OPERATOR  Docker image name for operator.
#                        Default is wlsldi-v2.docker.oraclecorp.com/weblogic-operator
#
#   IMAGE_PULL_POLICY_OPERATOR   Default 'Never'.
#   IMAGE_PULL_SECRET_OPERATOR   Default ''.
#   WEBLOGIC_IMAGE_PULL_SECRET_NAME   Default ''.
#
# -------------------------------------
# Directory configuration and structure
# -------------------------------------
#
#  Main external env vars:
#
#      RESULT_ROOT   Root path for local test files.
#      PV_ROOT       Root NFS path behind PV/C directories.  This must have permissions
#                    suitable for WL pods to add files (default UID 1000 group ???)
#
#  Defaults for RESULT_ROOT & PV_ROOT:
#
#      Test Mode    RESULT_ROOT                         PV_ROOT   Where Initialized
#      -----------  ----------------------------------  -------   ------------------
#      stand-alone  /scratch/$USER/wl_k8s_test_results  <--same   run.sh/cleanup.sh defaults
#      Jenkins      /scratch/k8s_dir                    <--same   deploy.sh - which calls run.sh
#      Wercker      /tmp/inttest                        /scratch  wercker.yml - which calls run.sh/cleanup.sh
#
#  'Physical' subdirectories created by test:
#
#      Local tmp files:      RESULT_ROOT/acceptance_test_tmp/...
#
#      PV dirs K8S NFS:      PV_ROOT/acceptance_test_pv/domain-${domain_uid}-storage/...
#
#      Archives of above:    PV_ROOT/acceptance_test_pv_archive/...
#                            RESULT_ROOT/acceptance_test_tmp_archive/...
#
#  'Logical' to 'Physical' K8S PV/PVC mappings:
#
#                   'Logical'     'Actual'
#      job.sh job:  /scratch <--> PV_ROOT on K8S machines
#      domain pod:  /shared  <--> PV_ROOT/acceptance_test_pv/persistentVolume-${domain_uid} on K8S machines
#
# ----------------------------------------------
# Instructions for adding a new acceptance test: 
# ----------------------------------------------
#
#   *  Must define as a function with a name prefix of "test_".
#   *  Must be called from the test_suite function.
#   *  Must not call another test.
#
#   *  Mark the entry and exit of each test as follows:
#
#         - First call in test must be 
#               'declare_new_test <version> <optional description - no spaces>*'
#
#         - The test function name and its declare_new_test args must together form
#           a unique string that stays the same between runs
#           (typically 'declare_new_test 1 "$@"' is sufficient).
#
#         - Last call in test must be either 'declare_test_pass' or 'fail' (more on 'fail' below).
#
#   *  Fail tests using the 'fail' function.  Calling fail fails the current test and
#      exits the entire acceptance test.  fail can be called from nested functions.
#
#   *  Use the 'trace' function for less verbose tracing.
#
#   *  For verbose tracing, do not use trace (use echo or some-such) and
#      prepend each line with a "+".  (The '+' helps down-stream callers filter
#      out verbose tracing.)
#
#   *  Helper functions that check state should be named starting with "verify_"
#      or "confirm_" and should call 'fail' on a failure.
#
#   *  If a test needs a new operator or domain, add it via a 'op_define' 
#      or 'dom_define' call at the beginning of the test_suite fn, reference
#      it using its 'key', and retrieve its values using 'op_get' or 'dom_get'.
#

# Test utilities

function processJson {
    python -c "
import sys, json
j=json.load(sys.stdin)
$1
"
}


# Attempt to renew the k8s lease if $LEASE_ID is set.  This should be called
# every few minutes throughout the run, and is currently called at the start
# of each test.   See LEASE_ID instructions above for instructions about
# how to obtain the lease prior to calling run.sh.
function renewLease {
  if [ ! "$LEASE_ID" = "" ]; then
    # RESULT_DIR may not have been created yet, so use /tmp
    local outfile=/tmp/acc_test_renew_lease.out
    $SCRIPTPATH/lease.sh -r "$LEASE_ID" 2>&1 | opt_tee $outfile
    if [ $? -ne 0 ]; then
      trace "Lease renew error:"
      echo "" >> $outfile
      echo "ERROR: Could not renew lease on k8s cluster for LEASE_ID=\"$LEASE_ID\"." >> $outfile
      echo "Used '$SCRIPTPATH/lease.sh -r \"$LEASE_ID\"' to try renew the lease." >> $outfile
      echo "Some of the potential reasons for this failure are that another run" >> $outfile
      echo "may have obtained the lease, the lease may have been externally " >> $outfile
      echo "deleted, or the caller of run.sh may have forgotten to obtain the" >> $outfile
      echo "lease before calling run.sh (using 'lease.sh -o \"$LEASE_ID\"'). " >> $outfile
      echo "To force delete a lease no matter who owns the lease," >> $outfile
      echo "call 'lease.sh -f' or 'kubernetes delete cm acceptance-test-lease'" >> $outfile
      echo "(this should only be done when sure there's no current run.sh " >> $outfile
      echo "that owns the lease).  To view the current lease holder," >> $outfile
      echo "use 'lease.sh -s'.  To disable this lease check, do not set" >> $outfile
      echo "the LEASE_ID environment variable.  For more information, see" >> $outfile
      echo "LEASE_ID in the instructions embedded at the top of run.sh." >> $outfile
      echo "" >> $outfile
      cat $outfile
      fail "Could not renew lease on k8s cluster"
    fi
    rm -f $outfile
    trace "Renewed lease."
  fi
}


# Test tracing and failure handling
#
#   declare_new_test             - call this at beginning of each test fn
#   declare_new_test_from_trap   - call this at ctrl-c interrupt trap
#   declare_test_pass            - call at the end of test that passes
#   trace                        - trace
#   fail                         - fail a test and exit
#
#    NOTES: The declare_new_test calls must generate a unique TEST_ID
#           that stays the same between runs.  If the same
#           test is run twice, it must use different params
#           in order to generate a different description.
#
#           Calling declare_new_test before a previous declare passes or fails
#           generates a fail.
#             
#   Related:
#     declare_test_fail            - report test failed, called by 'fail'
#     state_dump                   - dump current state to files 
#                                    called by 'fail' and at end of the run
#     archive.sh                   - tar.gz all results, called by state_dump
#     ctrl_c                       - traps users ctrl-c interrupt and calls fail
#

#
# declare_reset
#   - implicitly called from a declare_test_pass or declare_test_fail
#   - must also call at start of the run
#   - resets TEST_ID and TEST_VERSION to a well known NULL value
#
function declare_reset {
  export TEST_VERSION="NULL"
  export TEST_ID="NULL"
}

#
# declare_test_trace PASS|FAIL|START [echo]
#   - internal helper
#   - generates trace with contents ##TEST_INFO:${GIT_ABBREVIATED_COMMIT_HASH}:${TEST_ID}:${TEST_VERSION}:${1}##
#   - also echos same if 'echo' passed as the second parameter
#
function declare_test_trace {
  local GIT_ABBREVIATED_COMMIT_HASH="${WERCKER_GIT_COMMIT:-`git log --pretty=format:%h -n 1`}"
  local str="##TEST_INFO:${GIT_ABBREVIATED_COMMIT_HASH}:${TEST_ID}:${TEST_VERSION}:${1}##"
  trace "$str"
  if [ "$2" = echo ]; then
    echo "$str"
  fi
}

#
# declare_new_test <version> [<testargs...>]
#   - must call at beginning of each test
#   - sets TEST_VERSION according to $1
#   - sets TEST_ID based on caller's fn name and <testargs...>
#   - pairs with a subsequent call to 'declare_test_pass' or 'fail'
#   - fails if previous test did not call declare_test_pass or fail
#
function declare_new_test {
  if [ "$#" -eq 0 ] ; then
    fail "Missing argument(s). Expected <version> [<testargs...>]"
  fi

  if [ ! "$TEST_VERSION" = "NULL" ]; then
    fail "New test declared before previous test failed.  Previous TEST_ID=$TEST_ID"
  fi

  export TEST_VERSION="${1}"
  export TEST_ID="${FUNCNAME[1]}"
  shift
  while [ ! "$1" = "" ]; do
    export TEST_ID="${TEST_ID}.${1}"
    shift
  done

  declare_test_trace START
  renewLease
}

# 
# declare_new_test_from_trap <version> <testname>
#   - call only from a trap that's about to call fail
#   - the trap obliterates exports like TEST_VERSION and TEST_ID, so we need to set them to something logical.
#
function declare_new_test_from_trap {
  export TEST_VERSION="${1?}"
  export TEST_ID="${2?}"
}

# declare_test_fail
#   - do not call this directly from a test
#   - implicitly called from fail
#   - pairs with an earlier call to declare_new_test
#
function declare_test_fail {
  # the primary reason for this method is to echo the test failure in a special
  # format:  ##TEST_INFO:commitID:testID:testVersion:FAIL##
  declare_test_trace FAIL echo
  declare_reset
}

#
# declare_test_pass
#   - must call at the end of each test
#   - pairs with an earlier call to declare_new_test
#
function declare_test_pass {
  # the primary reason for this method is to echo the test pass in a special
  # format:  ##TEST_INFO:commitID:testID:testVersion:PASS##
  if [ "$TEST_VERSION" = "NULL" -o "$TEST_VERSION" = "" ]; then
    fail "No current test."
  fi

  declare_test_trace PASS echo
  declare_reset
}

# 
# trace <message>
#
function trace {
  #Date reported in same format as oper-log for easier correlation.  01-22-2018T21:49:01
  #See also a similar echo in function fail
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] [test=$TEST_ID] [fn=${FUNCNAME[1]}]: ""$@"
}

#
# opt_tee <filename>
# Use this function in place of 'tee' for most use cases.
# See the description of 'DEBUG_OUT' above for information about this function
#
function opt_tee {
  local filename="${1?}"
  if [ "$DEBUG_OUT" = "tee" ]; then
    tee $filename
  elif [ "$DEBUG_OUT" = "cat" ]; then
    cat > $filename
    cat $filename
  else
    cat > $filename
  fi
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] [test=$TEST_ID] [fn=${FUNCNAME[1]}]: Output from last command is in ""$1"
}

# 
# state_dump <dir-suffix>
#   - called at the end of a run, and from fail
#   - places k8s logs, descriptions, etc in directory $RESULT_DIR/state-dump-$1
#   - calls archive.sh on RESULT_DIR locally, and on PV_ROOT via a job
#   - IMPORTANT: this method must never call fail (since it is called from fail)
#   - IMPORTANT: this method should not rely on exports 
#
function state_dump {
  if [ -z "$RESULT_DIR" ]; then
     # exports can apparently be lost when the trap catches a ^C
     trace Exports have been lost.  Trying to recreate.

     local RESULT_DIR="`cat /tmp/test_suite.result_root`/acceptance_test_tmp"
     local PV_ROOT="`cat /tmp/test_suite.pv_root`"
     local PROJECT_ROOT="`cat /tmp/test_suite.project_root`"
     local SCRIPTPATH="$PROJECT_ROOT/src/integration-tests/bash"
     local LEASE_ID="`cat /tmp/test_suite.lease_id`"
     local DEBUG_OUT="`cat /tmp/test_suite.debug_out`"

     if [ ! -d "$RESULT_DIR" ]; then
        trace State dump exiting early.  RESULT_DIR \"$RESULT_DIR\" does not exist or is not a directory.
        return
     fi
  fi
  local DUMP_DIR=$RESULT_DIR/state-dump-${1:?}
  trace Starting state dump.   Dumping state to directory ${DUMP_DIR}

  mkdir -p ${DUMP_DIR}

  # Test output is captured to ${TESTOUT} when run.sh is run stand-alone
  if [ -f ${TESTOUT:-NoSuchFile.out} ]; then
      trace Copying ${TESTOUT} to ${DUMP_DIR}/test_suite.out
      cp ${TESTOUT} ${DUMP_DIR}/test_suite.out
  fi
  
  # dumping kubectl state
  #   get domains is in its own command since this can fail if domain CRD undefined

  trace Dumping kubectl gets to kgetmany.out and kgetdomains.out in ${DUMP_DIR}
  kubectl get all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,secrets --show-labels=true --all-namespaces=true 2>&1 | opt_tee ${DUMP_DIR}/kgetmany.out
  kubectl get domains --show-labels=true --all-namespaces=true 2>&1 | opt_tee ${DUMP_DIR}/kgetdomains.out

  # Get all pod logs and redirect/copy to files 

  set +x
  local namespaces="`kubectl get namespaces | egrep -v -e "(STATUS|kube)" | awk '{ print $1 }'`"
  set -x

  local namespace
  trace "Copying logs and describes to pod-log.NAMESPACE.PODNAME and pod-describe.NAMESPACE.PODNAME in ${DUMP_DIR}"
  for namespace in $namespaces; do
    set +x
    local pods="`kubectl get pods -n $namespace --ignore-not-found | egrep -v -e "(STATUS)" | awk '{print $1}'`"
    set -x
    local pod
    for pod in $pods; do
      local logfile=${DUMP_DIR}/pod-log.${namespace}.${pod}
      local descfile=${DUMP_DIR}/pod-describe.${namespace}.${pod}
      kubectl log $pod -n $namespace 2>&1 | opt_tee $logfile
      kubectl describe pod $pod -n $namespace 2>&1 | opt_tee $descfile
    done
  done

  # use a job to archive PV, /scratch mounts to PV_ROOT in the K8S cluster
  trace "Archiving pv directory using a kubernetes job.  Look for it on k8s cluster in $PV_ROOT/acceptance_test_pv_archive"
  local outfile=${DUMP_DIR}/archive_pv_job.out
  $SCRIPTPATH/job.sh "/scripts/archive.sh /scratch/acceptance_test_pv /scratch/acceptance_test_pv_archive" 2>&1 | opt_tee ${outfile}
  if [ "$?" = "0" ]; then
     trace Job complete.
  else
     trace Job failed.  See ${outfile}.
  fi

  if [ ! "$LEASE_ID" = "" ]; then
    # release the lease if we own it
    ${SCRIPTPATH}/lease.sh -d "$LEASE_ID" 2>&1 | opt_tee ${RESULT_DIR}/release_lease.out
    if [ "$?" = "0" ]; then
      trace Lease released.
    else
      trace Lease could not be released:
      cat /${RESULT_DIR}/release_lease.out 
    fi
  fi

  # now archive all the local test files
  $SCRIPTPATH/archive.sh "${RESULT_DIR}" "${RESULT_DIR}_archive"

  trace Done with state dump
}


# 
# fail <message>
#    - report the message, fail the run, and exit the script
#    - calls state_dump (which calls archive)
#
function fail {
  set +x
  #See also a similar echo in function trace
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] [test=$TEST_ID] [fn=${FUNCNAME[1]}]: [FAIL] ""$@"

  #echo current test failure in a special format
  declare_test_fail

  echo "Stack trace:"
  local deptn=${#FUNCNAME[@]}
  local i
  for ((i=1; i<$deptn; i++)); do
      local func="${FUNCNAME[$i]}"
      local line="${BASH_LINENO[$((i-1))]}"
      local src="${BASH_SOURCE[$((i-1))]}"
      printf '%*s' $i '' # indent
      echo "at: $func(), $src, line $line"
  done

  state_dump fail
  
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] [test=$TEST_ID] [fn=${FUNCNAME[1]}]: [FAIL] Exiting with status 1"

  exit 1
}

trap ctrl_c INT

function ctrl_c() {
    declare_new_test_from_trap 1 run_aborted_with_ctrl_c
    # disable the trap:
    trap - INT
    set -o pipefail
    fail "Trapped CTRL-C"
}

function clean_jenkins {
    trace Cleaning.
    /usr/local/packages/aime/ias/run_as_root "${SCRIPTPATH}/clean_docker_k8s.sh -y"
}

function setup_jenkins {
    trace Setting up.
    /usr/local/packages/aime/ias/run_as_root "sh ${SCRIPTPATH}/install_docker_k8s.sh -y -u wls -v ${K8S_VERSION}"
    set +x
    . ~/.dockerk8senv
    set -x
    id

    trace "Pull and tag the images we need"

    docker login -u teamsldi_us@oracle.com -p $docker_pass  wlsldi-v2.docker.oraclecorp.com
    docker images

    docker pull wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest store/oracle/weblogic:12.2.1.3

    docker pull wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest store/oracle/serverjre:8

    docker pull wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest store/oracle/apache:12.2.1.3

    # create a docker image for the operator code being tested
    export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
    trace "Running docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" --build-arg VERSION=$JAR_VERSION --no-cache=true ."
    docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" --build-arg VERSION=$JAR_VERSION --no-cache=true .

    docker images

    trace "Helm installation starts" 
    wget -q -O  /tmp/helm-v2.8.2-linux-amd64.tar.gz https://kubernetes-helm.storage.googleapis.com/helm-v2.8.2-linux-amd64.tar.gz
    mkdir /tmp/helm
    tar xzf /tmp/helm-v2.8.2-linux-amd64.tar.gz -C /tmp/helm
    chmod +x /tmp/helm/linux-amd64/helm
    /usr/local/packages/aime/ias/run_as_root "cp /tmp/helm/linux-amd64/helm /usr/bin/"
    rm -rf /tmp/helm
    helm init
    trace "Helm is configured."

    # we always use HELM for the operator now
    if [ ! -x "$(command -v helm)" ]; then
      fail "We always use HELM charts for the operator now. But helm binary not found in path.  the helm installation in this function must have failed "
    fi
}

# setup_local is for arbitrary dev hosted linux - it assumes docker & k8s are already installed
function setup_local {
  trace "Pull and tag the images we need"

  docker pull wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest
  docker tag wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest store/oracle/weblogic:12.2.1.3

  docker pull wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest
  docker tag wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest store/oracle/serverjre:8

  docker pull wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest
  docker tag wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest store/oracle/apache:12.2.1.3

  if [ ! -x "$(command -v helm)" ]; then
    fail "We always use HELM charts for the operator now. But helm binary not found in path, helm must be pre-installed prior to running integration tests locally"
  fi
}

function setup_wercker {
  trace "Perform setup for running in wercker"

  trace "Install tiller"

  kubectl create serviceaccount --namespace kube-system tiller
  kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller

  # Note: helm init --wait would wait until tiller is ready, and requires helm 2.8.2 or above 
  helm init --service-account=tiller --wait

  helm version

  kubectl get po -n kube-system

  trace "Existing helm charts "
  helm ls
  trace "Deleting installed helm charts"
  helm list --short --all | xargs -L1 helm delete --purge
  trace "After helm delete, list of installed helm charts is: "
  helm ls --all

  if [ ! -x "$(command -v helm)" ]; then
    fail "We always use HELM charts for the operator now. But helm binary not found in path.  the helm installation in this function must have failed "
  fi

  trace "Completed setup_wercker"
}

function create_image_pull_secret_jenkins {

    trace "Creating Secret"
    kubectl create secret docker-registry wlsldi-secret  \
    --docker-server=wlsldi-v2.docker.oraclecorp.com \
    --docker-username=teamsldi_us@oracle.com \
    --docker-password=$docker_pass \
    --docker-email=teamsldi_us@oracle.com 2>&1 | sed 's/^/+' 2>&1

    trace "Checking Secret"
    local SECRET="`kubectl get secret wlsldi-secret | grep wlsldi | wc | awk ' { print $1; }'`"
    if [ "$SECRET" != "1" ]; then
        fail 'secret wlsldi-secret was not created successfully'
    fi

}

function create_image_pull_secret_wercker {

    local namespace=${1:-default}

    trace "Creating Docker Secret"
    kubectl create secret docker-registry $IMAGE_PULL_SECRET_WEBLOGIC  \
    --docker-server='index.docker.io/v1/' \
    --docker-username='$DOCKER_USERNAME' \
    --docker-password='$DOCKER_PASSWORD' \
    --docker-email='$DOCKER_EMAIL' \
    -n $namespace 2>&1 | sed 's/^/+' 2>&1

    trace "Checking Secret"
    local SECRET="`kubectl get secret $IMAGE_PULL_SECRET_WEBLOGIC | grep $IMAGE_PULL_SECRET_WEBLOGIC | wc | awk ' { print $1; }'`"
    if [ "$SECRET" != "1" ]; then
        fail 'secret $IMAGE_PULL_SECRET_WEBLOGIC was not created successfully'
    fi

    trace "Creating Registry Secret"
    kubectl create secret docker-registry $IMAGE_PULL_SECRET_OPERATOR  \
    --docker-server='$REPO_SERVER' \
    --docker-username='$REPO_USERNAME' \
    --docker-password='$REPO_PASSWORD' \
    --docker-email='$REPO_EMAIL' \
    -n $namespace 2>&1 | sed 's/^/+' 2>&1

    trace "Checking Secret"
    local SECRET="`kubectl get secret $IMAGE_PULL_SECRET_OPERATOR | grep $IMAGE_PULL_SECRET_OPERATOR | wc | awk ' { print $1; }'`"
    if [ "$SECRET" != "1" ]; then
        fail 'secret $IMAGE_PULL_SECRET_OPERATOR was not created successfully'
    fi

}

# op_define OP_KEY NAMESPACE TARGET_NAMESPACES EXTERNAL_REST_HTTPSPORT
#   sets up table of operator values.
#
# op_get    OP_KEY
#   gets an operator value
#
# op_echo   OP_KEY
#   lists the values
#
# Usage example:
#   op_define myop mynamespace ns1,ns2 34007
#   opkey="myop"
#   echo Defined operator $opkey with `op_echo $opkey`
#   local nspace="`op_get $opkey NAMESPACE`"
#

function op_define {
    if [ "$#" != 4 ] ; then
      fail "requires 4 parameters: OP_KEY NAMESPACE TARGET_NAMESPACES EXTERNAL_REST_HTTPSPORT"
    fi
    local opkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    eval export OP_${opkey}_NAMESPACE="$2"
    eval export OP_${opkey}_TARGET_NAMESPACES="$3"
    eval export OP_${opkey}_EXTERNAL_REST_HTTPSPORT="$4"

    # generated TMP_DIR for operator = $USER_PROJECTS_DIR/weblogic-operators/$NAMESPACE :
    eval export OP_${opkey}_TMP_DIR="$USER_PROJECTS_DIR/weblogic-operators/$2"

    #verbose tracing starts with a +
    op_echo_all $1 | sed 's/^/+/'

    trace Defined operator ${1}.
}

function op_get {
    local opkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    eval "echo \${OP_${opkey}_${2?}}"
}

function op_echo_all {
    local opkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    env | grep "^OP_${opkey}_"
}

function operator_ready_wait {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local namespace=$OPERATOR_NS
    trace Waiting for operator deployment to be ready...
    local AVAILABLE="0"
    local max=30
    local count=0
    while [ "$AVAILABLE" != "1" -a $count -lt $max ] ; do
        sleep 10
        local AVAILABLE=`kubectl get deploy weblogic-operator -n $namespace -o jsonpath='{.status.availableReplicas}'`
        trace "status is $AVAILABLE, iteration $count of $max"
        local count=`expr $count + 1`
    done

    if [ "$AVAILABLE" != "1" ]; then
        kubectl get deploy weblogic-operator -n ${namespace}
        kubectl describe deploy weblogic-operator -n ${namespace}
        kubectl describe pods -n ${namespace}
        fail "The WebLogic operator deployment is not available, after waiting 300 seconds"
    fi
}

function deploy_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: opkey"
    fi

    local opkey=${1}
    local NAMESPACE="`op_get $opkey NAMESPACE`"
    local TARGET_NAMESPACES="`op_get $opkey TARGET_NAMESPACES`"
    local EXTERNAL_REST_HTTPSPORT="`op_get $opkey EXTERNAL_REST_HTTPSPORT`"
    local TMP_DIR="`op_get $opkey TMP_DIR`"

    if [ "$WERCKER" = "true" ]; then 
      create_image_pull_secret_wercker $NAMESPACE
    fi

    trace 'customize the yaml'
    mkdir -p $TMP_DIR
    local inputs="$TMP_DIR/weblogic-operator-values.yaml"

    # generate certificates
    $PROJECT_ROOT/kubernetes/generate-internal-weblogic-operator-certificate.sh > $inputs
    $PROJECT_ROOT/kubernetes/generate-external-weblogic-operator-certificate.sh DNS:${NODEPORT_HOST} >> $inputs

    trace 'customize the inputs yaml file to add test namespace'
    echo "domainNamespaces:" >> $inputs
    for i in $(echo $TARGET_NAMESPACES | sed "s/,/ /g")
    do
      echo "  - $i" >> $inputs
    done
    echo "imagPullPolicy: ${IMAGE_PULL_POLICY_OPERATOR}" >> $inputs
    echo "image: ${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" >> $inputs
    echo "externalRestOption: SELF_SIGNED_CERT" >> $inputs
    echo "externalOperatorCertSans: DNS:${NODEPORT_HOST}" >> $inputs
    trace 'customize the inputs yaml file to set the java logging level to $LOGLEVEL_OPERATOR'
    echo "javaLoggingLevel: \"$LOGLEVEL_OPERATOR\"" >> $inputs
    echo "externalRestHttpsPort: ${EXTERNAL_REST_HTTPSPORT}" >>  $inputs
    echo "serviceAccount: weblogic-operator" >> $inputs
    trace "Contents after customization in file $inputs"
    cat $inputs

    local outfile="${TMP_DIR}/create-weblogic-operator-helm.out"
    trace "Run helm install to deploy the weblogic operator, see \"$outfile\" for tracking."
    cd $PROJECT_ROOT/kubernetes/charts
    helm install weblogic-operator --name ${opkey} --namespace ${NAMESPACE} -f $inputs 2>&1 | opt_tee ${outfile}
    trace "helm install output:"
    cat $outfile
    operator_ready_wait $opkey

    if [ "$?" = "0" ]; then
       # Prepend "+" to detailed debugging to make it easy to filter out
       cat ${outfile} | sed 's/^/+/g'
       trace Script complete.
    else
       cat ${outfile}
       fail Script failed.
    fi

    # Prepend "+" to detailed debugging to make it easy to filter out
    echo 'weblogic-operator.yaml contents:' 2>&1 | sed 's/^/+/' 2>&1
    cat $TMP_DIR/weblogic-operator.yaml 2>&1 | sed 's/^/+/' 2>&1
    echo 2>&1 | sed 's/^/+/' 2>&1

    trace "Checking the operator pods"
    #TODO It looks like this code isn't checking if REPLICA_SET, POD_TEMPLATE, and POD have expected values, is that intentional?
    local namespace=$NAMESPACE
    local REPLICA_SET=`kubectl describe deploy weblogic-operator -n ${namespace} | grep NewReplicaSet: | awk ' { print $2; }'`
    local POD_TEMPLATE=`kubectl describe rs ${REPLICA_SET} -n ${namespace} | grep ^Name: | awk ' { print $2; } '`
    local POD=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | awk ' { print $1; } '`

    trace "Checking image for pod $POD"
    local IMAGE=`kubectl describe pod $POD -n ${namespace} | grep "Image:" | awk ' { print $2; } '`
    if [ "$IMAGE" != "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" ]; then
        fail "pod image should be ((${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR})) but image is ((${IMAGE}))"
    fi

}

function test_first_operator {
    declare_new_test 1 "$@"
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: opkey"
    fi
    local OP_KEY=${1}
    deploy_operator $OP_KEY
    verify_no_domain_via_oper_rest $OP_KEY
    declare_test_pass
}


# Create another operator in a new namespace named after the operator, managing a new namespace
# the specified existing domain should not be affacted
function test_second_operator {
    declare_new_test 1 "$@"
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: new_opkey dom_key_for_existing_domain"
    fi
    local OP_KEY="${1}"
    local DOM_KEY="${2}"
    deploy_operator $OP_KEY
    verify_domain $DOM_KEY
    declare_test_pass
}

# dom_define   DOM_KEY OP_KEY NAMESPACE DOMAIN_UID STARTUP_CONTROL WL_CLUSTER_NAME WL_CLUSTER_TYPE MS_BASE_NAME ADMIN_PORT ADMIN_WLST_PORT ADMIN_NODE_PORT MS_PORT LOAD_BALANCER_WEB_PORT LOAD_BALANCER_DASHBOARD_PORT
#   Sets up a table of domain values:  all of the above, plus TMP_DIR which is derived.
#
# dom_get      DOM_KEY <value>
#   Gets a domain value.
#
# dom_echo_all DOM_KEY
#   Lists domain values for the given key.
#
# Usage example:
#   dom_define mydom default domain1 cluster-1 managed-server 7001 30012 30701 8001 30305 30315
#   local DOM_KEY=mydom
#   local nspace="`dom_get $DOM_KEY NAMESPACE`"
#   echo Defined operator $opkey with `dom_echo_all $DOM_KEY`
#
function dom_define {
    if [ "$#" != 14 ] ; then
      fail "requires 14 parameters: DOM_KEY OP_KEY NAMESPACE DOMAIN_UID STARTUP_CONTROL WL_CLUSTER_NAME WL_CLUSTER_TYPE MS_BASE_NAME ADMIN_PORT ADMIN_WLST_PORT ADMIN_NODE_PORT MS_PORT LOAD_BALANCER_WEB_PORT LOAD_BALANCER_DASHBOARD_PORT"
    fi
    local DOM_KEY="`echo \"${1}\" | sed 's/-/_/g'`"
    eval export DOM_${DOM_KEY}_OP_KEY="$2"
    eval export DOM_${DOM_KEY}_NAMESPACE="$3"
    eval export DOM_${DOM_KEY}_DOMAIN_UID="$4"
    eval export DOM_${DOM_KEY}_STARTUP_CONTROL="$5"
    eval export DOM_${DOM_KEY}_WL_CLUSTER_NAME="$6"
    eval export DOM_${DOM_KEY}_WL_CLUSTER_TYPE="$7"
    eval export DOM_${DOM_KEY}_MS_BASE_NAME="$8"
    eval export DOM_${DOM_KEY}_ADMIN_PORT="$9"
    eval export DOM_${DOM_KEY}_ADMIN_WLST_PORT="${10}"
    eval export DOM_${DOM_KEY}_ADMIN_NODE_PORT="${11}"
    eval export DOM_${DOM_KEY}_MS_PORT="${12}"
    eval export DOM_${DOM_KEY}_LOAD_BALANCER_WEB_PORT="${13}"
    eval export DOM_${DOM_KEY}_LOAD_BALANCER_DASHBOARD_PORT="${14}"

    # derive TMP_DIR $USER_PROJECTS_DIR/weblogic-domains/$NAMESPACE-$DOMAIN_UID :
    eval export DOM_${DOM_KEY}_TMP_DIR="$USER_PROJECTS_DIR/weblogic-domains/$4"

    # we only test loadBalancerExposeAdminPort=true for Apache on domain1
    if [ "$LB_TYPE" == "APACHE" ] && [ "$4" == "domain1" ] ; then
      export DOM_${DOM_KEY}_LOAD_BALANCER_EXPOSE_ADMIN_PORT="true"
    else 
      export DOM_${DOM_KEY}_LOAD_BALANCER_EXPOSE_ADMIN_PORT="false"
    fi

    #verbose tracing starts with a +
    dom_echo_all $1 | sed 's/^/+/'

    trace Defined domain ${1}.
}

function dom_get {
    local domkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    eval "echo \${DOM_${domkey}_${2?}}"
}

function dom_echo_all {
    local domkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    env | grep "^DOM_${domkey}_"
}

#
# Usage:
# createVoyagerIngress voyagerIngressYaml namespace domainUID
#
function createVoyagerIngress {
  if [ "$#" != 3 ] ; then
    fail "requires 1 parameter: voyagerIngressYaml namespace domainUID"
  fi

  # deploy Voyager Ingress resource
  kubectl apply -f $1

  local namespace=$2
  local domainUID=$3


  echo "Checking Voyager Ingress resource..."
  local maxwaitsecs=100
  local mstart=$(date +%s)
  while : ; do
    local mnow=$(date +%s)
    local vdep=$(kubectl get ingresses.voyager.appscode.com -n ${namespace} | grep ${domainUID}-voyager | wc | awk ' { print $1; } ')
    if [ "$vdep" = "1" ]; then
      echo "The Voyager Ingress resource ${domainUID}-voyager is created successfully."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The Voyager Ingress resource ${domainUID}-voyager was not created."
    fi
    sleep 2
  done

  echo "Wait until HAProxy pod is running..."
  local maxwaitsecs=100
  local mstart=$(date +%s)
  while : ; do
    local mnow=$(date +%s)
    local st=$(kubectl get pod -n ${namespace} | grep ^voyager-${domainUID}-voyager- | awk ' { print $3; } ')
    if [ "$st" = "Running" ]; then
      echo "The HAProxy pod for Voyager Ingress ${domainUID}-voyager is running."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The HAProxy pod for Voyager Ingress ${domainUID}-voyager was not created or running."
    fi
    sleep 5
  done

  echo "Checking Voyager service..."
  local maxwaitsecs=10
  local mstart=`date +%s`
  while : ; do
    local mnow=`date +%s`
    local vscv=`kubectl get service ${domainUID}-voyager-stats -n ${namespace} | grep ${domainUID}-voyager-stats | wc | awk ' { print $1; } '`
    if [ "$vscv" = "1" ]; then
      echo "The service ${domainUID}-voyager-stats is created successfully."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      fail "The service ${domainUID}-voyager-stats was not created."
    fi
    sleep 2
  done
  echo
}

function create_pv_pvc_non_helm {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local STARTUP_CONTROL="`dom_get $1 STARTUP_CONTROL`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local WL_CLUSTER_TYPE="`dom_get $1 WL_CLUSTER_TYPE`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
    local ADMIN_NODE_PORT="`dom_get $1 ADMIN_NODE_PORT`"
    local MS_PORT="`dom_get $1 MS_PORT`"

    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local tmp_dir="$TMP_DIR"

    local inputsPvPvc="$tmp_dir/create-weblogic-sample-domain-pv-pvc-inputs.yaml"

    local DOMAIN_STORAGE_DIR="domain-${DOMAIN_UID}-storage"

    cp $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-weblogic-sample-domain-pv-pvc-inputs.yaml $inputsPvPvc
    sed -i -e "s/^#domainUID:.*/domainUID: $DOMAIN_UID/" $inputsPvPvc
    sed -i -e "s;^#weblogicDomainStoragePath:.*;weblogicDomainStoragePath: $PV_ROOT/acceptance_test_pv/$DOMAIN_STORAGE_DIR;" $inputsPvPvc
    sed -i -e "s/^namespace:.*/namespace: $NAMESPACE/" $inputsPvPvc
    sed -i -e "s/^weblogicDomainStorageReclaimPolicy:.*/weblogicDomainStorageReclaimPolicy: Recycle/" $inputsPvPvc

    if [ "$DOMAIN_UID" == "domain5" ] && [ "$JENKINS" = "true" ] ; then
      sed -i -e "s/^weblogicDomainStorageType:.*/weblogicDomainStorageType: NFS/" $inputsPvPvc
      sed -i -e "s/^#weblogicDomainStorageNFSServer:.*/weblogicDomainStorageNFSServer: $NODEPORT_HOST/" $inputsPvPvc
    fi

    trace "Run the script to create the domain into output dir $domainOutPutDir, see \"$outfile\" for tracing."

    local outfilePvPvc="${tmp_dir}/create-weblogic-sample-domain-pv-pvc.sh.out"
    sh $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-weblogic-sample-domain-pv-pvc.sh -i $inputsPvPvc -o $USER_PROJECTS_DIR 2>&1 | opt_tee ${outfilePvPvc}
    pvOutput="${domainOutPutDir}/weblogic-sample-domain-pv.yaml"
    echo Creating the pvresource using ${pvOutput}
    kubectl apply -f ${pvOutput}
    pvcOutput="${domainOutPutDir}/weblogic-sample-domain-pvc.yaml"
    echo Creating the pvcresource using ${pvcOutput}
    kubectl apply -f ${pvcOutput}
}

function create_domain_home_on_pv_non_helm {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local STARTUP_CONTROL="`dom_get $1 STARTUP_CONTROL`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local WL_CLUSTER_TYPE="`dom_get $1 WL_CLUSTER_TYPE`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
    local ADMIN_NODE_PORT="`dom_get $1 ADMIN_NODE_PORT`"
    local MS_PORT="`dom_get $1 MS_PORT`"

    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local tmp_dir="$TMP_DIR"

    local WEBLOGIC_CREDENTIALS_SECRET_NAME="$DOMAIN_UID-weblogic-credentials"

    local inputsDomain="$tmp_dir/create-weblogic-sample-domain-load-balancer-inputs.yaml"

    trace "Create the domain home, and start domain resources"

    cp $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-weblogic-sample-domain-inputs.yaml $inputsDomain

    # customize inputs properties
    sed -i -e "s/^exposeAdminT3Channel:.*/exposeAdminT3Channel: true/" $inputsDomain
    sed -i -e "s/^#domainUID:.*/domainUID: $DOMAIN_UID/" $inputsDomain
    sed -i -e "s/^clusterName:.*/clusterName: $WL_CLUSTER_NAME/" $inputsDomain
    sed -i -e "s/^clusterType:.*/clusterType: $WL_CLUSTER_TYPE/" $inputsDomain
    sed -i -e "s/^namespace:.*/namespace: $NAMESPACE/" $inputsDomain
    sed -i -e "s/^t3ChannelPort:.*/t3ChannelPort: $ADMIN_WLST_PORT/" $inputsDomain
    sed -i -e "s/^adminNodePort:.*/adminNodePort: $ADMIN_NODE_PORT/" $inputsDomain
    sed -i -e "s/^exposeAdminNodePort:.*/exposeAdminNodePort: true/" $inputsDomain
    sed -i -e "s/^t3PublicAddress:.*/t3PublicAddress: $NODEPORT_HOST/" $inputsDomain
    sed -i -e "s/^adminPort:.*/adminPort: $ADMIN_PORT/" $inputsDomain
    sed -i -e "s/^managedServerPort:.*/managedServerPort: $MS_PORT/" $inputsDomain
    sed -i -e "s/^managedServerNameBase:.*/managedServerNameBase: $MS_BASE_NAME/" $inputsDomain
    sed -i -e "s/^weblogicCredentialsSecretName:.*/weblogicCredentialsSecretName: $WEBLOGIC_CREDENTIALS_SECRET_NAME/" $inputsDomain
    if [ -n "${WEBLOGIC_IMAGE_PULL_SECRET_NAME}" ]; then
      sed -i -e "s|#weblogicImagePullSecretName:.*|weblogicImagePullSecretName: ${WEBLOGIC_IMAGE_PULL_SECRET_NAME}|g" $inputsDomain
    fi
    sed -i -e "s/^javaOptions:.*/javaOptions: $WLS_JAVA_OPTIONS/" $inputsDomain
    sed -i -e "s/^startupControl:.*/startupControl: $STARTUP_CONTROL/"  $inputsDomain
    sed -i -e "s/^persistentVolumeClaimName:.*/persistentVolumeClaimName: ${DOMAIN_UID}-weblogic-domain-pvc/" $inputsDomain
    # we will test cluster scale up and down in domain1 and domain4 
    if [ "$DOMAIN_UID" == "domain1" ] || [ "$DOMAIN_UID" == "domain4" ] ; then
      sed -i -e "s/^configuredManagedServerCount:.*/configuredManagedServerCount: 3/" $inputsDomain
    fi

    local outfileDomain="${tmp_dir}/create-weblogic-sample-domain.sh.out"
    sh $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-weblogic-sample-domain.sh -i $inputsDomain -o $USER_PROJECTS_DIR 2>&1 | opt_tee ${outfileDomain}
    dcrOutput="${domainOutPutDir}/domain-custom-resource.yaml"
    echo Creating the domain custom resource using ${dcrOutput}
    kubectl apply -f ${dcrOutput}
}

function create_load_balancer_non_helm {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
    local ADMIN_NODE_PORT="`dom_get $1 ADMIN_NODE_PORT`"
    local MS_PORT="`dom_get $1 MS_PORT`"
    local LOAD_BALANCER_WEB_PORT="`dom_get $1 LOAD_BALANCER_WEB_PORT`"
    local LOAD_BALANCER_DASHBOARD_PORT="`dom_get $1 LOAD_BALANCER_DASHBOARD_PORT`"
    local LOAD_BALANCER_EXPOSE_ADMIN_PORT="`dom_get $1 LOAD_BALANCER_EXPOSE_ADMIN_PORT`"
    # local LOAD_BALANCER_VOLUME_PATH="/scratch/DockerVolume/ApacheVolume"

    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local tmp_dir="$TMP_DIR"

    local inputsLoadBalancer="$tmp_dir/create-weblogic-sample-domain-load-balancer-inputs.yaml"

    cp $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain-load-balancer/create-weblogic-sample-domain-load-balancer-inputs.yaml $inputsLoadBalancer
    # accept the default domain name (i.e. don't customize it)
    local domain_name=`egrep 'domainName' $inputsLoadBalancer | awk '{print $2}'`

    sed -i -e "s/^#domainUID:.*/domainUID: $DOMAIN_UID/" $inputsLoadBalancer
    sed -i -e "s/^clusterName:.*/clusterName: $WL_CLUSTER_NAME/" $inputsLoadBalancer
    sed -i -e "s/^namespace:.*/namespace: $NAMESPACE/" $inputsLoadBalancer
    sed -i -e "s/^adminPort:.*/adminPort: $ADMIN_PORT/" $inputsLoadBalancer
    sed -i -e "s/^managedServerPort:.*/managedServerPort: $MS_PORT/" $inputsLoadBalancer
    sed -i -e "s/^loadBalancer:.*/loadBalancer: $LB_TYPE/" $inputsLoadBalancer
    sed -i -e "s/^loadBalancerWebPort:.*/loadBalancerWebPort: $LOAD_BALANCER_WEB_PORT/" $inputsLoadBalancer
    sed -i -e "s/^loadBalancerDashboardPort:.*/loadBalancerDashboardPort: $LOAD_BALANCER_DASHBOARD_PORT/" $inputsLoadBalancer
    if [ "$LB_TYPE" == "APACHE" ]; then
      local load_balancer_app_prepath="/weblogic"
      sed -i -e "s|loadBalancerVolumePath:.*|loadBalancerVolumePath: ${LOAD_BALANCER_VOLUME_PATH}|g" $inputsLoadBalancer
      sed -i -e "s|loadBalancerAppPrepath:.*|loadBalancerAppPrepath: ${load_balancer_app_prepath}|g" $inputsLoadBalancer
      sed -i -e "s|loadBalancerExposeAdminPort:.*|loadBalancerExposeAdminPort: ${LOAD_BALANCER_EXPOSE_ADMIN_PORT}|g" $inputsLoadBalancer
    fi 

    trace "Create and start domain load balancer"
    # Setup load balancer
    local outfileLoadBalancer="${tmp_dir}/create-weblogic-sample-domain-load-balancer.sh.out"
    sh $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain-load-balancer/create-weblogic-sample-domain-load-balancer.sh -i $inputsLoadBalancer -o $USER_PROJECTS_DIR 2>&1 | opt_tee ${outfileLoadBalancer}
    if [ "${LB_TYPE}" == "TRAEFIK" ]; then
      traefikSecurityOutput="${domainOutPutDir}/weblogic-sample-domain-traefik-security.yaml"
      traefikOutput="${domainOutPutDir}/weblogic-sample-domain-traefik.yaml"
      echo Creating the $LB_TYPE load balancer security resource using ${traefikSecurityOutput}
      kubectl apply -f ${traefikSecurityOutput}
      echo Creating the $LB_TYPE load balancer resource using ${traefikOutput}
      kubectl apply -f ${traefikOutput}

    elif [ "${LB_TYPE}" == "APACHE" ]; then
      apacheOutput="${domainOutPutDir}/weblogic-sample-domain-apache.yaml"
      apacheSecurityOutput="${domainOutPutDir}/weblogic-sample-domain-apache-security.yaml"
      echo Creating the $LB_TYPE load balancer security resource using ${apacheSecurityOutput}
      kubectl apply -f ${apacheSecurityOutput}
      echo Creating the $LB_TYPE load balancer resource using ${apacheOutput}
      kubectl apply -f ${apacheOutput}

    elif [ "${LB_TYPE}" == "VOYAGER" ]; then
      # start Voyager operator and ingress controller
      echo Creating Voyager operator and ingress controller
      sh $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain-load-balancer/start-voyager-controller.sh -p ${domainOutPutDir} 2>&1 | opt_tee ${outfileLoadBalancer}
      # start voyager ingress
      voyagerIngressOutput="${domainOutPutDir}/weblogic-sample-domain-voyager-ingress.yaml"
      echo Creating the $LB_TYPE load balancer resource using ${voyagerIngressOutput}
      createVoyagerIngress $voyagerIngressOutput $NAMESPACE ${DOMAIN_UID}
    fi
}

function create_domain_pv_pvc_load_balancer {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local STARTUP_CONTROL="`dom_get $1 STARTUP_CONTROL`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local WL_CLUSTER_TYPE="`dom_get $1 WL_CLUSTER_TYPE`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
    local ADMIN_NODE_PORT="`dom_get $1 ADMIN_NODE_PORT`"
    local MS_PORT="`dom_get $1 MS_PORT`"
    local LOAD_BALANCER_WEB_PORT="`dom_get $1 LOAD_BALANCER_WEB_PORT`"
    local LOAD_BALANCER_DASHBOARD_PORT="`dom_get $1 LOAD_BALANCER_DASHBOARD_PORT`"
    local LOAD_BALANCER_EXPOSE_ADMIN_PORT="`dom_get $1 LOAD_BALANCER_EXPOSE_ADMIN_PORT`"
    # local LOAD_BALANCER_VOLUME_PATH="/scratch/DockerVolume/ApacheVolume"

    local TMP_DIR="`dom_get $1 TMP_DIR`"

    if [ "$WERCKER" = "true" ]; then 
      create_image_pull_secret_wercker $NAMESPACE
    fi

    local WLS_JAVA_OPTIONS="$JVM_ARGS"

    trace "WLS_JAVA_OPTIONS = \"$WLS_JAVA_OPTIONS\""

    local DOMAIN_STORAGE_DIR="domain-${DOMAIN_UID}-storage"

    trace "Create $DOMAIN_UID in $NAMESPACE namespace with load balancer $LB_TYPE"
  
    local tmp_dir="$TMP_DIR"
    mkdir -p $tmp_dir

    local WEBLOGIC_CREDENTIALS_SECRET_NAME="$DOMAIN_UID-weblogic-credentials"
    local WEBLOGIC_CREDENTIALS_FILE="${tmp_dir}/weblogic-credentials.yaml"

    # Common inputs file for creating a domain
    local inputs="$tmp_dir/create-weblogic-domain-inputs.yaml"
    if [ "$USE_HELM" = "true" ]; then
      cp $PROJECT_ROOT/kubernetes/charts/weblogic-domain/values.yaml $inputs
    else
      cp $PROJECT_ROOT/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-weblogic-sample-domain-inputs.yaml $inputs
    fi

    # accept the default domain name (i.e. don't customize it)
    local domain_name=`egrep 'domainName' $inputs | awk '{print $2}'`

    trace 'Create the secret with weblogic admin credentials'
    cp $CUSTOM_YAML/weblogic-credentials-template.yaml  $WEBLOGIC_CREDENTIALS_FILE

    sed -i -e "s|%NAMESPACE%|$NAMESPACE|g" $WEBLOGIC_CREDENTIALS_FILE
    sed -i -e "s|%DOMAIN_UID%|$DOMAIN_UID|g" $WEBLOGIC_CREDENTIALS_FILE
    sed -i -e "s|%DOMAIN_NAME%|$domain_name|g" $WEBLOGIC_CREDENTIALS_FILE

    kubectl apply -f $WEBLOGIC_CREDENTIALS_FILE

    trace 'Check secret'
    local ADMINSECRET=`kubectl get secret $WEBLOGIC_CREDENTIALS_SECRET_NAME -n $NAMESPACE | grep $WEBLOGIC_CREDENTIALS_SECRET_NAME | wc -l `
    if [ "$ADMINSECRET" != "1" ]; then
        fail 'could not create the secret with weblogic admin credentials'
    fi

    trace 'Prepare the job customization script'
    local internal_dir="$tmp_dir/internal"
    mkdir $tmp_dir/internal

    # copy testwebapp.war for testing
    cp $PROJECT_ROOT/src/integration-tests/apps/testwebapp.war ${tmp_dir}/testwebapp.war


    local outfile="${tmp_dir}/mkdir_physical_nfs.out"
    trace "Use a job to create the k8s host directory \"$PV_ROOT/acceptance_test_pv/$DOMAIN_STORAGE_DIR\" that we will use for the domain's persistent volume, see \"$outfile\" for job tracing."

    # Note that the job.sh job mounts PV_ROOT to /scratch and runs as UID 1000,
    # so PV_ROOT must already exist and have 777 or UID=1000 permissions.
    $SCRIPTPATH/job.sh "mkdir -p /scratch/acceptance_test_pv/$DOMAIN_STORAGE_DIR" 2>&1 | opt_tee ${outfile}
    if [ "$?" = "0" ]; then
       cat ${outfile} | sed 's/^/+/g'
       trace Job complete.  Directory created on k8s cluster.
    else
       cat ${outfile}
       fail Job failed.  Could not create k8s cluster NFS directory.   
    fi

    if [ "$USE_HELM" = "true" ]; then
      # Customize the create domain inputs
      sed -i -e "s/^exposeAdminT3Channel:.*/exposeAdminT3Channel: true/" $inputs
      if [ "$DOMAIN_UID" == "domain5" ] && [ "$JENKINS" = "true" ] ; then
        sed -i -e "s/^weblogicDomainStorageType:.*/weblogicDomainStorageType: NFS/" $inputs
        sed -i -e "s/^#weblogicDomainStorageNFSServer:.*/weblogicDomainStorageNFSServer: $NODEPORT_HOST/" $inputs
      fi
      sed -i -e "s;^#weblogicDomainStoragePath:.*;weblogicDomainStoragePath: $PV_ROOT/acceptance_test_pv/$DOMAIN_STORAGE_DIR;" $inputs

      # Customize more configuration 
      sed -i -e "s/^clusterName:.*/clusterName: $WL_CLUSTER_NAME/" $inputs
      sed -i -e "s/^clusterType:.*/clusterType: $WL_CLUSTER_TYPE/" $inputs
      sed -i -e "s/^namespace:.*/namespace: $NAMESPACE/" $inputs

      sed -i -e "s/^t3ChannelPort:.*/t3ChannelPort: $ADMIN_WLST_PORT/" $inputs
      sed -i -e "s/^adminNodePort:.*/adminNodePort: $ADMIN_NODE_PORT/" $inputs
      sed -i -e "s/^exposeAdminNodePort:.*/exposeAdminNodePort: true/" $inputs
      sed -i -e "s/^t3PublicAddress:.*/t3PublicAddress: $NODEPORT_HOST/" $inputs
      sed -i -e "s/^adminPort:.*/adminPort: $ADMIN_PORT/" $inputs
      sed -i -e "s/^managedServerPort:.*/managedServerPort: $MS_PORT/" $inputs
      sed -i -e "s/^managedServerNameBase:.*/managedServerNameBase: $MS_BASE_NAME/" $inputs
      sed -i -e "s/^weblogicCredentialsSecretName:.*/weblogicCredentialsSecretName: $WEBLOGIC_CREDENTIALS_SECRET_NAME/" $inputs
      if [ -n "${WEBLOGIC_IMAGE_PULL_SECRET_NAME}" ]; then
        sed -i -e "s|#weblogicImagePullSecretName:.*|weblogicImagePullSecretName: ${WEBLOGIC_IMAGE_PULL_SECRET_NAME}|g" $inputs
      fi
      sed -i -e "s/^loadBalancer:.*/loadBalancer: $LB_TYPE/" $inputs
      sed -i -e "s/^loadBalancerWebPort:.*/loadBalancerWebPort: $LOAD_BALANCER_WEB_PORT/" $inputs
      sed -i -e "s/^loadBalancerDashboardPort:.*/loadBalancerDashboardPort: $LOAD_BALANCER_DASHBOARD_PORT/" $inputs
      if [ "$LB_TYPE" == "APACHE" ] ; then
        local load_balancer_app_prepath="/weblogic"
        sed -i -e "s|loadBalancerVolumePath:.*|loadBalancerVolumePath: ${LOAD_BALANCER_VOLUME_PATH}|g" $inputs
        sed -i -e "s|loadBalancerAppPrepath:.*|loadBalancerAppPrepath: ${load_balancer_app_prepath}|g" $inputs
        sed -i -e "s|loadBalancerExposeAdminPort:.*|loadBalancerExposeAdminPort: ${LOAD_BALANCER_EXPOSE_ADMIN_PORT}|g" $inputs
      fi
      sed -i -e "s/^javaOptions:.*/javaOptions: $WLS_JAVA_OPTIONS/" $inputs
      sed -i -e "s/^startupControl:.*/startupControl: $STARTUP_CONTROL/"  $inputs

      # we will test cluster scale up and down in domain1 and domain4 
      if [ "$DOMAIN_UID" == "domain1" ] || [ "$DOMAIN_UID" == "domain4" ] ; then
        sed -i -e "s/^configuredManagedServerCount:.*/configuredManagedServerCount: 3/" $inputs
      fi

      # we will test pv reclaim policy in domain6. We choose to do this way to void adding too many parameters in dom_define
      if [ "$DOMAIN_UID" == "domain6" ] ; then
        sed -i -e "s/^weblogicDomainStorageReclaimPolicy:.*/weblogicDomainStorageReclaimPolicy: Recycle/" $inputs
      fi

      local outfile="${tmp_dir}/create-weblogic-sample-domain.sh.out"
      trace "Run helm install to create the domain, see \"$outfile\" for tracing."
      cd $PROJECT_ROOT/kubernetes/charts
      helm install weblogic-domain --name $DOMAIN_UID -f $inputs --namespace ${NAMESPACE} 2>&1 | opt_tee ${outfile}
      trace "helm install output:"
      cat $outfile
    else
      # create sample domain, including a pv and pvc, domain home on pv, and load balancer
 
      domainOutPutDir=${USER_PROJECTS_DIR}/weblogic-domains/${DOMAIN_UID}
      trace "Run the sample scripts to create the domain into output dir $domainOutPutDir, see \"$outfile\" for tracing."

      # Create sample  domain pv and pvc
      trace "Create and start domain pv and pvc"
      create_pv_pvc_non_helm $@

      # Create  sample domain home on pv 
      trace "Create the domain home, and start domain resources"
      create_domain_home_on_pv_non_helm $@

      # Setup sample load balancer
      trace "Create and start domain load balancer"
      create_load_balancer_non_helm $@

    fi

    if [ "$?" = "0" ]; then
       cat ${outfile} | sed 's/^/+/g'
       trace Script complete.
    else
       cat ${outfile}
       fail Script failed.
    fi

    trace 'create_domain_pv_pvc_load_balancer done'
}

# note that this function has slightly different parameters than deploy_webapp_via_WLST
function deploy_webapp_via_REST {

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local WLS_ADMIN_USERNAME="`get_wladmin_user $1`"
    local WLS_ADMIN_PASSWORD="`get_wladmin_pass $1`"

    local AS_NAME="$DOMAIN_UID-admin-server"

    # Make sure the admin server has established JMX connection with all managed servers
    verify_ms_connectivity $1

    trace "deploy the web app to domain $DOMAIN_UID in $NAMESPACE namespace"

    # call the wls rest api to deploy the app

    local CURL_RESPONSE_BODY="$TMP_DIR/deploywebapp.rest.response.body"

    local get_admin_host="kubectl get services -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$AS_NAME\")].spec.clusterIP}'"

    trace admin host query is $get_admin_host

    local ADMIN_HOST=`eval $get_admin_host`

    trace admin host is $get_admin_host

    local REST_ADDR="http://${ADMIN_HOST}:${ADMIN_PORT}"

    echo "NO_DATA" > $CURL_RESPONSE_BODY

    local HTTP_RESPONSE=$(curl --silent --show-error --noproxy "*" \
      --user ${WLS_ADMIN_USERNAME}:${WLS_ADMIN_PASSWORD} \
      -H X-Requested-By:Integration-Test \
      -H Accept:application/json \
      -H Content-Type:multipart/form-data \
      -F "model={
        name: 'testwebapp',
        targets: [ '$WL_CLUSTER_NAME' ]
      }" \
      -F "deployment=@$TMP_DIR/testwebapp.war" \
      -X POST ${REST_ADDR}/management/wls/latest/deployments/application \
      -o ${CURL_RESPONSE_BODY} \
      --write-out "%{http_code}" \
    )

    echo $HTTP_RESPONSE
    cat $CURL_RESPONSE_BODY

    # verify that curl returned a status code of 200 or 201

    if [ "${HTTP_RESPONSE}" != "200" ] && [ "${HTTP_RESPONSE}" != "201" ]; then
        fail "curl did not return a 200 or 201 status code, got ${HTTP_RESPONSE}"
    fi

    trace 'done'
}

function get_cluster_replicas {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"

    local find_num_replicas="kubectl get domain $DOMAIN_UID -n $NAMESPACE -o jsonpath='{.spec.clusterStartup[?(@.clusterName == \"$WL_CLUSTER_NAME\")].replicas }'"
    local replicas=`eval $find_num_replicas`

    if [ -z ${replicas} ]; then
      fail "replicas not found for $WL_CLUSTER_NAME in domain $DOMAIN_UID"
    fi
    echo $replicas
}

function get_startup_control {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"

    local startup_control_cmd="kubectl get domain $DOMAIN_UID -n $NAMESPACE -o jsonpath='{.spec.startupControl}'"
    local startup_control=`eval $startup_control_cmd`

    if [ -z ${startup_control} ]; then
      fail "startupControl not found in domain $DOMAIN_UID"
    fi

    echo $startup_control
}

function get_dns_legal_name {
    local legal_name=$1
    # the managed server base name of domain2 and domain3 contains invalid DNS charaters
    local legal_dns_name="`legal_dns_name $1`"
    if [ "$legal_dns_name" == "false" ] ; then
      legal_name=$(change_to_legal_dns_name $1)
    fi
    echo "$legal_name"
}

function verify_managed_servers_ready {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local DOM_KEY="$1"
    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"

    local MS_BASE_NAME_DNS_LEGAL=`get_dns_legal_name $MS_BASE_NAME`

    local replicas=`get_cluster_replicas $DOM_KEY`

    local max_count=50
    local wait_time=10

    local i
    trace "verify $replicas number of managed servers for readiness"
    for i in $(seq 1 $replicas);
    do
      local MS_NAME="$DOMAIN_UID-${MS_BASE_NAME_DNS_LEGAL}$i"
      trace "verify that $MS_NAME pod is ready"
      local count=0
      local status="0/1"
      while [ "${status}" != "1/1" -a $count -lt $max_count ] ; do
        local status=`kubectl get pods -n $NAMESPACE | egrep $MS_NAME | awk '{print $2}'`
        local count=`expr $count + 1`
        if [ "${status}" != "1/1" ] ; then
          trace "kubectl ready status is ${status}, iteration $count of $max_count"
          sleep $wait_time
        fi
      done

      if [ "${status}" != "1/1" ] ; then
        kubectl get pods -n $NAMESPACE
        fail "ERROR: the $MS_NAME pod is not running and ready, exiting!"
      fi
    done
}

function test_wls_liveness_probe {
    declare_new_test 1 "$@"
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local DOM_KEY="$1"
    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local MS_BASE_NAME_DNS_LEGAL=`get_dns_legal_name $MS_BASE_NAME`

    local POD_NAME="${DOMAIN_UID}-${MS_BASE_NAME_DNS_LEGAL}1"

    local initial_restart_count=`kubectl describe pod $POD_NAME --namespace=$NAMESPACE | egrep Restart | awk '{print $3}'`

    # First, we kill the mgd server process in the container three times to cause the node manager
    # to mark the server 'failed not restartable'.  This in turn is detected by the liveness probe,
    # which initiates a pod restart.

    cat <<EOF > ${TMP_DIR}/killserver.sh
#!/bin/bash
kill -9 \`jps | grep Server | awk '{print \$1}'\`
EOF

    chmod a+x ${TMP_DIR}/killserver.sh
    kubectl cp ${TMP_DIR}/killserver.sh $POD_NAME:/shared/killserver.sh --namespace=$NAMESPACE
    [ ! "$?" = "0" ] && fail "Error: Make sure that ${TMP_DIR}/killserver.sh exists and $POD_NAME pod is running."

    for value in {1..3}
    do
      kubectl exec -it $POD_NAME /shared/killserver.sh --namespace=$NAMESPACE
      sleep 1
    done
    kubectl exec -it $POD_NAME rm /shared/killserver.sh --namespace=$NAMESPACE

    # Now we verify the pod restarts.
   
    local maxwaitsecs=180
    local mstart=`date +%s`
    while : ; do
      local mnow=`date +%s`
      local final_restart_count=`kubectl describe pod $POD_NAME --namespace=$NAMESPACE | egrep Restart | awk '{print $3}'`
      local restart_count_diff=$((final_restart_count-initial_restart_count))
      if [ $restart_count_diff -eq 1 ]; then
        trace 'WLS liveness probe test is successful.'
        break
      fi
      if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
        fail 'WLS liveness probe is not working.'
      fi
      sleep 5
    done
    declare_test_pass
}

function verify_webapp_load_balancing {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey managedServerCount"
    fi 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local LOAD_BALANCER_WEB_PORT="`dom_get $1 LOAD_BALANCER_WEB_PORT`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local MS_NUM="$2"
    if [ "$MS_NUM" -lt "2" ] || [ "$MS_NUM" -gt "3" ] ; then
       fail "wrong parameters, managed server count must be 2 or 3"
    fi

    local MS_BASE_NAME_DNS_LEGAL=`get_dns_legal_name $MS_BASE_NAME`
    local WL_CLUSTER_NAME_DNS_LEGAL=`get_dns_legal_name $WL_CLUSTER_NAME`

    local list=()
    local i
    for i in $(seq 1 $MS_NUM);
    do
      local msname="$DOMAIN_UID-${MS_BASE_NAME_DNS_LEGAL}$i"
      list+=("$msname")
    done

    trace "confirm the load balancer is working"

    trace "verify that ingress is created.  see $TMP_DIR/describe.ingress.out"
    date >> $TMP_DIR/describe.ingress.out
    kubectl describe ingress -n $NAMESPACE >> $TMP_DIR/describe.ingress.out 2>&1
  
    local TEST_APP_URL="http://${NODEPORT_HOST}:${LOAD_BALANCER_WEB_PORT}/testwebapp/"
    if [ "$LB_TYPE" == "APACHE" ] ; then
      TEST_APP_URL="http://${NODEPORT_HOST}:${LOAD_BALANCER_WEB_PORT}/weblogic/testwebapp/"
    fi
    local CURL_RESPONSE_BODY="$TMP_DIR/testapp.response.body"

    trace "wait for test app to become available on ${TEST_APP_URL}"

    local max_count=30
    local wait_time=6
    local count=0
    local vheader="host: $DOMAIN_UID.$WL_CLUSTER_NAME_DNS_LEGAL"

    while [ "${HTTP_RESPONSE}" != "200" -a $count -lt $max_count ] ; do
      local count=`expr $count + 1`
      echo "NO_DATA" > $CURL_RESPONSE_BODY
      local HTTP_RESPONSE=$(eval "curl --silent --show-error --noproxy ${NODEPORT_HOST} ${TEST_APP_URL} \
        --write-out '%{http_code}' \
        -o ${CURL_RESPONSE_BODY}" \
      )

      if [ "${HTTP_RESPONSE}" != "200" ]; then
        trace "testwebapp did not return 200 status code, got ${HTTP_RESPONSE}, iteration $count of $max_count"
        sleep $wait_time
      fi
    done


    if [ "${HTTP_RESPONSE}" != "200" ]; then
        kubectl get services --all-namespaces
        kubectl get pods -n $NAMESPACE
        local i
        for i in $(seq 1 $MS_NUM);
        do
          kubectl logs ${DOMAIN_UID}-${MS_BASE_NAME_DNS_LEGAL}$i -n $NAMESPACE
        done
        fail "ERROR: testwebapp is not available"
    fi

    local i
    for i in "${list[@]}"; do
      local SERVER_SEARCH_STRING="InetAddress.hostname: $i"
      trace "check if the load balancer can reach $i"
      local from_server=false
      local j
      for j in `seq 1 20`
      do
        echo "NO_DATA" > $CURL_RESPONSE_BODY

        local HTTP_RESPONSE=$(eval "curl --silent --show-error -H '${vheader}' --noproxy ${NODEPORT_HOST} ${TEST_APP_URL} \
          --write-out '%{http_code}' \
          -o ${CURL_RESPONSE_BODY}" \
        )

        echo $HTTP_RESPONSE | sed 's/^/+/'
        cat $CURL_RESPONSE_BODY | sed 's/^/+/'

        if [ "${HTTP_RESPONSE}" != "200" ]; then
          trace "curl did not return a 200 status code, got ${HTTP_RESPONSE}"
          continue
        else
          if grep -q "${SERVER_SEARCH_STRING}" $CURL_RESPONSE_BODY; then
            local from_server=true
            trace " get response from $i"
            break
          fi
        fi
      done
      if [ "$from_server" = false ]; then
        fail "load balancer can not reach server $i"
      fi
    done

    trace 'done'
}

function verify_admin_server_ext_service {

    # Pre-requisite: requires admin server to be already up and running and able to service requests

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local WLS_ADMIN_USERNAME="`get_wladmin_user $1`"
    local WLS_ADMIN_PASSWORD="`get_wladmin_pass $1`"

    local ADMIN_SERVER_NODEPORT_SERVICE="$DOMAIN_UID-admin-server"

    trace "verify that admin server REST and console are accessible from outside of the kubernetes cluster"

    local get_configured_nodePort="kubectl get domains -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$DOMAIN_UID\")].spec.serverStartup[?(@.serverName == \"admin-server\")].nodePort}'"

    local configuredNodePort=`eval $get_configured_nodePort`

    trace "configured NodePort for the admin server in domain $DOMAIN_UID is ${configuredNodePort}"

    if [ -z ${configuredNodePort} ]; then
      kubectl describe domain $DOMAIN_UID -n $NAMESPACE
      trace "Either domain $DOMAIN_UID does not exist or no NodePort is not configured for the admin server in domain $DOMAIN_UID. Skipping this verify"
      return
    fi

    local get_service_nodePort="kubectl get services -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$ADMIN_SERVER_NODEPORT_SERVICE\")].spec.ports[0].nodePort}'"
   
    trace get_service_nodePort

    set +x     
    local nodePort=`eval $get_service_nodePort`
    set -x     

    if [ -z ${nodePort} ]; then
      fail "nodePort not found in domain $DOMAIN_UID"
    fi

    if [ "$nodePort" -ne "$configuredNodePort" ]; then
      fail "Configured NodePort of ${configuredNodePort} for the admin server is different from nodePort found in service ${ADMIN_SERVER_NODEPORT_SERVICE}: ${nodePort}"
    fi

    local TEST_REST_URL="http://${NODEPORT_HOST}:${nodePort}/management/weblogic/latest/serverRuntime"

    local CURL_RESPONSE_BODY="$TMP_DIR/testconsole.response.body"

    echo "NO_DATA" > $CURL_RESPONSE_BODY

    set +x
    local HTTP_RESPONSE=$(curl --silent --show-error --noproxy ${NODEPORT_HOST} ${TEST_REST_URL} \
      --user ${WLS_ADMIN_USERNAME}:${WLS_ADMIN_PASSWORD} \
      -H X-Requested-By:Integration-Test \
      --write-out "%{http_code}" \
      -o ${CURL_RESPONSE_BODY} \
    )
    set -x

    trace "REST test: $HTTP_RESPONSE "

    if [ "${HTTP_RESPONSE}" != "200" ]; then
      cat $CURL_RESPONSE_BODY
      fail "accessing admin server REST endpoint did not return 200 status code, got ${HTTP_RESPONSE}"
    fi

    local TEST_CONSOLE_URL="http://${NODEPORT_HOST}:${nodePort}/console/login/LoginForm.jsp"

    echo "NO_DATA" > $CURL_RESPONSE_BODY

    set +x
    local HTTP_RESPONSE=$(curl --silent --show-error --noproxy ${NODEPORT_HOST} ${TEST_CONSOLE_URL} \
      --write-out "%{http_code}" \
      -o ${CURL_RESPONSE_BODY} \
    )
    set -x

    trace "console test: $HTTP_RESPONSE "

    if [ "${HTTP_RESPONSE}" != "200" ]; then
      cat $CURL_RESPONSE_BODY
      fail "accessing admin console did not return 200 status code, got ${HTTP_RESPONSE}"
    fi

    trace 'done'
}

function verify_admin_console_via_loadbalancer {

    # Pre-requisite: requires admin server to be already up and running and able to service requests

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    # We only perform this verification when the load balancer type is APACHE
    if [ "$LB_TYPE" != "APACHE" ]; then
      return
    fi 

    trace "verify that admin console is accessible via Apache load balancer from outside of the kubernetes cluster"

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local WLS_ADMIN_USERNAME="`get_wladmin_user $1`"
    local WLS_ADMIN_PASSWORD="`get_wladmin_pass $1`"
    local LOAD_BALANCER_EXPOSE_ADMIN_PORT="`dom_get $1 LOAD_BALANCER_EXPOSE_ADMIN_PORT`"

    local ADMIN_SERVER_LB_NODEPORT_SERVICE="$DOMAIN_UID-apache-webtier"

    local get_service_nodePort="kubectl get services -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$ADMIN_SERVER_LB_NODEPORT_SERVICE\")].spec.ports[0].nodePort}'"
   
    trace get_service_nodePort

    set +x     
    local nodePort=`eval $get_service_nodePort`
    set -x     

    if [ -z ${nodePort} ]; then
      fail "nodePort not found in domain $DOMAIN_UID"
    fi

    local TEST_CONSOLE_URL="http://${NODEPORT_HOST}:${nodePort}/console/login/LoginForm.jsp"
    local CONSOLE_RESPONSE_BODY="$TMP_DIR/testconsole.response.body"

    trace "console test url: $TEST_CONSOLE_URL "
    echo "NO_DATA" > $CONSOLE_RESPONSE_BODY

    set +x
    local HTTP_RESPONSE=$(curl --silent --show-error --noproxy ${NODEPORT_HOST} ${TEST_CONSOLE_URL} \
      --write-out "%{http_code}" \
      -o ${CONSOLE_RESPONSE_BODY} \
    )
    set -x

    trace "console test response: $HTTP_RESPONSE "

    if [ "$LOAD_BALANCER_EXPOSE_ADMIN_PORT" == "false" ]; then
      if [ "${HTTP_RESPONSE}" == "200" ]; then
        cat $CONSOLE_RESPONSE_BODY
        fail "accessing admin console via load balancer returned status code ${HTTP_RESPONSE} unexpectedly"
      fi
    else 
      if [ "${HTTP_RESPONSE}" != "200" ]; then
        cat $CONSOLE_RESPONSE_BODY
        fail "accessing admin console via load balancer did not return 200 status code, got ${HTTP_RESPONSE}"
      fi
    fi

    trace 'done'

}

function test_domain_creation {
    declare_new_test 1 "$@"
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    create_domain_pv_pvc_load_balancer $DOM_KEY
    verify_domain_created $DOM_KEY 
    verify_managed_servers_ready $DOM_KEY

    #deploy_webapp_via_REST $DOM_KEY
    deploy_webapp_via_WLST $DOM_KEY
    verify_webapp_load_balancing $DOM_KEY 2

    verify_admin_server_ext_service $DOM_KEY

    verify_admin_console_via_loadbalancer $DOM_KEY
    
    extra_weblogic_checks
    declare_test_pass
}

function verify_domain {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    verify_domain_created $DOM_KEY 
    verify_managed_servers_ready $DOM_KEY

    verify_webapp_load_balancing $DOM_KEY 2
    verify_admin_server_ext_service $DOM_KEY
}

function extra_weblogic_checks {

    trace 'Pani will contribute some extra checks here'

}

# This function call operator Rest api with the given url
function call_operator_rest {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: operatorKey urlTail"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local OPERATOR_TMP_DIR="`op_get $OP_KEY TMP_DIR`"
    local URL_TAIL="${2}"

    trace "URL_TAIL=$URL_TAIL"

    trace "Checking REST service is running"
    set +x
    local REST_SERVICE="`kubectl get services -n $OPERATOR_NS -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-svc")]}'`"
    set -x
    if [ -z "$REST_SERVICE" ]; then
        fail 'operator rest service was not created'
    fi

    set +x
    local REST_PORT="`kubectl get services -n $OPERATOR_NS -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-svc")].spec.ports[?(@.name == "rest")].nodePort}'`"
    set -x
    local REST_ADDR="https://${NODEPORT_HOST}:${REST_PORT}"
    local SECRET="`kubectl get serviceaccount weblogic-operator -n $OPERATOR_NS -o jsonpath='{.secrets[0].name}'`"
    local ENCODED_TOKEN="`kubectl get secret ${SECRET} -n $OPERATOR_NS -o jsonpath='{.data.token}'`"
    local TOKEN="`echo ${ENCODED_TOKEN} | base64 --decode`"

    local OPERATOR_CERT_DATA="`grep externalOperatorCert: ${OPERATOR_TMP_DIR}/weblogic-operator-values.yaml | awk '{ print $2 }'`"

    local OPERATOR_CERT_FILE="${OPERATOR_TMP_DIR}/operator.cert.pem"
    echo ${OPERATOR_CERT_DATA} | base64 --decode > ${OPERATOR_CERT_FILE}

    trace "Calling some operator REST APIs via ${REST_ADDR}/${URL_TAIL}"

    #pod=`kubectl get pod -n $OPERATOR_NS --show-labels=true | grep $OPERATOR_NS | awk '{ print $1 }'`
    #kubectl logs $pod -n $OPERATOR_NS > "${OPERATOR_TMP_DIR}/operator.pre.rest.log"

    # turn off all of the https proxying so that curl will work
    OLD_HTTPS_PROXY="${HTTPS_PROXY}"
    old_https_proxy="${https_proxy}"
    export HTTPS_PROXY=""
    export https_proxy=""

    local OPER_CURL_STDERR="${OPERATOR_TMP_DIR}/operator.rest.stderr"
    local OPER_CURL_RESPONSE_BODY="${OPERATOR_TMP_DIR}/operator.rest.response.body"

    echo "NO_DATA" > $OPER_CURL_STDERR
    echo "NO_DATA" > $OPER_CURL_RESPONSE_BODY

    local STATUS_CODE="`curl --silent --show-error \
        -v \
        --cacert ${OPERATOR_CERT_FILE} \
        -H "Authorization: Bearer ${TOKEN}" \
        -H Accept:application/json \
        -X GET ${REST_ADDR}/${URL_TAIL} \
        -o ${OPER_CURL_RESPONSE_BODY} \
        --stderr ${OPER_CURL_STDERR} \
        -w "%{http_code}"`"

    # restore the https proxying now that we're done using curl
    export HTTPS_PROXY="${OLD_HTTPS_PROXY}"
    export https_proxy="${old_https_proxy}"

    #kubectl logs $pod -n $OPERATOR_NS > "${OPERATOR_TMP_DIR}/operator.post.rest.log"
    #diff ${OPERATOR_TMP_DIR}/operator.pre.rest.log ${OPERATOR_TMP_DIR}/operator.post.rest.log

    # verify that curl returned a status code of 200
    # e.g. < HTTP/1.1 200 OK
    if [ "${STATUS_CODE}" = "200" ]; then
        # '+' marks verbose tracing
        cat $OPER_CURL_STDERR | sed 's/^/+/'
        cat $OPER_CURL_RESPONSE_BODY | sed 's/^/+/'
        cat ${OPERATOR_CERT_FILE} | sed 's/^/+/'
        trace "Done"
    else
        cat $OPER_CURL_STDERR
        cat $OPER_CURL_RESPONSE_BODY 
        cat ${OPERATOR_CERT_FILE} 
        fail "curl did not return a 200 status code, it returned ${STATUS_CODE}"
    fi
}

function confirm_mvn_build {
    local fail_out=`grep FAIL ${1:?}`

    if [ "$fail_out" != "" ]; then
      cat $1 | sed 's/^/+/'
      fail "ERROR: found FAIL in output $1" 
    fi

    local success_out=`grep SUCCESS $1`

    if [ "$success_out" = "" ]; then
      cat $1 | sed 's/^/+/'
      fail "ERROR: didn't find SUCCESS in output $1" 
    fi
}

function test_mvn_integration_jenkins {
    declare_new_test 1 "$@"

    trace "generating job to run mvn -P integration-tests clean install "

    local job_name=integration-test-$RANDOM
    local job_yml=$RESULT_DIR/$job_name.yml
    local job_workspace=$RESULT_DIR/$job_name/workspace
    local uid=`id -u`
    local gid=`id -g`

    local JOBDEF=`cat <<EOF > $job_yml
apiVersion: batch/v1
kind: Job
metadata:
  name: $job_name
spec:
    template:
       metadata:
         name: $job_name
       spec:
         containers:
         - name: $job_name
           image: store/oracle/serverjre:8
           command: ["/workspace/run_test.sh"]
           volumeMounts:
           - name: workspace
             mountPath: /workspace
         restartPolicy: Never
         securityContext:
           runAsUser: $uid
           fsGroup: $gid
         volumes:
         - name: workspace
           hostPath:
             path:  "$job_workspace"
EOF
`
    trace "copying source code, java and mvn into workspace folder to be shared with pod"

    mkdir -p  $job_workspace
    rsync -a $PROJECT_ROOT $job_workspace/weblogic-operator
    rsync -a $M2_HOME/ $job_workspace/apache-maven
    rsync -a $JAVA_HOME/ $job_workspace/java 

    cat <<EOF > $job_workspace/run_test.sh
#!/bin/sh
export M2_HOME=/workspace/apache-maven
export M2=\$M2_HOME/bin
export JAVA_HOME=/workspace/java
export PATH=\$M2:\$JAVA_HOME/bin:\$PATH
set -x
cd /workspace/weblogic-operator
mvn --settings ../settings.xml -P integration-tests clean install > /workspace/mvn.out
EOF


    cat <<EOF > $job_workspace/settings.xml
<settings>
  <proxies>
   <proxy>
      <id>www-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>www-proxy.us.oracle.com</host>
      <port>80</port>
      <nonProxyHosts>*.oraclecorp.com|*.oracle.com|*.us.oracle.com|127.0.0.1</nonProxyHosts>
    </proxy>
   <proxy>
      <id>wwws-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>www-proxy.us.oracle.com</host>
      <port>80</port>
      <nonProxyHosts>*.oraclecorp.com|*.oracle.com|*.us.oracle.com|127.0.0.1</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
EOF

    chmod a+x $job_workspace/run_test.sh

    kubectl create -f $job_yml

    trace "job created to run mvn -P integration-tests clean install , check $job_workspace/mvn.out"

    local status="0"
    local max=20
    local count=0
    while [ ${status:=0} != "1" -a $count -lt $max ] ; do
      sleep 30
      local status=`kubectl get job $job_name | egrep $job_name | awk '{print $3}'`
      trace "kubectl status is ${status:=Error}, iteration $count of $max"
      local count=`expr $count + 1`
    done
    local status=`kubectl get job $job_name | egrep $job_name | awk '{print $3}'`
    if [ ${status:=0} != "1" ] ; then
      fail "ERROR: kubectl get job reports status=${status:=0} after running, exiting!"
    fi

    confirm_mvn_build $job_workspace/mvn.out

    cat $job_workspace/mvn.out

    kubectl delete job $job_name

    # save the mvn.out somewhere before rm?
    rm -rf $job_workspace
    rm $job_yml
    declare_test_pass
}

function test_mvn_integration_local {
    declare_new_test 1 "$@"

    trace "Running mvn -P integration-tests clean install.  Output in $RESULT_DIR/mvn.out"

    which mvn 2>&1 > /dev/null 2>&1
    [ "$?" = "0" ] || fail "Error: Could not find mvn in path."

    local mstart=`date +%s`
    mvn -P integration-tests clean install 2>&1 | opt_tee $RESULT_DIR/mvn.out

    local mend=`date +%s`
    local msecs=$((mend-mstart))
    trace "mvn complete, runtime $msecs seconds"

    confirm_mvn_build $RESULT_DIR/mvn.out

    export JAR_VERSION="`grep -m1 "<version>" pom.xml | cut -f2 -d">" | cut -f1 -d "<"`"
    trace "Running docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" --build-arg VERSION=$JAR_VERSION --no-cache=true ."
    docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" --build-arg VERSION=$JAR_VERSION --no-cache=true . 2>&1 | opt_tee $RESULT_DIR/docker_build_tag.out
    [ "$?" = "0" ] || fail "Error:  Failed to docker tag operator image, see $RESULT_DIR/docker_build_tag.out".

    declare_test_pass
}

function test_mvn_integration_wercker {
    declare_new_test 1 "$@"

    local mstart=`date +%s`
    trace "Running mvn -P integration-tests.  Output in $RESULT_DIR/mvn.out"
    mvn -P integration-tests  install 2>&1 | opt_tee $RESULT_DIR/mvn.out
    local mend=`date +%s`
    local msecs=$((mend-mstart))
    trace "mvn complete, runtime $msecs seconds"

    confirm_mvn_build $RESULT_DIR/mvn.out
    declare_test_pass
}

function check_pv {

    trace "Checking if the persistent volume ${1:?} is ${2:?}"
    local pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
    local attempts=0
    while [ ! "$pv_state" = "$2" ] && [ ! $attempts -eq 10 ]; do
        local attempts=$((attempts + 1))
        sleep 1
        local pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
    done
    if [ "$pv_state" != "$2" ]; then
        fail "The Persistent Volume should be $2 but is $pv_state"
    fi
}

function get_wladmin_cred {
  if [ "$#" != 2 ]; then
    fail "requires two parameters:  domainKey and keyword 'username' or 'password'."
  fi
  # All domains use the same user/pass
  local TMP_DIR="`dom_get $1 TMP_DIR`"
  if ! val=`grep "^  $2:" $TMP_DIR/weblogic-credentials.yaml | awk '{ print $2 }' | base64 -d`
  then
    fail "get_wladmin_cred:  Could not determine $1"
  fi
  echo $val
}

function get_wladmin_pass {
  if [ "$#" != 1 ] ; then
    fail "requires 1 parameter: domainKey"
  fi 
  get_wladmin_cred $1 password
}

function get_wladmin_user {
  if [ "$#" != 1 ] ; then
    fail "requires 1 parameter: domainKey"
  fi 
  get_wladmin_cred $1 username
}

function verify_wlst_access {
  #Note:  uses admin server pod's ADMIN_WLST_PORT

  if [ "$#" != 1 ] ; then
    fail "requires 1 parameter: domainKey"
  fi 

  local DOM_KEY="$1"

  local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
  local TMP_DIR="`dom_get $1 TMP_DIR`"

  trace "Testing WLST connectivity to pod $AS_NAME"

  local pyfile_con=$TMP_DIR/connect.py
  cat << EOF > ${pyfile_con}
  connect(sys.argv[1],sys.argv[2],sys.argv[3])
EOF

  run_wlst_script $1 local ${pyfile_con} 

  trace Passed
}


# run_wlst_script
#   - runs an arbitrary wlst script either locally or remotely on the admin server
#   - always passes "username password url" as first parameters to the wlst script
#   - plus passes any additional arguments after the third argument
#   - modes:
#   -   local  --> run locally - requires java & weblogic to be installed
#   -   remote --> run remotely on admin server, construct URL using admin pod name
#   -   hybrid --> run remotely on admin server, construct URL using 'NODEPORT_HOST'
#                  or 'K8S_NODEPORT_IP' in wercker since NODEPORT_HOST sometimes does not work in OCI
#
function run_wlst_script {
  if [ "$#" -lt 3 ] ; then
    fail "requires at least 3 parameters: domainKey local|remote|hybrid local_pyfile optionalarg1 optionalarg2 ..."
  fi 

  # TODO It seems possible to obtain user/pass from the secret via WLST verbs.  This
  #      would be better than passing it to the WLST command-line in plain-text.  See
  #      read-domain-secret.py in domain-job-template for an example of how this is done
  #      for WLST that runs from within a pod...

  local DOM_KEY="$1"
  local NAMESPACE="`dom_get $1 NAMESPACE`"
  local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
  local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
  local TMP_DIR="`dom_get $1 TMP_DIR`"
  local AS_NAME="$DOMAIN_UID-admin-server"
  local location="$2"
  local username=`get_wladmin_user $1` 
  local password=`get_wladmin_pass $1`
  local pyfile_lcl="$3"
  local pyfile_pod="/shared/`basename $pyfile_lcl`"
  if [ "$WERCKER" = "true" ]; then 
    # use OCI public IP in wercker
    local t3url_lcl="t3://$K8S_NODEPORT_IP:$ADMIN_WLST_PORT"
  else
    local t3url_lcl="t3://$NODEPORT_HOST:$ADMIN_WLST_PORT"
  fi
  local t3url_pod="t3://$AS_NAME:$ADMIN_WLST_PORT"
  local wlcmdscript_lcl="$TMP_DIR/wlcmd.sh"
  local wlcmdscript_pod="/shared/wlcmd.sh"

  shift
  shift
  shift

  if [ "$location" = "local" ]; then
  
    # If local java weblogic.WLST isn't setup, then switch to 'hybrid' mode.

    cat << EOF > $TMP_DIR/empty.py
EOF
    java weblogic.WLST $TMP_DIR/empty.py 2>&1 | opt_tee $TMP_DIR/empty.py.out
    if [ "$?" = "0" ]; then
      # We're running WLST locally.  No need to do anything fancy.
      local mycommand="java weblogic.WLST ${pyfile_lcl} ${username} ${password} ${t3url_lcl}"
    else
      trace "Warning - Could not run 'java weblogic.WLST' locally.  Running WLST remotely in a pod instead.  See $TMP_DIR/empty.py.out for details."
      # prepend verbose output with a '+':
      cat $TMP_DIR/empty.py.out | sed 's/^/+/g'
      location="hybrid"
    fi
  fi

  if [ "$location" = "remote" -o "$location" = "hybrid" ]; then

    # We're running WLST remotely, so we need to copy the py script 
    # and a helper sh script to the admin server, plus  build up a
    # kubectl exec command.

    cat << EOF > $wlcmdscript_lcl
#!/usr/bin/bash
#
# This is a script for running arbitrary WL command line commands.
# Usage example:  $wlcmdscript_pod java weblogic.version
#
ARG="\$*"
ENVSCRIPT="\`find /shared -name setDomainEnv.sh\`" || exit 1
echo Sourcing \$ENVSCRIPT
. \$ENVSCRIPT || exit 1
echo "\$@"
echo Calling \$ARG
eval \$ARG || exit 1
exit 0
EOF

    local mycommand="kubectl cp $wlcmdscript_lcl ${NAMESPACE}/${AS_NAME}:$wlcmdscript_pod"
    trace "Copying wlcmd to pod $AS_NAME in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl cp command failed."

    local mycommand="kubectl -n ${NAMESPACE} exec -it ${AS_NAME} chmod 777 $wlcmdscript_pod"
    trace "Changing file permissions on pod wlcmd script using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl exec chmod command failed."

    local mycommand="kubectl cp ${pyfile_lcl} ${NAMESPACE}/${AS_NAME}:${pyfile_pod}"
    trace "Copying wlst to $DOMAIN_UID in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl cp command failed."

    local mycommand="kubectl -n ${NAMESPACE} exec -it ${AS_NAME} $wlcmdscript_pod java weblogic.WLST ${pyfile_pod} ${username} ${password}"

    if [ "$location" = "hybrid" ]; then
      # let's see if we can access the t3 channel external port from within a pod using 'NODEPORT_HOST' instead of pod name
      mycommand="${mycommand} ${t3url_lcl}"
    else
      # otherwise let's use an URL constructed from the pod name (still using t3 port)
      mycommand="${mycommand} ${t3url_pod}"
    fi
  fi
 
  trace "Running \"$mycommand $@\""

  # it's theoretically possible for a booting pod to have its default port running but
  # not its t3-channel port, so we retry on a failure

  local mstart=`date +%s`
  local maxwaitsecs=180
  local failedonce="false"
  while : ; do
    eval "$mycommand ""$@" 2>&1 | opt_tee ${pyfile_lcl}.out
    local result="$?"

    # '+' marks verbose tracing
    cat ${pyfile_lcl}.out | sed 's/^/+/'

    if [ "$result" = "0" ];
    then 
      cat ${pyfile_lcl}.out
      break
    fi

    if [ "$failedonce" = "false" ]; then
      cp ${pyfile_lcl}.out ${pyfile_lcl}.out.firstfail
      failedonce="true"
    fi

    local mnow=`date +%s`
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      cat ${pyfile_lcl}.out.firstfail
      fail "Could not successfully run WLST script ${pyfile_lcl} on pod ${AS_NAME} via ${t3url} within ${maxwaitsecs} seconds.  Giving up."
    fi

    trace "WLST script ${pyfile_lcl} failed.  Wait time $((mnow - mstart)) seconds (max=${maxwaitsecs}).  Will retry."
    sleep 10
  done

  trace "Passed."
}

function verify_ms_connectivity {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    trace "Checking JMX connectivity between admin and managed servers using WLST"

    local pyfile_ms=$TMP_DIR/check_ms.py
    cat << EOF > ${pyfile_ms}
connect(sys.argv[1],sys.argv[2],sys.argv[3])
domainRuntime()
cd("ServerRuntimes")
if (len(sys.argv)) > 3 :
  slist=range(4,len(sys.argv))
  for i in slist:
    cd(sys.argv[i])
    cd("../")
EOF
  
    local replicas=`get_cluster_replicas $1`
    local i
    local ms_name_list=""
    for i in $(seq 1 $replicas);
    do
      local msname="${MS_BASE_NAME}$i"
      ms_name_list="$msname $ms_name_list"
    done

    run_wlst_script $1 remote ${pyfile_ms} ${ms_name_list}

    trace "Done."
}

#
# deploy_webapp_via_WLST
#
# This function
#   waits until the admin server has established JMX communication with managed servers
#   copies a webapp to the admin pod /shared/applications directory
#   copies a deploy WLST script to the admin pod
#   executes the WLST script from within the pod
#
# It is possible to modify this function to run deploy.py outside of the k8s cluster
# and upload the file.
#
# Note that this function has slightly different parameters than deploy_webapp_via_REST.
#
function deploy_webapp_via_WLST {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local as_name="$DOMAIN_UID-admin-server"
    local appname="testwebapp"
    local appwar_lcl="$TMP_DIR/testwebapp.war"
    local appwar_pod="/shared/applications/testwebapp.war"
    local pyfile_lcl="$TMP_DIR/deploy.py"

    # Make sure the admin server has established JMX connection with all managed servers
    verify_ms_connectivity $1

    # To instead run the WLST locally, must set remote='true' below,
    # plus modify run_wlst_script 'remote' parameter to 'local'.
    # And to furthermore have local WLST upload a local war, modify upload to 'true',

    cat << EOF > ${pyfile_lcl}
connect(sys.argv[1],sys.argv[2],sys.argv[3])
deploy(sys.argv[4],sys.argv[5],sys.argv[6],upload='false',remote='false')
# deploy(sys.argv[4],sys.argv[5],sys.argv[6],upload='false',remote='true')
EOF

    local mycommand="kubectl cp ${appwar_lcl} ${NAMESPACE}/${as_name}:${appwar_pod}"
    trace "Copying webapp to $DOMAIN_UID in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl cp command failed."

    local mycommand_args="${appname} ${appwar_pod} ${WL_CLUSTER_NAME}"

    trace "Deploying webapp to ${DOMAIN_UID} in namespace ${NAMESPACE}"
    run_wlst_script ${1} remote ${pyfile_lcl} ${mycommand_args}

    trace "Done."
}

#
# Function to check if a value is lowercase and legal DNS name
# $1 - value to check
# $2 - name of object being checked
function legal_dns_name {
  local val=$(change_to_legal_dns_name $1)
  if [ "$val" != "$1" ]; then
    echo "false"
  else 
    echo "true"
  fi
}

#
# Function to lowercase a value and make it a legal DNS name
# $1 - value to convert to lowercase
function change_to_legal_dns_name {
    local lc=`echo $1 | tr "[:upper:]" "[:lower:]"`
    local value=${lc//"_"/"-"}
    echo "$value"
}

function verify_service_and_pod_created {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainkey serverNum, set serverNum to 0 to indicate the admin server"
    fi

    local DOM_KEY="${1}"

    local OP_KEY="`dom_get $1 OP_KEY`"
    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"

    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local OPERATOR_TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local MS_BASE_NAME_DNS_LEGAL=`get_dns_legal_name $MS_BASE_NAME`

    local SERVER_NUM="$2"

    if [ "$SERVER_NUM" = "0" ]; then 
      local IS_ADMIN_SERVER="true"
      local POD_NAME="${DOMAIN_UID}-admin-server"
    else
      local IS_ADMIN_SERVER="false"
      local POD_NAME="${DOMAIN_UID}-${MS_BASE_NAME_DNS_LEGAL}${SERVER_NUM}"
    fi

    local SERVICE_NAME="${POD_NAME}"

    local max_count_srv=50
    local max_count_pod=50
    local wait_time=10
    local count=0
    local srv_count=0

    trace "checking if service $SERVICE_NAME is created"
    while [ "${srv_count:=Error}" != "1" -a $count -lt $max_count_srv ] ; do
      local count=`expr $count + 1`
      local srv_count=`kubectl -n $NAMESPACE get services | grep "^$SERVICE_NAME " | wc -l`
      if [ "${srv_count:=Error}" != "1" ]; then
        trace "Did not find service $SERVICE_NAME, iteration $count of $max_count_srv"
        sleep $wait_time
      fi
    done

    if [ "${srv_count:=Error}" != "1" ]; then
      local pod=`kubectl get pod -n $OPERATOR_NS --show-labels=true | grep $OPERATOR_NS | awk '{ print $1 }'`
      local debuglog="${OPERATOR_TMP_DIR}/verify_domain_debugging.log"
      kubectl logs $pod -n $OPERATOR_NS > "${debuglog}"
      if [ -f ${debuglog} ] ; then
        tail -20 ${debuglog}
      fi

      fail "ERROR: the service $SERVICE_NAME is not created, exiting!"
    fi

    if [ "${IS_ADMIN_SERVER}" == "true" ]; then
      local EXTCHANNEL_T3CHANNEL_SERVICE_NAME=${SERVICE_NAME}-extchannel-t3channel
      trace "checking if service ${EXTCHANNEL_T3CHANNEL_SERVICE_NAME} is created"
      count=0
      srv_count=0
      while [ "${srv_count:=Error}" != "1" -a $count -lt $max_count_srv ] ; do
        local count=`expr $count + 1`
        local srv_count=`kubectl -n $NAMESPACE get services | grep "^$EXTCHANNEL_T3CHANNEL_SERVICE_NAME " | wc -l`
        if [ "${srv_count:=Error}" != "1" ]; then
          trace "Did not find service $EXTCHANNEL_T3CHANNEL_SERVICE_NAME, iteration $count of $max_count_srv"
          sleep $wait_time
        fi
      done
    fi

    if [ "${srv_count:=Error}" != "1" ]; then
      local pod=`kubectl get pod -n $OPERATOR_NS --show-labels=true | grep $OPERATOR_NS | awk '{ print $1 }'`
      local debuglog="${OPERATOR_TMP_DIR}/verify_domain_debugging.log"
      kubectl logs $pod -n $OPERATOR_NS > "${debuglog}"
      if [ -f ${debuglog} ] ; then
        tail -20 ${debuglog}
      fi

      fail "ERROR: the service $EXTCHANNEL_T3CHANNEL_SERVICE_NAME is not created, exiting!"
    fi

    trace "checking if pod $POD_NAME is successfully started"
    local status="NotRunning"
    local count=0
    while [ ${status:=Error} != "Running" -a $count -lt $max_count_pod ] ; do
      local status=`kubectl -n $NAMESPACE describe pod $POD_NAME | grep "^Status:" | awk ' { print $2; } '`
      local count=`expr $count + 1`
      if [ ${status:=Error} != "Running" ] ; then
        trace "pod status is ${status:=Error}, iteration $count of $max_count_pod"
        sleep $wait_time
      fi
    done

    if [ ${status:=Error} != "Running" ] ; then
      fail "ERROR: pod $POD_NAME is not running, exiting!"
    fi

    trace "checking if pod $POD_NAME is ready"
    local ready="false"
    local count=0
    while [ ${ready:=Error} != "true" -a $count -lt $max_count_pod ] ; do
      local ready=`kubectl -n $NAMESPACE get pod $POD_NAME -o jsonpath='{.status.containerStatuses[0].ready}'`
      local count=`expr $count + 1`
      if [ ${ready:=Error} != "true" ] ; then
        trace "pod readiness is ${ready:=Error}, iteration $count of $max_count_pod"
        sleep $wait_time
      fi
    done
    if [ ${ready:=Error} != "true" ] ; then
      fail "ERROR: pod $POD_NAME is not ready, exiting!"
    fi

    if [ "${IS_ADMIN_SERVER}" = "true" ]; then
      trace "listing pods"
      kubectl -n $NAMESPACE get pods -o wide

      verify_wlst_access $DOM_KEY
    fi
}


function verify_domain_created {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"

    local MS_BASE_NAME_DNS_LEGAL=`get_dns_legal_name $MS_BASE_NAME`

    trace "verify domain $DOMAIN_UID in $NAMESPACE namespace"

    # Prepend "+" to detailed debugging to make it easy to filter out

    kubectl get all -n $NAMESPACE --show-all 2>&1 | sed 's/^/+/' 2>&1

    kubectl get domains  -n $NAMESPACE 2>&1 | sed 's/^/+/' 2>&1

    trace 'checking if the domain is created'
    local count=`kubectl get domain $DOMAIN_UID -n $NAMESPACE |grep "^$DOMAIN_UID " | wc -l `
    if [ ${count:=Error} != 1 ] ; then
      fail "ERROR: domain not found, exiting!"
    fi

    trace "verify the service and pod of admin server"
    verify_service_and_pod_created $DOM_KEY 0

    local startup_control=`get_startup_control $DOM_KEY`

    local verify_as_only=false
    if [ "${startup_control}" = "ADMIN" ] ; then
      verify_as_only=true
    fi

    local replicas=`get_cluster_replicas $DOM_KEY`

    trace "verify $replicas number of managed servers for creation"
    local i
    for i in $(seq 1 $replicas);
    do
      local MS_NAME="$DOMAIN_UID-${MS_BASE_NAME_DNS_LEGAL}$i"
      trace "verify service and pod of server $MS_NAME"
      if [ "${verify_as_only}" = "true" ]; then 
        verify_pod_deleted $DOM_KEY $i
      else
        verify_service_and_pod_created $DOM_KEY $i
      fi
    done

    # Check if we got exepcted number of managed servers running
    local ms_name_common="${DOMAIN_UID}-${MS_BASE_NAME_DNS_LEGAL}"
    local pod_count=`kubectl get pods -n $NAMESPACE |grep "^${ms_name_common}" | wc -l `
    if [ ${pod_count:=Error} != $replicas ] && [ "${verify_as_only}" != "true" ] ; then
      fail "ERROR: expected $replicas number of managed servers running, but got $pod_count, exiting!"
    fi
    if [ ${pod_count:=Error} != 0 ] && [ "${verify_as_only}" = "true" ] ; then
      fail "ERROR: expected none of managed servers running, but got $pod_count, exiting!"
    fi
}

function verify_pod_deleted {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainkey serverNum, set serverNum to 0 to indicate the admin server"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"

    local MS_BASE_NAME_DNS_LEGAL=`get_dns_legal_name $MS_BASE_NAME`

    local SERVER_NUM="$2"

    if [ "$SERVER_NUM" = "0" ]; then 
      local POD_NAME="${DOMAIN_UID}-admin-server"
    else
      local POD_NAME="${DOMAIN_UID}-${MS_BASE_NAME_DNS_LEGAL}${SERVER_NUM}"
    fi

    local max_count_srv=50
    local max_count_pod=50
    local wait_time=10

    trace "checking if pod $POD_NAME is deleted"
    local pod_count=1
    local count=0

    while [ "${pod_count:=Error}" != "0" -a $count -lt $max_count_pod ] ; do
      local pod_count=`kubectl -n $NAMESPACE get pod $POD_NAME | grep "^$POD_NAME " | wc -l`
      local count=`expr $count + 1`
      if [ "${pod_count:=Error}" != "0" ] ; then
        trace "pod $POD_NAME still exists, iteration $count of $max_count_srv"
        sleep $wait_time
      fi
    done

    if [ "${pod_count:=Error}" != "0" ] ; then
      fail "ERROR: pod $POD_NAME is not deleted, exiting!"
    fi
}

function verify_domain_deleted {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey managedServerCount"
    fi 
    trace Begin. 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"

    local MS_NUM="$2"

    trace "verify domain $DOMAIN_UID in $NAMESPACE namespace is deleted"

    # Prepend "+" to detailed debugging to make it easy to filter out

    kubectl get all -n $NAMESPACE --show-all 2>&1 | sed 's/^/+/' 2>&1

    kubectl get domains -n $NAMESPACE 2>&1 | sed 's/^/+/' 2>&1

    trace 'checking if the domain is deleted'
    local count=`kubectl get domain $DOMAIN_UID -n $NAMESPACE | egrep $DOMAIN_UID | wc -l `
    if [ ${count:=Error} != 0 ] ; then
      fail "ERROR: domain still exists, exiting!"
    fi

    trace "verify the pod of admin server is deleted"
    verify_pod_deleted $DOM_KEY 0

    trace "verify $MS_NUM number of managed servers for deletion"
    local i
    for i in $(seq 1 $MS_NUM);
    do
      trace "verify pod of managed server $i is deleted"
      verify_pod_deleted $DOM_KEY $i
    done
    trace Done. Verified.
}

function shutdown_domain {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    trace Begin. 
    local DOM_KEY="$1"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local replicas=`get_cluster_replicas $DOM_KEY`

    if [ "$USE_HELM" = "true" ]; then
      trace "calling helm delete ${DOM_KEY} --purge"
      helm delete ${DOM_KEY} --purge
    else
      kubectl delete -f ${TMP_DIR}/domain-custom-resource.yaml
    fi

    verify_domain_deleted $DOM_KEY $replicas
    trace Done. 
}

function startup_domain {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameters: domainKey"
    fi 

    local DOM_KEY="$1"

    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local NAMESPACE="`dom_get $1 NAMESPACE`"

    if [ "$USE_HELM" = "true" ]; then
      local inputs=$TMP_DIR/create-weblogic-domain-inputs.yaml
      local outfile="$TMP_DIR/startup-weblogic-domain.out"
      cd $PROJECT_ROOT/kubernetes/charts
      trace "calling helm install weblogic-domain --name ${DOM_KEY} -f $inputs --namespace ${NAMESPACE} --set createWebLogicDomain=false"
      helm install weblogic-domain --name ${DOM_KEY} -f $inputs --namespace ${NAMESPACE} --set createWebLogicDomain=false 2>&1 | opt_tee ${outfile}
      trace "helm install output:"
      cat $outfile
    else   
      kubectl create -f ${TMP_DIR}/domain-custom-resource.yaml
    fi

    verify_domain_created $DOM_KEY 
}

function test_shutdown_domain {
    declare_new_test 1 "$@"

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    shutdown_domain $DOM_KEY

    declare_test_pass
}

function test_domain_lifecycle {
    declare_new_test 1 "$@"

    if [ "$#" != 1 -a "$#" != 2 ] ; then
      fail "requires 1 or 2 parameters: domainKey [verifyDomainKey]"
    fi 

    local DOM_KEY="$1"
    local VERIFY_DOM_KEY="$2"

    shutdown_domain $DOM_KEY
    startup_domain $DOM_KEY 
    verify_domain_exists_via_oper_rest $DOM_KEY 
    verify_domain $DOM_KEY

    # verify that scaling $DOM_KEY had no effect on another domain
    if [ ! "$VERIFY_DOM_KEY" = "" ]; then
      verify_domain $VERIFY_DOM_KEY 
    fi

    declare_test_pass
}

function wait_for_operator_helm_chart_deleted {
  release=$1
  deleted=false
  iter=1
  trace "waiting for the operator helm release ${release} to no longer exist"
  while [ ${deleted} == false -a $iter -lt 101 ]; do
    helm status ${release}
    if [ $? != 0 ]; then
      deleted=true
    else
      iter=`expr $iter + 1`
      sleep 5
    fi
  done
  if [ ${deleted} == false ]; then
    fail 'the operator helm release ${release} failed to be deleted'
  else
    trace "the operator helm release ${release} has been deleted"
  fi
}

function wait_for_operator_deployment_deleted {
  namespace=$1
  deployment="weblogic-operator"
  deleted=false
  iter=1
  trace "waiting for the operator deployment ${deployment} in the namespace ${namespace} to longer exist"
  while [ ${deleted} == false -a $iter -lt 101 ]; do
    kubectl get deployment -namespace ${namespace} ${deployment}
    if [ $? != 0 ]; then
      deleted=true
    else
      iter=`expr $iter + 1`
      sleep 5
    fi
  done
  if [ ${deleted} == false ]; then
    fail 'the operator deployment ${deployment} in the namespace ${namespace} failed to be deleted'
  else
    trace "the operator deployment ${deployment} in the namespace ${namespace} has been deleted"
  fi
}

function shutdown_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    helm delete $OP_KEY --purge
    wait_for_operator_helm_chart_deleted $OP_KEY
    wait_for_operator_deployment_deleted $OPERATOR_NS

    trace "Checking REST service is deleted"
    set +x
    local servicenum=`kubectl get services -n $OPERATOR_NS | egrep weblogic-operator-svc | wc -l`
    set -x
    trace "servicenum=$servicenum"
    if [ "$servicenum" != "0" ]; then
        fail 'operator fail to be deleted'
    fi
}

function startup_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local inputs="$TMP_DIR/weblogic-operator-values.yaml"
    local outfile="$TMP_DIR/startup-weblogic-operator.out"
    helm install weblogic-operator --name ${OP_KEY} --namespace ${OPERATOR_NS} -f $inputs 2>&1 | opt_tee ${outfile}
    trace "helm install output:"
    cat $outfile

    operator_ready_wait $OP_KEY

    local namespace=$OPERATOR_NS

    trace "Checking the operator pods"
    local REPLICA_SET=`kubectl describe deploy weblogic-operator -n ${namespace} | grep NewReplicaSet: | awk ' { print $2; }'`
    local POD_TEMPLATE=`kubectl describe rs ${REPLICA_SET} -n ${namespace} | grep ^Name: | awk ' { print $2; } '`
    local PODS=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | wc | awk ' { print $1; } '`
    local POD=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | awk ' { print $1; } '`

    if [ "$PODS" != "1" ]; then
        fail "There should be one operator pod running"
    fi

    trace Checking the operator Pod status
    local POD_STATUS=`kubectl describe pod $POD -n ${namespace} | grep "^Status:" | awk ' { print $2; } '`
    if [ "$POD_STATUS" != "Running" ]; then
        fail "The operator pod status should be Running"
    fi

    trace "Checking REST service is running"
    set +x
    local REST_SERVICE=`kubectl get services -n ${namespace} -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-svc")]}'`
    set -x
    if [ -z "$REST_SERVICE" ]; then
        fail 'operator rest service was not created'
    fi
}

function verify_no_domain_via_oper_rest {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local OPER_CURL_RESPONSE_BODY="$TMP_DIR/operator.rest.response.body"
    call_operator_rest $OP_KEY "operator/latest/domains"
    # verify that curl returned an empty list of domains, e.g. { ..., "items": [], ... }
    set +x
    local DOMAIN_COUNT=`cat ${OPER_CURL_RESPONSE_BODY} | processJson 'print(len(j["items"]))'`
    set -x
    if [ "${DOMAIN_COUNT}" != "0" ]; then
        fail "expect no domain CR created but return $DOMAIN_COUNT domain(s)"
    fi 
}


function verify_domain_exists_via_oper_rest {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameters: domainKey"
    fi
    local DOM_KEY=${1}
    local OP_KEY="`dom_get $DOM_KEY OP_KEY`"
    local DOMAIN_UID="`dom_get $DOM_KEY DOMAIN_UID`"
    local OPERATOR_TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local OPER_CURL_RESPONSE_BODY="$OPERATOR_TMP_DIR/operator.rest.response.body"
    call_operator_rest $OP_KEY "operator/latest/domains" 
    set +x
    local DOMAIN_COUNT=`cat ${OPER_CURL_RESPONSE_BODY} | processJson 'print(len(j["items"]))'`
    set -x
    if [ "${DOMAIN_COUNT}" != "1" ]; then
        fail "expect one domain CR created but return $DOMAIN_COUNT domain(s)"
    fi

    call_operator_rest $OP_KEY "operator/latest/domains/$DOMAIN_UID"
}

function test_operator_lifecycle {
    declare_new_test 1 "$@"

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameters: domainKey"
    fi

    local DOM_KEY=${1}
    local OP_KEY="`dom_get $DOM_KEY OP_KEY`"

    trace 'begin'
    shutdown_operator $OP_KEY
    startup_operator $OP_KEY
    verify_domain_exists_via_oper_rest $DOM_KEY 
    verify_domain_created $DOM_KEY 
    trace 'end'

    declare_test_pass
}

function test_create_domain_startup_control_admin {
    declare_new_test 1 "$@"

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameters: domainKey"
    fi

    local DOM_KEY=${1}
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"

    create_domain_pv_pvc_load_balancer $DOMAIN_UID
    verify_domain_created $DOMAIN_UID

    declare_test_pass
}


function test_create_domain_pv_reclaim_policy_recycle {
    declare_new_test 1 "$@"

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameters: domainKey"
    fi

    local DOM_KEY=${1}
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local NAMESPACE="`dom_get $1 NAMESPACE`"

    create_domain_pv_pvc_load_balancer $DOMAIN_UID
    verify_domain_created $DOMAIN_UID
    shutdown_domain $DOM_KEY

    kubectl delete pvc ${DOMAIN_UID}-weblogic-domain-pvc -n $NAMESPACE

    local count=`kubectl get pv $DOMAIN_UID-weblogic-domain-pv -n $NAMESPACE |grep "^$DOMAIN_UID " | wc -l `
    if [ ${count:=Error} != 0 ] ; then
      fail "ERROR: pv for $DOMAIN_UID still exists after the pvc is deleted, exiting!"
    fi

    declare_test_pass
}

# scale domain $1 up and down, and optionally verify the scaling had no effect on domain $2
function test_cluster_scale {
    declare_new_test 1 "$@"

    if [ "$#" != 1 -a "$#" != 2 ] ; then
      fail "requires 1 or 2 parameters: domainKey [verifyDomainKey]"
    fi 

    local DOM_KEY="$1"
    local VERIFY_DOM_KEY="$2"
    local NAMESPACE="`dom_get $1 NAMESPACE`"

    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local cur_replicas="`get_cluster_replicas $DOM_KEY`"
    [ "$cur_replicas" = "2" ] || fail "Expected domain CRD to specify 2 replicas."

    trace "test cluster scale-up from 2 to 3"
    local domainCR="$TMP_DIR/domain-custom-resource.yaml"
    if [ "$USE_HELM" = "true" ]; then
      kubectl get domain $DOM_KEY -n $NAMESPACE -o yaml > $domainCR
    fi
    sed -i -e "0,/replicas:/s/replicas:.*/replicas: 3/"  $domainCR
    kubectl apply -f $domainCR

    verify_service_and_pod_created $DOM_KEY 3
    verify_webapp_load_balancing $DOM_KEY 3

    trace "test cluster scale-down from 3 to 2"
    if [ "$USE_HELM" = "true" ]; then
      kubectl get domain $DOM_KEY -n $NAMESPACE -o yaml > $domainCR
    fi
    sed -i -e "0,/replicas:/s/replicas:.*/replicas: 2/"  $domainCR
    kubectl apply -f $domainCR

    verify_pod_deleted $DOM_KEY 3
    verify_webapp_load_balancing $DOM_KEY 2 

    # verify that scaling $DOM_KEY had no effect on another domain
    if [ ! "$VERIFY_DOM_KEY" = "" ]; then
      verify_domain $VERIFY_DOM_KEY 
    fi

    declare_test_pass
}

function test_elk_integration {
    # TODO Placeholder
    declare_new_test 1 "$@"
    trace 'verify elk integration TBD (nyi)'
    declare_test_pass
}

# define globals, re-install docker & k8s if needed, pull images if needed, etc.
function test_suite_init {
    # we defer declaring this a test until the env vars are all setup
    # see 'declare_new_test 1 "$@"' later on

    # The following exports can be customized by the caller of this script.

    local varname
    for varname in RESULT_ROOT \
                   PV_ROOT \
                   LB_TYPE \
                   VERBOSE \
                   DEBUG_OUT \
                   QUICKTEST \
                   NODEPORT_HOST \
                   JVM_ARGS \
                   BRANCH_NAME \
                   IMAGE_TAG_OPERATOR \
                   IMAGE_NAME_OPERATOR \
                   IMAGE_PULL_POLICY_OPERATOR \
                   IMAGE_PULL_SECRET_OPERATOR \
                   WEBLOGIC_IMAGE_PULL_SECRET_NAME \
                   WERCKER \
                   JENKINS \
                   USE_HELM \
                   LEASE_ID;
    do
      trace "Customizable env var before: $varname=${!varname}"
    done

    export RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
    export PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
    export NODEPORT_HOST=${K8S_NODEPORT_HOST:-`hostname | awk -F. '{print $1}'`}
    export JVM_ARGS="${JVM_ARGS:-'-Dweblogic.StdoutDebugEnabled=false'}"
    export BRANCH_NAME="${BRANCH_NAME:-$WERCKER_GIT_BRANCH}"

    if [ -z "$BRANCH_NAME" ]; then
      export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
      [ ! "$?" = "0" ] && fail "Error: Could not determine branch.  Run script from within a git repo".
    fi

    if [ -z "$LB_TYPE" ]; then
      export LB_TYPE=TRAEFIK
    fi

    export LEASE_ID="${LEASE_ID}"

    if [ -z "$LB_TYPE" ]; then
      export LB_TYPE=TRAEFIK
    fi

    if [ -z "$DEBUG_OUT" ]; then
      export DEBUG_OUT="false"
    fi

    # Test installation using helm charts if helm is available
    #
    if [ -x "$(command -v helm)" ]; then
      trace 'helm is installed, assume user wants to use helm if USE_HELM is not set'
      USE_HELM="${USE_HELM:-true}"
    else
      trace 'helm is not installed and USE_HELM="$USE_HELM", if USE_HELM is true future steps may try install helm'
    fi

    # The following customizable exports are currently only customized by WERCKER
    export IMAGE_TAG_OPERATOR=${IMAGE_TAG_OPERATOR:-`echo "test_${BRANCH_NAME}" | sed "s#/#_#g"`}
    export IMAGE_NAME_OPERATOR=${IMAGE_NAME_OPERATOR:-wlsldi-v2.docker.oraclecorp.com/weblogic-operator}
    export IMAGE_PULL_POLICY_OPERATOR=${IMAGE_PULL_POLICY_OPERATOR:-Never}
    export IMAGE_PULL_SECRET_OPERATOR=${IMAGE_PULL_SECRET_OPERATOR}
    export WEBLOGIC_IMAGE_PULL_SECRET_NAME=${WEBLOGIC_IMAGE_PULL_SECRET_NAME}
    export LOGLEVEL_OPERATOR=${LOGLEVEL_OPERATOR:-INFO}

    # Show custom env vars after defaults were substituted as needed.

    local varname
    for varname in RESULT_ROOT \
                   PV_ROOT \
                   LB_TYPE \
                   VERBOSE \
                   DEBUG_OUT \
                   QUICKTEST \
                   NODEPORT_HOST \
                   JVM_ARGS \
                   BRANCH_NAME \
                   IMAGE_TAG_OPERATOR \
                   IMAGE_NAME_OPERATOR \
                   IMAGE_PULL_POLICY_OPERATOR \
                   IMAGE_PULL_SECRET_OPERATOR \
                   WEBLOGIC_IMAGE_PULL_SECRET_NAME \
                   WERCKER \
                   JENKINS \
                   USE_HELM \
                   LEASE_ID;
    do
      trace "Customizable env var after: $varname=${!varname}"
    done

    # Derived exports

    export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
    export CUSTOM_YAML="$SCRIPTPATH/../kubernetes"
    export PROJECT_ROOT="$SCRIPTPATH/../../.."
    export RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
    export USER_PROJECTS_DIR="$RESULT_DIR/user-projects"

    local varname
    for varname in SCRIPTPATH CUSTOM_YAML PROJECT_ROOT; do
      trace "Derived env var: $varname=${!varname}"
    done

    # Save some exports to files in case the env var is lost when calling 'dump'.  This can happen with a ^C trap

    echo "${RESULT_ROOT}" > /tmp/test_suite.result_root
    echo "${PV_ROOT}" > /tmp/test_suite.pv_root
    echo "${PROJECT_ROOT}" > /tmp/test_suite.project_root
    echo "${LEASE_ID}" > /tmp/test_suite.lease_id
    echo "${DEBUG_OUT}" > /tmp/test_suite.debug_out

    # Declare we're in a test.  We did not declare at the start
    # of the function to ensure that any env vars that might
    # be needed by declare_new_test are setup.
    declare_new_test 1 "$@"

    cd $PROJECT_ROOT || fail "Could not cd to $PROJECT_ROOT"
   
    if [ "$WERCKER" = "true" ]; then 
      trace "Test Suite is running locally on Wercker and k8s is running on remote nodes."

      # No need to check M2_HOME or docker_pass -- not used by local runs

      # Assume store/oracle/weblogic:12.2.1.3 and store/oracle/serverjre:8 are already
      # available in the k8s cluster's docker cluster.

      mkdir -p $RESULT_ROOT/acceptance_test_tmp || fail "Could not mkdir -p RESULT_ROOT/acceptance_test_tmp (RESULT_ROOT=$RESULT_ROOT)"

      create_image_pull_secret_wercker

      setup_wercker
      
    elif [ "$JENKINS" = "true" ]; then
    
      trace "Test Suite is running on Jenkins and k8s is running locally on the same node."

      # External customizable env vars unique to Jenkins:

      export docker_pass=${docker_pass:?}
      export M2_HOME=${M2_HOME:?}
      export K8S_VERSION=${K8S_VERSION}

      clean_jenkins

      setup_jenkins

      create_image_pull_secret_jenkins

      /usr/local/packages/aime/ias/run_as_root "mkdir -p $PV_ROOT"
      /usr/local/packages/aime/ias/run_as_root "mkdir -p $RESULT_ROOT"

      # 777 is needed because this script, k8s pods, and/or jobs may need access.

      /usr/local/packages/aime/ias/run_as_root "mkdir -p $RESULT_ROOT/acceptance_test_tmp"
      /usr/local/packages/aime/ias/run_as_root "chmod 777 $RESULT_ROOT/acceptance_test_tmp"

      /usr/local/packages/aime/ias/run_as_root "mkdir -p $RESULT_ROOT/acceptance_test_tmp_archive"
      /usr/local/packages/aime/ias/run_as_root "chmod 777 $RESULT_ROOT/acceptance_test_tmp_archive"

      /usr/local/packages/aime/ias/run_as_root "mkdir -p $PV_ROOT/acceptance_test_pv"
      /usr/local/packages/aime/ias/run_as_root "chmod 777 $PV_ROOT/acceptance_test_pv"

      /usr/local/packages/aime/ias/run_as_root "mkdir -p $PV_ROOT/acceptance_test_pv_archive"
      /usr/local/packages/aime/ias/run_as_root "chmod 777 $PV_ROOT/acceptance_test_pv_archive"

    else
    
      trace "Test Suite is running locally and k8s is running locally on the same node."

      setup_local

      mkdir -p $PV_ROOT || fail "Could not mkdir -p PV_ROOT (PV_ROOT=$PV_ROOT)"

      # The job.sh and wl pods run as UID 1000, so PV_ROOT needs to allow this UID.
      [ `stat -c "%a" $PV_ROOT` -eq 777 ] || chmod 777 $PV_ROOT || fail "Could not chmod 777 PV_ROOT (PV_ROOT=$PV_ROOT)"

      mkdir -p $RESULT_ROOT/acceptance_test_tmp || fail "Could not mkdir -p RESULT_ROOT/acceptance_test_tmp (RESULT_ROOT=$RESULT_ROOT)"

    fi

    local duration=$SECONDS
    trace "Setting up env spent $(($duration / 60)) minutes and $(($duration % 60)) seconds."

    declare_test_pass
}

#
# TODO:  Make output less verbose -- suppress REST, archive, and job output, etc.  In general, move
#        move verbose output to file and/or  only report output on a failure and/or prefix output
#        with a "+".   Also, suppress output in the pod readiness loops to only once every 30 seconds.
#

function test_suite {
    # SECONDS is a special bash reserved variable that auto-increments every second, used by trace
    SECONDS=0

    # we must call declare_reset once before using other declare_ verbs or calling trace
    declare_reset

    trace "******************************************************************************************"
    trace "***                                                                                    ***"
    trace "***    This is the Oracle WebLogic Server Kubernetes Operator Acceptance Test Suite    ***"
    trace "***                                                                                    ***"
    trace "******************************************************************************************"

    set -x

    # define globals, re-install docker & k8s if needed, pull images if needed, etc.

    test_suite_init

    # specify settings for each operator and domain 
    
    declare_new_test 1 define_operators_and_domains

    #          OP_KEY  NAMESPACE            TARGET_NAMESPACES  EXTERNAL_REST_HTTPSPORT
    op_define  oper1   weblogic-operator-1  "default,test1"    31001
    op_define  oper2   weblogic-operator-2  test2              32001

    #          DOM_KEY  OP_KEY  NAMESPACE DOMAIN_UID STARTUP_CONTROL WL_CLUSTER_NAME WL_CLUSTER_TYPE  MS_BASE_NAME   ADMIN_PORT ADMIN_WLST_PORT ADMIN_NODE_PORT MS_PORT LOAD_BALANCER_WEB_PORT LOAD_BALANCER_DASHBOARD_PORT
    dom_define domain1  oper1   default   domain1    AUTO            cluster-1       DYNAMIC          managed-server 7001       30012           30701           8001    30305                  30315

    # TODO: we need to figure out how to support invalid characters in the helm use cases
    # for now, invalid characters are only tested in the none helm cases
    if [ "$USE_HELM" = "true" ]; then
      dom_define domain2  oper1   default   domain2    AUTO            cluster-1       DYNAMIC          managed-server 7011       30031           30702           8021    30306                  30316
      dom_define domain3  oper1   test1     domain3    AUTO            cluster-1       DYNAMIC          managed-server 7021       30041           30703           8031    30307                  30317
    else
      dom_define domain2  oper1   default   domain2    AUTO            cluster-1       DYNAMIC          Managed_Server 7011       30031           30702           8021    30306                  30316
      dom_define domain3  oper1   test1     domain3    AUTO            cluster_1       DYNAMIC          managed-Server 7021       30041           30703           8031    30307                  30317
    fi
    dom_define domain4  oper2   test2     domain4    AUTO            cluster-1       CONFIGURED       managed-server 7041       30051           30704           8041    30308                  30318
    dom_define domain5  oper1   default   domain5    ADMIN           cluster-1       DYNAMIC          managed-server 7051       30061           30705           8051    30309                  30319
    dom_define domain6  oper1   default   domain6    AUTO            cluster-1       DYNAMIC          managed-server 7061       30071           30706           8061    30310                  30320

    # create namespaces for domains (the operator job creates a namespace if needed)
    # TODO have the op_define commands themselves create target namespace if it doesn't already exist, or test if the namespace creation is needed in the first place, and if so, ask MikeG to create them as part of domain create job
    kubectl create namespace test1 2>&1 | sed 's/^/+/g' 
    kubectl create namespace test2 2>&1 | sed 's/^/+/g' 

    kubectl create namespace weblogic-operator-1 2>&1 | sed 's/^/+/g' 
    kubectl create namespace weblogic-operator-2 2>&1 | sed 's/^/+/g' 
    kubectl create serviceaccount --namespace weblogic-operator-1 weblogic-operator | sed 's/^/+/g' 
    kubectl create serviceaccount --namespace weblogic-operator-2 weblogic-operator | sed 's/^/+/g' 

    # This test pass pairs with 'declare_new_test 1 define_operators_and_domains' above
    declare_test_pass
   
    trace 'Running mvn integration tests...'
    if [ "$WERCKER" = "true" ]; then
      test_mvn_integration_wercker
    elif [ "$JENKINS" = "true" ]; then
      test_mvn_integration_jenkins
    else
      test_mvn_integration_local
    fi


    # create and start first operator, manages namespaces default & test1
    test_first_operator oper1

    # test elk intgration
    test_elk_integration

    # create first domain in default namespace and verify it
    test_domain_creation domain1 

    # test shutting down and restarting a domain 
    test_domain_lifecycle domain1 

    # test shutting down and restarting the operator for the given domain
    test_operator_lifecycle domain1

    # test scaling domain1 cluster from 2 to 3 servers and back down to 2
    test_cluster_scale domain1 

    # if QUICKTEST is true skip the rest of the tests
    if [ ! "${QUICKTEST:-false}" = "true" ]; then
   
      # create another domain in the default namespace and verify it
      test_domain_creation domain2 
    
      # shutdown domain2 before create domain3
      test_shutdown_domain domain2
    
      # create another domain in the test namespace and verify it
      test_domain_creation domain3 
    
      # shutdown domain3
      test_shutdown_domain domain3
    
      # Create another operator in weblogic-operator-2 namespace, managing namespace test2
      # Verify the only remaining running domain, domain1, is unaffected
      test_second_operator oper2 domain1 
    
      # create another domain in the test2 namespace and verify it
      test_domain_creation domain4 

      # test scaling domain4 cluster from 2 to 3 servers and back to 2, plus verify no impact on domain1
      test_cluster_scale domain4 domain1 
 
      # cycle domain1 down and back up, plus verify no impact on domain4
      test_domain_lifecycle domain1 domain4 

      # create domain5 in the default namespace with startupControl="ADMIN", and verify that only admin server is created
      # on Jenkins, this domain will also test NFS instead of HOSTPATH PV storage (search for [ "$DOMAIN_UID" == "domain5" ])
      test_create_domain_startup_control_admin domain5

      # create domain6 in the default namespace with pvReclaimPolicy="Recycle", and verify that the PV is deleted once the domain and PVC are deleted
      test_create_domain_pv_reclaim_policy_recycle domain6

      # test managed server 1 pod auto-restart
      test_wls_liveness_probe domain1
    
      # shutdown domain1
      test_shutdown_domain domain1

    fi 

    local duration=$SECONDS
    trace "Running integration tests spent $(($duration / 60)) minutes and $(($duration % 60)) seconds."

    # declare success of entire test_suite
    declare_new_test 1 complete
    declare_test_pass
    
    # dump current state and archive into a tar file, also called from 'fail'
    state_dump logs

    set +x
    trace "******************************************************************************************"
    trace "***                                                                                    ***"
    trace "***    Oracle WebLogic Server Kubernetes Operator Acceptance Test Suite Completed !!   ***"
    trace "***                                                                                    ***"
    trace "******************************************************************************************"
} 


# entry point

# "set -o pipefail" ensures that "$?" reflects the first failure in a pipe
# instead of the status of the last command in the pipe.
# For example, this script:
#   ls missing-file | tee /tmp/ls.out
#   echo $?
# Will echo "0" by default, and echo "2" with "set -o pipefile" 

set -o pipefail

if [ "$WERCKER" = "true" -o "$JENKINS" = "true" ]; then
  if [ "${VERBOSE:-false}" = "true" ]; then
    test_suite 2>&1 
    exit_status="$?"
  else
    test_suite 2>&1 | grep -v "^+"
    exit_status="${PIPESTATUS[0]}"
  fi
else
  export TESTOUT=/tmp/test_suite.out
  trace See $TESTOUT for full trace.

  if [ "${VERBOSE:-false}" = "true" ]; then
    test_suite 2>&1 | tee /tmp/test_suite.out 
    exit_status="${PIPESTATUS[0]}"
  else
    test_suite 2>&1 | tee /tmp/test_suite.out | grep -v "^+"
    exit_status="${PIPESTATUS[0]}"
  fi
  trace See $TESTOUT for full trace.
fi


exit $exit_status

