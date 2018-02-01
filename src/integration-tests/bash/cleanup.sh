#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This script does a best-effort k8s delete of run.sh integration test k8s artifacts.
# It double checks whether its deletes are succeeding, and if not reports an "Error" and continue regardless.
#
# Note that it deletes elk and WL domain PV directories (as root). 

DOMAINS=(domain1 domain2 domain3 domain4)
DOMAIN_NAMESPACES=(default default test test2)
DCOUNT=${#DOMAINS[@]}

OPER_NAMESPACES=(weblogic-operator weblogic-operator-2)
OCOUNT=${#OPER_NAMESPACES[@]}

echo @@ Cleaning up $OCOUNT operators

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../.."
export RESULT_ROOT=${RESULT_ROOT:-/scratch/k8s_dir}
export RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
mkdir -m 777 -p $RESULT_DIR

function waitForDelete {
  maxwaitsecs=60
  echo "@@ Waiting up to $maxwaitsecs seconds for ${1:?} that contain string ${2:?} to delete."

  artcount=1
  mstart=`date +%s`
  while : ; do
    kubectl get $1 --show-labels=true --all-namespaces=true > $RESULT_DIR/kinv.out.tmp 2>&1
    egrep -e "($2)" $RESULT_DIR/kinv.out.tmp > $RESULT_DIR/kinv.out2.tmp
    artcount="`cat $RESULT_DIR/kinv.out2.tmp | wc -l`"
    mnow=`date +%s`
    echo
    echo "@@ Waiting for $artcount artifacts to delete.  Wait time $((mnow - mstart)) seconds (max=$maxwaitsecs).  Waiting for:"
    cat $RESULT_DIR/kinv.out2.tmp
    if [ $((artcount)) -eq 0 ]; then
      echo "@@ No artifacts found."
      break
    fi
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      echo "@@ Error:  $maxwaitsecs seconds reached.  Giving up."
      break
    fi
    sleep 5
  done
}

for ((i=0;i<DCOUNT;i++)); do
  curdomain=${DOMAINS[i]}
  curns=${DOMAIN_NAMESPACES[i]}

  echo @@ Deleting domain ${curdomain} in namespace $curn
  kubectl -n $curns delete domain $curdomain 

  # Give operator some time to digest the domain deletion
  sleep 3

  echo @@ Deleting create domain ${curdomain} job in namespace $curns
  kubectl -n $curns delete job domain-${curdomain}-job 

  echo @@ Deleting domain pv and pvc for domain ${curdomain} in namespace $curns
  kubectl delete pv ${curdomain}-pv --ignore-not-found=true
  kubectl -n $curns delete pvc ${curdomain}-pv-claim --ignore-not-found=true

  echo @@ Deleting ${curdomain}-weblogic-credentials secret in namespace $curns
  kubectl -n $curns delete secret ${curdomain}-weblogic-credentials --ignore-not-found=true

  #echo @@ Deleting ${curdomain} traefik in namespace $curns
  #kubectl delete -f $RESULT_DIR/${curns}-${curdomain}/traefik-deployment.yaml --ignore-not-found=true
  #kubectl delete -f $RESULT_DIR/${curns}-${curdomain}/traefik-rbac.yaml --ignore-not-found=true
done

for ((i=0;i<OCOUNT;i++)); do
  opns=${OPER_NAMESPACES[i]}
  echo @@ Deleting operator in namespace $opns
  #kubectl delete -f $RESULT_DIR/${opns}/weblogic-operator.yaml 
  kubectl delete deploy weblogic-operator -n ${opns} --ignore-not-found=true
  sleep 10
done

for ((i=0;i<DCOUNT;i++)); do
  curdomain=${DOMAINS[i]}
  curns=${DOMAIN_NAMESPACES[i]}
  kubectl -n $curns delete rolebinding weblogic-operator-rolebinding 
  kubectl -n $curns delete rolebinding weblogic-operator-operator-rolebinding          
done

kubectl delete clusterrole \
                 weblogic-operator-cluster-role-nonresource  \
                 weblogic-operator-namespace-role \
                 weblogic-operator-cluster-role --ignore-not-found=true                         

for ((i=0;i<OCOUNT;i++)); do
  opns=${OPER_NAMESPACES[i]}
  echo @@ Deleting clusterrolebindings for operator in $opns
  kubectl -n $opns delete clusterrolebinding \
                 ${opns}-operator-rolebinding-auth-delegator \
                 ${opns}-operator-rolebinding-discovery      \
                 ${opns}-operator-rolebinding-nonresource    \
                 ${opns}-operator-rolebinding
done


echo @@ Deleting crd
kubectl delete crd domains.weblogic.oracle --ignore-not-found=true

#echo @@ Deleting security
#kubectl delete -f $PROJECT_ROOT/kubernetes/rbac.yaml --ignore-not-found=true

#echo @@ Deleting kibani, logstash, and elasticsearch artifacts.
kubectl delete -f $PROJECT_ROOT/src/integration-tests/kubernetes/kibana.yaml --ignore-not-found=true
kubectl delete -f $PROJECT_ROOT/src/integration-tests/kubernetes/logstash.yaml --ignore-not-found=true
kubectl delete -f $PROJECT_ROOT/src/integration-tests/kubernetes/elasticsearch.yaml --ignore-not-found=true

echo @@ Deleting elk pv and pvc
# TBD The next line can be deleted once the fix
#     to have uniquely scoped pv/pvc per operator is in place
kubectl delete pv elk-pv --ignore-not-found=true
for ((i=0;i<OCOUNT;i++)); do
  curns=${OPER_NAMESPACES[i]}
  kubectl delete pv elk-pv-${curns} --ignore-not-found=true
  kubectl -n ${curns} delete pvc elk-pvc --ignore-not-found=true
done

for ((i=0;i<OCOUNT;i++)); do
  opns=${OPER_NAMESPACES[i]}
  echo @@ Deleting weblogic-operator namespace
  kubectl delete namespace $opns --ignore-not-found=true
  sleep 1  # wait in case above needs below
done

echo @@ Deleting test namespace
kubectl delete namespace test --ignore-not-found=true

for ((i=0;i<DCOUNT;i++)); do
  curdomain=${DOMAINS[i]}
  curns=${DOMAIN_NAMESPACES[i]}
  echo @@ Deleting configmap domain-domain1-scripts in namespace $curns
  kubectl -n $curns delete cm domain-${curdomain}-scripts 
done

waitForDelete "all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,secrets" "logstash|kibana|elastisearch|weblogic|elk|domain"

# clean-up volumes
kubectl create -f $PROJECT_ROOT/build/cleanup-pv-job.yaml
JOB_NAME="weblogic-pv-cleanup-job"
echo "Waiting for the job to complete..."
  JOB_STATUS="0"
  max=10
  count=0
  while [ "$JOB_STATUS" != "Completed" -a $count -lt $max ] ; do
    sleep 30
    count=`expr $count + 1`
    JOB_STATUS=`kubectl get pods --show-all | grep "$JOB_NAME" | awk ' { print $3; } '`
    JOB_INFO=`kubectl get pods --show-all | grep "$JOB_NAME" | awk ' { print "pod", $1, "status is", $3; } '`
    echo "status on iteration $count of $max"
    echo "$JOB_INFO"

  done

  # Confirm the job pod is status completed
  JOB_POD=`kubectl get pods --show-all | grep "$JOB_NAME" | awk ' { print $1; } '`
  if [ "$JOB_STATUS" != "Completed" ]; then
    echo The create domain job is not showing status completed after waiting 300 seconds
    echo Check the log output for errors
    kubectl logs jobs/$JOB_NAME
    fail "Exiting due to failure"
  fi

  # Check for successful completion in log file
  JOB_STS=`kubectl logs $JOB_POD | grep "Successfully Completed" | awk ' { print $1; } '`
  if [ "${JOB_STS}" != "Successfully" ]; then
    echo The log file for the create domain job does not contain a successful completion status
    echo Check the log output for errors
    kubectl logs $JOB_POD
    fail "Exiting due to failure"
  fi
  
  kubectl delete job $JOB_NAME --ignore-not-found=true

