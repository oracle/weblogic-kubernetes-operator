# Copyright 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.
#
# Description
# This sample script check the state of a given pod in a namespace 
# based on READY column String
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

function checkPod(){

max=20
count=1

pod=$1
ns=$2

pname=`kubectl get po -n ${ns} | grep -w ${pod} | awk '{print $1}'`
if [ -z ${pname} ]; then 
 echo "No such pod [$pod] exists in NameSpace [$ns]"
 sleep 10
 #exit -1
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

pod=${1:-domain1-adminserver}
ns=${2:-weblogic-domain}
state=${3:-1/1}

checkPod ${pod} ${ns}
checkPodState ${pod} ${ns} ${state}
