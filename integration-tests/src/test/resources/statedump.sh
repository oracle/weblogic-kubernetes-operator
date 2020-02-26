#!/bin/bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

 
# 
# state_dump 
#   - called at the end of a run
#   - places k8s logs, descriptions, etc in directory $RESULT_DIR/state-dump-logs
#   - calls archive.sh on RESULT_DIR locally, and on PV_ROOT via a job
#   - IMPORTANT: this method should not rely on exports 
#
function state_dump {
     local RESULT_DIR="$RESULT_ROOT/$IT_CLASS/acceptance_test_tmp"
     local PV_ROOT="$PV_ROOT/$IT_CLASS"
     local PROJECT_ROOT="$PROJECT_ROOT"
     local SCRIPTPATH="$PROJECT_ROOT/src/integration-tests/bash"
     local LEASE_ID="$LEASE_ID"
     local ARCHIVE_DIR="$RESULT_ROOT/$IT_CLASS/acceptance_test_pv_archive"
     local ARCHIVE_FILE="IntSuite.${IT_CLASS}.PV.`date '+%Y%m%d%H%M%S'`.jar"
     local ARCHIVE="$ARCHIVE_DIR/$ARCHIVE_FILE"

     if [ ! -d "$RESULT_DIR" ]; then
        echo State dump exiting early.  RESULT_DIR \"$RESULT_DIR\" does not exist or is not a directory.
        return
     fi

  local kubectlcmd
  if [ "$OPENSHIFT" = "true" ]; then
    kubectlcmd="oc"
  else
    kubectlcmd="kubectl"
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
  $kubectlcmd get all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,secrets --show-labels=true --all-namespaces=true > ${DUMP_DIR}/kgetmany.out
  $kubectlcmd get domains --show-labels=true --all-namespaces=true > ${DUMP_DIR}/kgetdomains.out

  # Get all pod logs/describes and redirect/copy to files 

  set +x
  local namespaces
  if [ -z "$NAMESPACE_LIST" ]; then
    namespaces="`$kubectlcmd get namespaces | egrep -v -e "(STATUS|kube)" | awk '{ print $1 }'`"
  else
    namespaces="$NAMESPACE_LIST"
  fi
  set -x

  local namespace
  echo "Copying logs and describes to pod-log.NAMESPACE.PODNAME and pod-describe.NAMESPACE.PODNAME in ${DUMP_DIR}"
  for namespace in $namespaces; do
    set +x
    local pods="`$kubectlcmd get pods -n $namespace --ignore-not-found | egrep -v -e "(STATUS)" | awk '{print $1}'`"
    set -x
    local pod
    for pod in $pods; do
      local logfile=${DUMP_DIR}/pod-log.${namespace}.${pod}
      local descfile=${DUMP_DIR}/pod-describe.${namespace}.${pod}
      $kubectlcmd log $pod -n $namespace > $logfile
      $kubectlcmd describe pod $pod -n $namespace > $descfile
    done
  done

  mkdir -p $ARCHIVE_DIR || fail Could not archive, could not create target directory \'$ARCHIVE_DIR\'.
  
  # Get various k8s resource describes and redirect/copy to files 

  set +x
  local ktype
  local kobj
  local fname
  for namespace in $namespaces; do
    for ktype in pod job deploy rs service pvc ingress cm domain; do
      for kobj in `$kubectlcmd get $ktype -n $namespace -o=jsonpath='{range .items[*]}{" "}{.metadata.name}{end}'`; do
        fname="${DUMP_DIR}/kubectl.describe.$ktype.$kobj.ns-$namespace"
        echo "Generating $fname"
        $kubectlcmd describe $ktype $kobj -n $namespace > $fname
      done
    done
  done

  # Getting  and describing secrets hangs for some of the tests in openshift. So, moving it out to its own loop for other platforms
  if [ -z "${OPENSHIFT}" ]; then
    for namespace in $namespaces; do
      for ktype in secret; do
        for kobj in `$kubectlcmd get $ktype -n $namespace -o=jsonpath='{range .items[*]}{" "}{.metadata.name}{end}'`; do
          fname="${DUMP_DIR}/kubectl.describe.$ktype.$kobj.ns-$namespace"
          echo "Generating $fname"
          $kubectlcmd describe $ktype $kobj -n $namespace > $fname
        done
      done
    done
  fi

  # treat pv differently as a pv is not namespaced:
  for ktype in pv; do
    for kobj in `$kubectlcmd get $ktype -o=jsonpath='{range .items[*]}{" "}{.metadata.name}{end}'`; do
      fname="${DUMP_DIR}/kubectl.describe.$ktype.$kobj"
      echo "Generating $fname"
      $kubectlcmd describe $ktype $kobj > $fname
    done
  done
  set -x
  
  # use a job to archive PV, /scratch mounts to PV_ROOT in the K8S cluster
  echo "Archiving pv directory using a kubernetes job.  Look for it on k8s cluster in $PV_ROOT/acceptance_test_pv_archive"
  local outfile=${DUMP_DIR}/archive_pv_job.out

  if [ "$JENKINS" = "true" ] || [ "$SHARED_CLUSTER" = "true" ]; then
    # echo "Running $SCRIPTPATH/krun.sh -i openjdk:11-oracle -t 300 -d ${RESULT_DIR} -m ${PV_ROOT}:/sharedparent -c 'jar cf /sharedparent/pvarchive.jar /sharedparent/acceptance_test_pv' 2>&1 | tee ${outfile}"
  	$SCRIPTPATH/krun.sh -i openjdk:11-oracle -t 300 -p "pod-${IT_CLASS,,}-1" -d ${RESULT_DIR} -m "${PV_ROOT}:/sharedparent" -c 'jar cf /sharedparent/pvarchive.jar /sharedparent/acceptance_test_pv' 2>&1 | tee ${outfile}
  	if [ "$?" = "0" ]; then
  		#echo "Running $SCRIPTPATH/krun.sh -i openjdk:11-oracle -t 300 -d ${RESULT_DIR} -m  ${PV_ROOT}:/sharedparent -c 'base64 /sharedparent/pvarchive.jar' > $RESULT_DIR/pvarchive.b64 2>&1"
    	$SCRIPTPATH/krun.sh -i openjdk:11-oracle -t 300 -d ${RESULT_DIR} -p "pod-${IT_CLASS,,}-2" -m  "${PV_ROOT}:/sharedparent" -c 'base64 /sharedparent/pvarchive.jar' > $RESULT_DIR/pvarchive.b64 2>&1
	 	if [ "$?" = "0" ]; then
	 		#echo "Running base64 -di $RESULT_DIR/pvarchive.b64 > $ARCHIVE"
   			base64 -di $RESULT_DIR/pvarchive.b64 > $ARCHIVE
   			if [ "$?" = "0" ]; then
   				echo Run complete. Archived to $ARCHIVE
   			else 
   				echo Run failed. 
   			fi
   			# Jenkins can only publish logs under the workspace
			mkdir -p ${JENKINS_RESULTS_DIR}
			rm -f $RESULT_DIR/pvarchive.b64
			cp $ARCHIVE ${JENKINS_RESULTS_DIR}
			if [ "$?" = "0" ]; then
   				echo Copy complete. Archive $ARCHIVE copied to ${JENKINS_RESULTS_DIR}
   			else 
   				echo Failed to copy archive $ARCHIVE to ${JENKINS_RESULTS_DIR}
	   		fi
	 	else
     		# command failed
     		echo Run failed
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
  rm -rf ${RESULT_DIR}/**/docker-images
  
  rm -rf ${RESULT_DIR}/**/samples
  
  # now archive all the local test files
  if [ "$JENKINS" = "true" ] || [ "$SHARED_CLUSTER" = "true" ]; then
  	$SCRIPTPATH/archive.sh "${RESULT_DIR}" "${JENKINS_RESULTS_DIR}"
  else 
  	$SCRIPTPATH/archive.sh "${RESULT_DIR}" "${RESULT_DIR}_archive"
  fi
  
  echo Done with state dump
}

export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."
export RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
export PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
echo "RESULT_ROOT $RESULT_ROOT PV_ROOT $PV_ROOT"
    
state_dump
