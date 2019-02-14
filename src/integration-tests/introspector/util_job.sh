#!/bin/bash

# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This script:
#        0) deletes a job's job/pods if they already exist
#        1) launches a job via a yaml file that's passed to the script
#        2) waits for job/pod to finish
#        3) dumps its pod's log
#        4) exits status 0 if the job succeeded, !0 otherwise
#
# It expects these env vars:
#        NAMESPACE  - 'default', etc
#        JOB_YAML   - Job Yaml File, Must have backoffLimit=1
#        JOB_NAME   - Name in Job Yaml
#
# IMPORTANT:  This script assumes the job will only make one
#             attempt (backoffLimit set to 0 in the job yaml).
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SOURCEPATH="`echo $SCRIPTPATH | sed 's/weblogic-kubernetes-operator.*/weblogic-kubernetes-operator/'`"
traceFile=${SOURCEPATH}/operator/src/main/resources/scripts/traceUtils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

trace "Begin kubernetes job"

checkEnv NAMESPACE JOB_YAML JOB_NAME

[ ! -f "$JOB_YAML" ] && trace "Error:  JOB_YAML not found." && exit 1

# Handle '--show-all' deprecation for 'kubectl get jobs' and 'kubectl get pods'.
# --show-all yields a deprecation warning in stderr in 1.10 in later, since
# it isn't needed in 1.10 and later. 
k8s_minor=`kubectl version | grep Client | sed 's/.*Minor:"\([0-9]*\)".*/\1/'`
k8s_major=`kubectl version | grep Client | sed 's/.*Major:"\([0-9]*\)".*/\1/'`
show_all="--show-all"
if [ $k8s_major -gt 1 ] || [ $k8s_minor -gt 9 ]; then
  show_all=""
fi

function getJobs() {
  # fn to get jobs for NAMESPACE/JOB_NAME

  cmd="kubectl -n $NAMESPACE get jobs $show_all --output=jsonpath='{.items[?(@.metadata.name == \"$JOB_NAME\")]..metadata.name}'"
  eval $cmd || exit 1
}

function getJobSucceededCount() {
  # fn to get job succeeded count for NAMESPACE/JOB_NAME
  # * job succeeded/failed counts go up from "" to "something" (should be "1")

  cmd="kubectl -n $NAMESPACE get jobs $show_all --output=jsonpath='{.items[?(@.metadata.name == \"$JOB_NAME\")]..status.succeeded}'"
  eval $cmd || exit 1
}

function getJobFailedCount() {
  # fn to get job failed count for NAMESPACE/JOB_NAME
  # * job succeeded/failed counts go up from "" to "something" (should be "1" if backoffLimit=0)

  cmd="kubectl -n $NAMESPACE get jobs $show_all --output=jsonpath='{.items[?(@.metadata.name == \"$JOB_NAME\")]..status.failed}'"
  eval $cmd || exit 1
}

function getPodNames() {
  # fn to get pod name(s) for NAMESPACE/JOB_NAME
  # * pod name(s) should be  "" or a single name if backoffLimit=0

  cmd="kubectl -n $NAMESPACE get pods $show_all --output=jsonpath='{.items[?(@.metadata.labels.job-name == \"$JOB_NAME\")]..metadata.name}'"
  eval $cmd || exit 1
}

function getPodStatus() {
  # fn to get pod status for NAMESPACE/JOB_NAME
  # * can be "Succeeded", "Failed", "", or even something else like "Pending".
  # * This script assumes "Succeeded" and "Failed" are the most useful terminal statuses.
  # * This function can return multiple statuses if the job hasn't 
  #   been carefully constrained to only try once (backoffLimit: 0)

  cmd="kubectl -n $NAMESPACE get pods $show_all --output=jsonpath='{.items[?(@.metadata.labels.job-name == \"$JOB_NAME\")]..status.phase}'"
  eval $cmd || exit 1
}

