#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Usage:  See 'function usage' below, or call 'lease.sh -h'.
# Main:   See 'function main' below.
#

function usage {
cat << EOF
 
  Summary:
 
    Use this helper script to obtain an exclusive 10 minute lease
    on a kubernetes cluster, renew this lease, release this lease,
    force the delete of the lease, or view the current lease.
    It exits with a non-zero value on a failure.

  Usage:
 
    Obtain       : lease.sh -o id [-t timeout]
    Renew        : lease.sh -r id  
    Delete       : lease.sh -d id 
    Force Delete : lease.sh -f 
    Show         : lease.sh -s
    Help         : lease.sh -h

    The user is expected to call this script multiple times throughout
    the ownership of a lease:
 
    1) First, call 'lease.sh -o id' to try obtain a lease.  If 
       this fails, fail your script, since failure may mean some other
       owner has ownership.  This call will block up to 30 minutes
       while trying to get the lease (tunable via -t), and will grab
       the lease if no other process owns it or no other process has touched
       it for a period of 10 minutes.  'id' should be some unique
       string suitable for use as a filename - a uuid is preferred but a 
       pid is usually sufficient.
 
    2) While running, call 'lease.sh -r id' every few minutes
       to renew the lease and retain ownership.   Fail if this fails,
       since that means you may have lost the lease.  Be careful to call
       this within 10 minutes of last obtaining or renewing the lease, 
       otherwise the lease expires and another process is free to obtain
       ownership.  Use the same value of 'id' as was specified in step (1).
  
    3) Finally, call 'lease.sh -d id' to delete and give up your
       lease.  This will fail if you are not the current owner.  Use the same
       value of 'id' as was specified in step (1).  If you forget to 
       call it, no one else will be able to get the lease for up to 10
       minutes.
 
    To show who currently owns a lease, call 'lease.sh -s' or call
    'kubectl get cm acceptance-test-lease -o yaml -n default'.
   
    To force delete a lease no matter who owns the lease, call
    'lease.sh -f' or 'kubectl delete cm acceptance-test-lease -n default'.  
    This should only be done if you're sure there's
    no current processe that owns the lease.

  Warnings:
 
    This is a 'best effort' script -- it still has potential race
    conditions.  They should be pretty rare, but don't bet the farm on 
    them not happening!

    This script requires cooperation among all users of the k8s cluster that
    need exclusive access - they all must use the script.  The script
    doesn't directly grant exclusive access, it just can be used to make sure
    that no other callers of the same script have the lease.

 
  Internals:
 
    This script maintains a local file '[tmp_root]/acceptance-test-lease'
    which is a potential copy of the remote lease. tmp_root is /tmp/lease
    by default.  You can optionally specify a different tmp_root
    using the hidden '-l' option described above, but always specify the
    same tmp_root across all steps.
 
    This script also maintains a lease record in the remote kubernetes
    cluster in a config map named 'acceptance-test-lease' that's
    located in the default namespace.

    The lease record contains multiple fields to help identify
    the last user of the lease.  It also contains a 'timestamp'
    field which equals 0 when the lease is unowned, and which
    is set to the last time the lease was updated when the lease
    is owned (in seconds since the epoch - e.g. 'date +%s').
EOF
}


