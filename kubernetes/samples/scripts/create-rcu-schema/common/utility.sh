#!/bin/bash
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# checks if a given pod in a namespace has been deleted
function checkPodDelete(){

 pod=$1
 ns=$2
 status="Terminating"

 if [ -z ${1} ]; then 
  echo "No Pod name provided "
  exit -1 
 fi

 if [ -z ${2} ]; then 
  echo "No namespace provided "
  exit -2 
 fi

 echo "Checking Status for Pod [$pod] in namesapce [${ns}]"
 max=10
 count=1
 while [ $count -le $max ] ; do
  sleep 5 
  pod=`kubectl get po/$1 -n ${ns} | grep -v NAME | awk '{print $1}'`
  if [ -z ${pod} ]; then 
    status="Terminated"
    echo "Pod [$1] removed from nameSpace [${ns}]"
    break;
  fi
  count=`expr $count + 1`
  echo "Pod [$pod] Status [${status}]"
 done

 if [ $count -gt $max ] ; then
   echo "[ERROR] The Pod[$1] in namespace [$ns] could not be deleted in 50s"; 
   exit 1
 fi 
}

# Checks if all container(s) in a pod are running state based on READY column 
#NAME                READY     STATUS    RESTARTS   AGE
#domain1-adminserver 1/1       Running   0          4m

function checkPodState(){

 status="NotReady"
 max=60
 count=1

 pod=$1
 ns=$2
 state=${3:-1/1}

 echo "Checking Pod READY column for State [$state]"
 pname=`kubectl get po -n ${ns} | grep -w ${pod} | awk '{print $1}'`
 if [ -z ${pname} ]; then 
  echo "No such pod [$pod] exists in NameSpace [$ns] "
  exit -1
 fi 

 rcode=`kubectl get po ${pname} -n ${ns} | grep -w ${pod} | awk '{print $2}'`
 [[ ${rcode} -eq "${state}"  ]] && status="Ready"

 while [ ${status} != "Ready" -a $count -le $max ] ; do
  sleep 5 
  rcode=`kubectl get po/$pod -n ${ns} | grep -v NAME | awk '{print $2}'`
  [[ ${rcode} -eq "1/1"  ]] && status="Ready"
  echo "Pod [$1] Status is ${status} Iter [$count/$max]"
  count=`expr $count + 1`
 done
 if [ $count -gt $max ] ; then
   echo "[ERROR] Unable to start the Pod [$pod] after 300s "; 
   exit 1
 fi 

 pname=`kubectl get po -n ${ns} | grep -w ${pod} | awk '{print $1}'`
 kubectl -n ${ns} get po ${pname}
}

# Checks if a pod is available in a given namesapce 
function checkPod(){

 max=20
 count=1

 pod=$1
 ns=$2

 pname=`kubectl get po -n ${ns} | grep -w ${pod} | awk '{print $1}'`
 if [ -z ${pname} ]; then 
  echo "No such pod [$pod] exists in NameSpace [$ns]"
  sleep 10
 fi 

 rcode=`kubectl get po -n ${ns} | grep -w ${pod} | awk '{print $1}'`
 if [ ! -z ${rcode} ]; then 
  echo "[$pod] already initialized .. "
  return 0
 fi

 echo "The POD [${pod}] has not been initialized ..."
 while [ -z ${rcode} ]; do
  [[ $count -gt $max ]] && break
  echo "Pod[$pod] is being initialized ..."
  sleep 5
  rcode=`kubectl get po -n ${ns} | grep $pod | awk '{print $1}'`
  count=`expr $count + 1`
 done

 if [ $count -gt $max ] ; then
  echo "[ERROR] Could not find Pod [$pod] after 120s";
  exit 1
 fi
}
