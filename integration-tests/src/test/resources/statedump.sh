#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

 
# 
# state_dump 
#   - called at the end of a run
#   - places k8s logs, descriptions, etc in directory $RESULT_DIR/state-dump-logs
#   - calls archive.sh on RESULT_DIR locally, and on PV_ROOT via a job
#   - IMPORTANT: this method should not rely on exports 
#
function state_dump {
     local RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
     local PV_ROOT="$PV_ROOT"
     local PROJECT_ROOT="$PROJECT_ROOT"
     local SCRIPTPATH="$PROJECT_ROOT/src/integration-tests/bash"
     local LEASE_ID="$LEASE_ID"
     local ARCHIVE_DIR="$RESULT_ROOT/acceptance_test_pv_archive"
     local ARCHIVE_FILE="IntSuite.`date '+%Y%m%d%H%M%S'`.jar"
     local ARCHIVE="$ARCHIVE_DIR/$ARCHIVE_FILE"

     if [ ! -d "$RESULT_DIR" ]; then
        echo State dump exiting early.  RESULT_DIR \"$RESULT_DIR\" does not exist or is not a directory.
        return
     fi

  local DUMP_DIR=$RESULT_DIR/state-dump-logs
  echo Starting state dump.   Dumping state to directory ${DUMP_DIR}

  mkdir -p ${DUMP_DIR}

  # if [ -f ${TESTOUT:-NoSuchFile.out} ]; then
   #   echo Copying ${TESTOUT} to ${DUMP_DIR}/test_suite.out
   #   cp ${TESTOUT} ${DUMP_DIR}/test_suite.out
  # fi
  
  # dumping kubectl state
  #   get domains is in its own command since this can fail if domain CRD undefined

  echo Dumping kubectl gets to kgetmany.out and kgetdomains.out in ${DUMP_DIR}
  kubectl get all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,secrets --show-labels=true --all-namespaces=true > ${DUMP_DIR}/kgetmany.out
  kubectl get domains --show-labels=true --all-namespaces=true > ${DUMP_DIR}/kgetdomains.out

  # Get all pod logs/describes and redirect/copy to files 

  set +x
  local namespaces="`kubectl get namespaces | egrep -v -e "(STATUS|kube)" | awk '{ print $1 }'`"
  set -x

  local namespace
  echo "Copying logs and describes to pod-log.NAMESPACE.PODNAME and pod-describe.NAMESPACE.PODNAME in ${DUMP_DIR}"
  for namespace in $namespaces; do
    set +x
    local pods="`kubectl get pods -n $namespace --ignore-not-found | egrep -v -e "(STATUS)" | awk '{print $1}'`"
    set -x
    local pod
    for pod in $pods; do
      local logfile=${DUMP_DIR}/pod-log.${namespace}.${pod}
      local descfile=${DUMP_DIR}/pod-describe.${namespace}.${pod}
      kubectl log $pod -n $namespace > $logfile
      kubectl describe pod $pod -n $namespace > $descfile
    done
  done

  mkdir -p $ARCHIVE_DIR || fail Could not archive, could not create target directory \'$ARCHIVE_DIR\'.
  
  # Get various k8s resource describes and redirect/copy to files 

  set +x
  local ktype
  local kobj
  local fname
  for namespace in $namespaces; do
    for ktype in pod job deploy rs service pvc ingress cm secret domain; do
      for kobj in `kubectl get $ktype -n $namespace -o=jsonpath='{range .items[*]}{" "}{.metadata.name}{end}'`; do
        fname="${DUMP_DIR}/kubectl.describe.$ktype.$kobj.ns-$namespace"
        echo "Generating $fname"
        kubectl describe $ktype $kobj -n $namespace > $fname
      done
    done
  done

  # treat pv differently as a pv is not namespaced:
  for ktype in pv; do
    for kobj in `kubectl get $ktype -o=jsonpath='{range .items[*]}{" "}{.metadata.name}{end}'`; do
      fname="${DUMP_DIR}/kubectl.describe.$ktype.$kobj"
      echo "Generating $fname"
      kubectl describe $ktype $kobj > $fname
    done
  done
  set -x
  
  # use a job to archive PV, /scratch mounts to PV_ROOT in the K8S cluster
  echo "Archiving pv directory using a kubernetes job.  Look for it on k8s cluster in $PV_ROOT/acceptance_test_pv_archive"
  local outfile=${DUMP_DIR}/archive_pv_job.out

  if [ "$WERCKER" = "true" ]; then
	$SCRIPTPATH/job.sh "/scripts/archive.sh /scratch/acceptance_test_pv /scratch/acceptance_test_pv_archive" 2>&1 | tee ${outfile}
	if [ "$?" = "0" ]; then
     	echo Job complete.
  	else
    	echo Job failed.  See ${outfile}.
  	fi
  else
  
  	$SCRIPTPATH/krun.sh -t 300 -d ${RESULT_DIR} -m "${PV_ROOT}:/sharedparent" -c 'jar cf /sharedparent/pvarchive.jar /sharedparent/acceptance_test_pv' 2>&1 | tee ${outfile}
  	if [ "$?" = "0" ]; then
    	$SCRIPTPATH/krun.sh -t 300 -d ${RESULT_DIR} -m  "${PV_ROOT}:/sharedparent" -c 'base64 /sharedparent/pvarchive.jar' > $RESULT_DIR/pvarchive.b64 2>&1
	 	if [ "$?" = "0" ]; then
   			base64 -di $RESULT_DIR/pvarchive.b64 > $ARCHIVE
   			if [ "$?" = "0" ]; then
   				echo Run complete. Archived to $ARCHIVE
   			else 
   				echo Run failed. 
   			fi
	 	else
     		# command failed
  			cat $RESULT_DIR/pvarchive.b64 | head -100
	 	fi
	 	# rm $RESULT_DIR/pvarchive.b64
  	else
    	 echo Job failed.  See ${outfile}.
  	fi	
  fi
  
  
  

  
 # if [ ! "$LEASE_ID" = "" ]; then
    # release the lease if we own it
  #  ${SCRIPTPATH}/lease.sh -d "$LEASE_ID" 2>&1 | tee ${RESULT_DIR}/release_lease.out
   # if [ "$?" = "0" ]; then
   #   echo Lease released.
   # else
   #   echo Lease could not be released:
   #   cat /${RESULT_DIR}/release_lease.out 
   # fi
 # fi

  # remove docker-images project before archiving
  rm -rf ${RESULT_DIR}/docker-images
  
  rm -rf ${RESULT_DIR}/samples
  
  # now archive all the local test files
  $SCRIPTPATH/archive.sh "${RESULT_DIR}" "${RESULT_DIR}_archive"
  
  echo Done with state dump
}

export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."
export RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
export PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
echo "RESULT_ROOT$RESULT_ROOT PV_ROOT$PV_ROOT"
    
state_dump