function main {
  # 
  # The main for this script.  Arguments come from the command line.
  #

  # Setup globals

  # Time in seconds after which we consider a lease abandoned:
  ABANDON_TIMEOUT_SECONDS=$((10 * 60))

  # Max time in seconds to wait when attempting to obtain a lease for the first time:
  OBTAIN_LEASE_TIMEOUT_SECONDS=$((30 * 60))

  # Interval to sleep between various checks:
  SLEEP_SECONDS=$((30))

  # Dir location of potential local copy of remote lease (see hidden '-l' option):
  LOCAL_ROOT=${LOCAL_ROOT:-/tmp}

  # File name of potential copy of remote lease.  
  # WARNING:  Do not include a '.' in the file name, this messes up jsonpath parsing.
  LOCAL_FILE=acceptance-test-lease-props

  # K8S CM that contains the remote lease
  CONFIGMAP_NAME="acceptance-test-lease"

  # Location of this script
  SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

  # Save command line for error messages...
  COMMAND_LINE="$0 $*"

  # Local ID that owns the K8S lease.  This is passed in on the command line.
  LOCAL_ID=""

  local mode=""

  # Parse command line options

  while [ ! "$1" = "" ]; do
    case "$1" in
    -h) if [ "$mode" = "" ]; then
          mode="usage"
        else
          mode="commandLineError"
        fi
        ;;
    -o) if [ "$mode" = "" ] && [ ! "$2" = "" ]; then
          mode="obtainLease"
          LOCAL_ID="${2}"
          shift
        else
          mode="commandLineError"
        fi
        ;;
    -r) if [ "$mode" = "" ] && [ ! "$2" = "" ]; then
          mode="renewLease"
          LOCAL_ID="${2}"
          shift
        else
          mode="commandLineError"
        fi
        ;;
    -d) if [ "$mode" = "" ] && [ ! "$2" = "" ]; then
          mode="deleteRemoteLeaseSafe"
          LOCAL_ID="${2}"
          shift
        else
          mode="commandLineError"
        fi
        ;;
    -f) if [ "$mode" = "" ]; then
          mode="deleteRemoteLeaseUnsafe"
        else
          mode="commandLineError"
        fi
        ;;
    -s) if [ "$mode" = "" ]; then
          mode="showRemoteLease"
        else
          mode="commandLineError"
        fi
        ;;
    -t) if [ ! "$2" = "" ]; then
          OBTAIN_LEASE_TIMEOUT_SECONDS="${2}"
          shift
        else
          mode="commandLineError"
        fi
        ;;
    -l) if [ ! "$2" = "" ]; then
          LOCAL_ROOT="${2}"
          shift
        else
          mode="commandLineError"
        fi
        ;;
    *)  mode="commandLineError"
        ;;
    esac
    shift
  done

  LOCAL_ROOT="${LOCAL_ROOT}/lease/id-${LOCAL_ID}"

  # Execute command line

  eval "$mode"
  return $?
}

function traceInfo {
  echo "@@ [script=$0] [fn=${FUNCNAME[1]}]"" Info: ""$@"
}

function traceError {
  echo "@@ [script=$0] [fn=${FUNCNAME[1]}]"" Error: ""$@"
}

function commandLineError {
  traceError "Incorrect command line syntax '$COMMAND_LINE'.  Use '-h' to get usage."
  return 1
}

function getLocalLease {
  #
  # cat the local candidate lease file in $LOCAL_FILE
  # if local lease DNE, just make a dummy file and cat that instead
  # returns non-zero if fails
  #
  mkdir -p $LOCAL_ROOT
  if [ $? -ne 0 ]; then
    traceError "Could not create directory $LOCAL_ROOT."
    return 1
  fi
  if [ ! -f ${LOCAL_ROOT}/${LOCAL_FILE} ]; then
    makeLocalLease
    if [ $? -ne 0 ]; then
      traceError "No local lease file and could not create a dummy lease"
      return 1
    fi
  fi
  cat ${LOCAL_ROOT}/${LOCAL_FILE}
  if [ $? -ne 0 ]; then
    traceError "Could not view $LOCAl_ROOT/$LOCAL_FILE."
    return 1
  fi
}

function makeLocalLease {
#
# Make a candidate lease file in ${LOCAL_ROOT}/[expired/]${LOCAL_FILE} 
# Embed id, hostname, timestamp, etc to make it easy to identify the owner.
# returns non-zero if fails
# Usage: Pass "expired" as $1 to create a lease that immediately expires
#        (its timestamp value will be 0)  This file will be located
#        in expired/${LOCAL_FILE} instead of just ${LOCAL_FILE}
#
  mkdir -p ${LOCAL_ROOT}
  if [ $? -ne 0 ]; then
    traceError "Could not create directory $LOCAL_ROOT."
    return 1
  fi
  mkdir -p ${LOCAL_ROOT}/expired
  if [ $? -ne 0 ]; then
    traceError "Could not create directory $LOCAL_ROOT."
    return 1
  fi
  if [ "$1" = "expired" ]; then
    cat <<EOF > ${LOCAL_ROOT}/expired/${LOCAL_FILE}
#This file is created by script $0
timestamp=0
date=`date '+%m-%d-%YT%H:%M:%S'`
host=$HOST
id=$LOCAL_ID
user=$USER
EOF
  else
    cat <<EOF > ${LOCAL_ROOT}/${LOCAL_FILE}
#This file is created by script $0
timestamp=`date +%s`
date=`date '+%m-%d-%YT%H:%M:%S'`
host=$HOST
id=$LOCAL_ID
user=$USER
EOF
  fi

  if [ $? -ne 0 ]; then
    traceError "Could not create candidate lease file."
    return 1
  fi
}

