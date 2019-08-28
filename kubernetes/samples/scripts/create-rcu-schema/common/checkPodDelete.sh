# Copyright 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.
#
# Description
# This sample script check if a given pod in a namespace is deleted 

#!/bin/bash

function checkPodDelete(){

pod=$1
ns=$2

status="Terminating"

if [ -z ${1} ]; then 
 echo "Either No Pod Name is provided or Pod has been Terminated ..."
 exit -1 
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

pod=${1:-rcu}
ns=${2:-default}

checkPodDelete ${pod} ${ns}