function getPodCount() {
  # fn to get pod count for NAMESPACE/JOB_NAME
  # * Should be 0 or 1 if backoffLimit has been set to 0

  getPodNames | wc -w |  sed 's/[[:space:]]*//'
}

function deleteJob() {
  # fn to delete job NAMESPACE/JOB_NAME and wait for it and its pods to go away

  trace "Calling 'kubectl -n $NAMESPACE delete job $JOB_NAME --ignore-not-found=true'"
  kubectl -n $NAMESPACE delete job $JOB_NAME --ignore-not-found=true
  local maxwait=15
  local startsecs=$SECONDS
  while : ; do 
    if [ "`getPodNames``getJobs`" = "" ]; then
      return 0;
    fi
    if [ $((SECONDS - startsecs)) -gt $maxwait ]; then
      trace "Timed out waiting for $JOB_NAME to delete."
      exit 1
    fi
  done
}

function launchCommandJob {
  # fn to launch job JOB_YAML, then wait for job & pod for NAMESPACE/JOB_NAME to report success or failure, then dump the log of the pod

  deleteJob

  trace "Calling 'kubectl -n $NAMESPACE create -f $JOB_YAML'"
  kubectl -n $NAMESPACE create -f $JOB_YAML

  trace "Waiting for the job to complete.  Will dump its log once finished."

  local job_success_count=""
  local job_fail_count=""
  local pod_status=""
  local pod_count=""
  local pod_names=""
  local maxwaitsecs=120
  local mstart=$SECONDS
  local return_status="1"
  while : ; do
    local mnow=$SECONDS

    # We monitor both pod & job status because one or the other
    # may return a useful status far earlier.

    # pod_status can be "", "Succeeded", or "Failed"
    pod_status="`getPodStatus`"

    # pod_count should always be 1 since backoffLimit is 1
    pod_count="`getPodCount`"

    # pod_names should only have one name since backoffLimit is 1
    pod_names="`getPodNames`"

    # succeeded count can be "", or some number (should be 1 at most!)
    job_succeeded_count="`getJobSucceededCount`"

    # failed count can be "", or some number (should be 1 at most given job's backoffLimit=0!)
    job_failed_count="`getJobFailedCount`"

    if [ ! "$pod_count" = "1" ] && [ ! "$pod_count" = "0" ] ; then
      trace "Error:  Unexpected pod count='$pod_count'  Expected it to be 0 or 1 (no more than 1 try since job's backoffLimit=1)."
      return_status="1"
      break
    fi

    if [ "$pod_status" = "Succeeded" ]; then
      return_status="0"
      break
    fi

    if [ "$pod_status" = "Failed" ]; then
      trace "Error:  Pod status is 'Failed'"
      return_status="1"
      break
    fi

    if [ ! "$job_succeeded_count" = "" ]; then
      return_status="0"
      break
    fi

    if [ ! "$job_failed_count" = "" ]; then
      trace "Error:  Job failed count is '$job_failed_count'"
      return_status="1"
      break
    fi

    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      trace "Job failed to complete before $maxwaitsecs."
      return_status="1"
      break
    fi
  done

  local pods=$(kubectl -n $NAMESPACE get pods $show_all --selector=job-name=$JOB_NAME --output=jsonpath={.items..metadata.name})
  local pod
  for pod in $pods; do
    # trace 
    # trace "Calling 'kubectl -n $NAMESPACE get pod $pod'"
    kubectl -n $NAMESPACE get pod $pod 2>&1
    # trace 
    # trace "Calling 'kubectl -n $NAMESPACE logs pod $pod'"
    kubectl -n $NAMESPACE logs $pod 2>&1
    # trace 
  done

  trace "Info: job='$JOB_NAME' pod='$pod_names' return_status='$return_status' pod_status='$pod_status' job_failed_count='$job_failed_count' job_succeeded_count='$job_succeeded_count'."
  trace "Exiting with status $return_status"

  return $return_status
}

launchCommandJob 
exit $?