function makeLocalLeaseAndReplaceRemote {
  # Replace the remote lease with a new lease that we own
  # It's assumed that it's already determined it's safe to try and get the lease
  # (either the lease is unowned, expired, or owned by us).
  #
  # TBD: There's a small race where this call temporarily deletes the lease before
  # it replaces it with a new one,
  # which means someone else could come in and snipe it even if we already
  # own an older version of the lease and the older version hasn't expired.
  # If this happens, this call will fail when it tries to 'checkLease'
  # and the caller therefore is forced to give up their lease.   In theory,
  # this race could be resolved by using a 'replace -f' pattern - but this
  # failed with unexpected errors on some kubectl setups but not others.
  #

  makeLocalLease
  if [ $? -ne 0 ]; then
    traceError "failed - could not generate a new local lease"
    return 1
  fi

  deleteRemoteLeaseUnsafe
  if [ $? -ne 0 ]; then
    traceError "failed - could not delete remote lease" 
    return 1
  fi

  kubectl create configmap ${CONFIGMAP_NAME} --from-file ${LOCAL_ROOT}/${LOCAL_FILE} -n default
  if [ $? -ne 0 ]; then
    traceError "failed - could not replace" 
    return 1
  fi

  # finally, check if we now actually own the lease (someone could have been replacing at the same time)
  checkLease 
  if [ $? -ne 0 ]; then
    traceError "failed - replaced remote lease, but we somehow lost a race or can no longer communicate with kubernetes"
    return 1
  fi
  return 0
}

function getRemoteLease {
  #
  #  first, if the remote lease configmap doesn't exist
  #         then try create one with a lease that's already expired 
  #  second, try show the lease in stdout
  #  return non-zero if this fails
  #
  kubectl get configmaps -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' -n default > /tmp/getRemoteLease.tmp.$$
  if [ $? -ne 0 ]; then
    traceError "kubectl command unexpectedly failed"
    rm -f /tmp/getRemoteLease.tmp.$$
    return 1
  fi
  local val=`grep -c "^${CONFIGMAP_NAME}\$" /tmp/getRemoteLease.tmp.$$`
  rm -f /tmp/getRemoteLease.tmp.$$
  if [ "$val" = "0" ]; then
    # the config map is missing, try create an expired one
    makeLocalLease expired
    if [ $? -ne 0 ]; then
      traceError "failed"
      return 1
    fi
    kubectl create configmap ${CONFIGMAP_NAME} --from-file ${LOCAL_ROOT}/expired/${LOCAL_FILE} -n default
    if [ $? -ne 0 ]; then
      traceError "remote lease missing, and we failed while trying to create a new one"
      return 1
    fi
  fi
  # echo the remote lease to stdout:
  local command="kubectl get configmap ${CONFIGMAP_NAME} -o=jsonpath=\"{.data['${LOCAL_FILE}']}\" -n default"
  eval "$command"
  if [ $? -ne 0 ]; then
    traceError "failed while trying to get values in remote lease, command=$command"
    return 1
  fi
}

function getLeaseTimestamp {
  # Get the timestamp value from a lease.   $1 must contain the lease
  echo "$1" | grep "timestamp=" | sed "s/.*timestamp=\([0-9]*\).*/\1/g"
}

function showRemoteLease {
  local remote_lease="`getRemoteLease`"
  if [ $? -ne 0 ]; then
    traceError "failed while trying to get values in remote lease"
    return 1
  fi
  local timestamp=`getLeaseTimestamp "$remote_lease"`
  if [ "$timestamp" = "0" ]; then
    traceInfo "Lease is unowned.  Lease contents:"
  else
    local mnow=`date +%s`
    traceInfo "Lease is owned and was last used $((mnow - timestamp)) seconds ago.  Lease contents:"
  fi
  getRemoteLease
  if [ $? -ne 0 ]; then
    traceError "failed while trying to get values in remote lease"
    return 1
  fi
  return 0
}

