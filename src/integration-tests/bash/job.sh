#!/bin/bash
# Copyright 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# job.sh <command> [<optarg1>] [<optarg2>] ...
#
#    This is a helper script called by cleanup.sh.  It runs the given bash
#    command in a kubernetes job.  It also embeds the 'archive.sh' script
#    internally in its /scripts volume.
#
#    There are two configurable env vars - PV_ROOT and  RESULT_ROOT.  See cleanup.sh for
#    a description.
#
#    The command and each optarg argument must have no internal spaces.
#
#    The job will only make one attempt (backoffLimit is set to 0).
#    The job mounts NFS directory $PV_ROOT to /scratch
#    The job mounts scripts to /scripts (via a configmap volume).
#    The job runs as the default O/S user for WL pods (uid = 1000?).
#

# Constants

JOB_NAME="weblogic-command-job"

# Customizable env vars.  See cleanup.sh for an explanation.

echo "@@ Begin kubernetes job to run a command.  Job name='$JOB_NAME'.  Command='$*'."

echo "@@ At script entry:  RESULT_ROOT='$RESULT_ROOT'"
echo "@@ At script entry:  PV_ROOT='$PV_ROOT'"

RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
PV_ROOT=${PV_ROOT:-$RESULT_ROOT}

echo "@@ After script init:  RESULT_ROOT='$RESULT_ROOT'"
echo "@@ After script init:  PV_ROOT='$PV_ROOT'"

# Derived env vars.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
PROJECT_ROOT="$SCRIPTPATH/../../.."
RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
TMP_DIR="$RESULT_DIR"

# Handle '--show-all' deprecation for 'kubectl get jobs' and 'kubectl get pods'.
# --show-all yields a deprecation warning in stderr in 1.10 in later, since
# it isn't needed in 1.10 and later.

k8s_minor=`kubectl version | grep Client | sed 's/.*Minor:"\([0-9]*\)".*/\1/'`
k8s_major=`kubectl version | grep Client | sed 's/.*Major:"\([0-9]*\)".*/\1/'`
show_all="--show-all"
if [ $k8s_major -gt 1 ] || [ $k8s_minor -gt 9 ]; then
  show_all=""
fi

function launchCommandJob {
  command="$*"

  # setup the yaml

  local template_yaml="$PROJECT_ROOT/src/integration-tests/kubernetes/command-job-template.yaml"
  local target_yaml="$TMP_DIR/command-job.yaml"

  if [ ! -f "$template_yaml" ]; then
    echo "@@ Error could not find $template_yaml."
    return 1
  fi

  cp "$template_yaml" "$target_yaml"
  if [ $? -ne 0 ]; then
    echo "@@ Could not cp $template_yaml to $target_yaml."
    return 1
  fi

  sed -i -e "s|COMMAND|$command|g" $target_yaml
  sed -i -e "s|PV_ROOT|$PV_ROOT|g" $target_yaml

  echo @@ "Calling 'kubectl create -f $target_yaml'"

  # setup configmap for job that includes archive.sh

  local archive_file="$PROJECT_ROOT/src/integration-tests/bash/archive.sh"
  if [ ! -f $archive_file ]; then
    echo "@@ Could not find $archive_file."
  fi

  kubectl delete job $JOB_NAME --ignore-not-found=true
  kubectl delete configmap ${JOB_NAME}-configmap --ignore-not-found=true

  # deploy the job

  kubectl create configmap ${JOB_NAME}-configmap --from-file $archive_file
  kubectl create -f $target_yaml

  echo "@@ Waiting for the job to complete..."

  local job_status="0"
  local maxwaitsecs=120
  local mstart=`date +%s`
  local return_status="1"
  while : ; do
    sleep 3
    local mnow=`date +%s`
    job_status=`kubectl get job $JOB_NAME | grep "$JOB_NAME" | awk '{ print $3; }'`

    # If the job is status 1 or the pod is status Completed, the job succeeded.
    # If the pod is status Error, we know the job failed.

    if [ "$job_status" = "1" ]; then
      echo "@@ Success. Job $JOB_NAME status is 1".
      return_status="0"
      break
    fi

    local pod_status=`kubectl get pods $show_all --selector=job-name=$JOB_NAME | grep "$JOB_NAME" | awk '{ print $3 }'`

    if [ "$pod_status" = "Completed" ]; then
      echo "@@ Success.  job_status=$job_status pod_status=$pod_status"
      return_status="0"
      break
    fi

    if [ "$pod_status" = "Error" -o "$pod_status" = "ErrImagePull" ]; then
      echo "@@ Failure.  job_status=$job_status pod_status=$pod_status"
      return_status="1"
      break
    fi

    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      echo "@@ Job failed to complete before $maxwaitsecs.  job_status=$job_status pod_status=$pod_status"
      return_status="1"
      break
    fi

    echo "@@ Job incomplete after $((mnow - mstart)) seconds (max wait = $maxwaitsecs seconds).  Will retry.  job_status=$job_status pod_status=$pod_status"
    sleep 3
  done

  local pods=$(kubectl get pods $show_all --selector=job-name=$JOB_NAME --output=jsonpath={.items..metadata.name})
  local pod
  for pod in $pods; do
    echo @@
    echo "@@ Calling 'kubectl get pod $pod'"
    kubectl get pod $pod 2>&1
    echo @@
    echo "@@ Calling 'kubectl logs pod $pod'"
    kubectl logs $pod 2>&1
    echo @@
  done

  echo @@ Deleting job and its configmap.

  kubectl delete job $JOB_NAME --ignore-not-found=true
  kubectl delete configmap ${JOB_NAME}-configmap --ignore-not-found=true

  echo @@ Exiting with status $return_status

  return $return_status
}

launchCommandJob "$*"
exit $?