function checkLease {
  #
  # get current local lease || fail
  # get current remote lease || fail
  # check local == remote || fail
  #
  local local_lease="`getLocalLease`"
  if [ $? -ne 0 ]; then
    traceError "failed - could not get local lease candidate file"
    return 1
  fi
  local remote_lease="`getRemoteLease`"
  if [ $? -ne 0 ]; then
    traceError "failed - could not get remote lease, kubernetes is not responding"
    return 1
  fi
  if [ ! "$local_lease" = "$remote_lease" ]; then
    traceError "failed - local lease and remote lease don't match local_lease=\'$local_lease\' remote_lease=\'$remote_lease\'"
    return 1
  fi
}

function obtainLease {
  #
  # pseudo-code
  #   try look at remote lease, return a failure if cannot
  #   if remote lease older than ABANDON_TIMEOUT_SECONDS, then
  #      try replace remote lease with our own new lease
  #      if we verify that the remote lease now matches our local lease, then
  #        return success
  #      else
  #        retry above up to OBTAIN_LEASE_TIMEOUT_SECONDS
  #
  local mstart=`date +%s`
  while : ; do
    local remote_lease="`getRemoteLease`"
    if [ $? -ne 0 ]; then
      traceError "failed - could not get remote lease"
      return 1
    fi
    local remote_ts=`getLeaseTimestamp "$remote_lease"`
    local mnow=`date +%s`
    if [ $((mnow)) -gt $((remote_ts + ABANDON_TIMEOUT_SECONDS)) ]; then
      # the remote lease hasn't been updated for ABANDON_TIMEOUT_SECONDS
      # so assume it can be replaced and we can try takeover the lease 

      # first make a local candidate lease
      makeLocalLeaseAndReplaceRemote
      if [ $? -eq 0 ]; then
        return 0
      else
        traceError "failed to replace remote lease, will keep retrying up to the timeout"
      fi
    fi
    local mnow=`date +%s`
    if [ $((mnow - mstart)) -gt $((OBTAIN_LEASE_TIMEOUT_SECONDS)) ]; then
      traceError "failed to obtain lease before ${OBTAIN_LEASE_TIMEOUT_SECONDS} seconds."
      return 1
      break
    fi
    traceInfo "Retrying after $((mnow - mstart)) seconds.  About to sleep ${SLEEP_SECONDS} seconds.   Max wait is ${OBTAIN_LEASE_TIMEOUT_SECONDS} seconds.  Current lease owner:"
    showRemoteLease
    sleep $SLEEP_SECONDS
  done
}

function renewLease {
  #
  # this must called every few minutes (less than ABANDON_TIMEOUT_SECONDS)
  # after obtaining a lease in order to keep ownership of a lease
  #
  # it fails if it doesn't look like we currently own the lease, or if
  # the lease can't be renewed for any reason
  #
  # if it fails, the caller should assume it doesn't own the lease anymore
  #

  # first, check if we actually still own the lease (someone could have replaced it or deleted it)
  checkLease 
  if [ $? -ne 0 ]; then
    traceError "failed - we no longer own the lease"
    return 1
  fi

  # now make a new local candidate lease
  makeLocalLeaseAndReplaceRemote
  if [ $? -ne 0 ]; then
    traceError "failed to replace remote lease"
    return 1
  else
    return 0
  fi
}

function deleteRemoteLeaseSafe {
  #
  # delete the remote lease if we determine we're the local owner
  # fail otherwise
  #
  checkLease 
  if [ $? -ne 0 ]; then
    traceError "failed - could not delete lease, we are not the owner or kubectl is not working" 
    return 1
  fi
  kubectl delete cm ${CONFIGMAP_NAME} -n default
  return $?
}

function deleteRemoteLeaseUnsafe {
  #
  # delete the remote lease regardless of whether we're the owner
  #
  kubectl delete cm ${CONFIGMAP_NAME} -n default --ignore-not-found
}

main "$@"
exit $?
