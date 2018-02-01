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
  kubectl delete pv ${curdomain}-pv 
  kubectl -n $curns delete pvc ${curdomain}-pv-claim 

  echo @@ Deleting ${curdomain}-weblogic-credentials secret in namespace $curns
  kubectl -n $curns delete secret ${curdomain}-weblogic-credentials 

  echo @@ Deleting ${curdomain} traefik in namespace $curns
  kubectl delete -f $RESULT_DIR/${curns}-${curdomain}/traefik-deployment.yaml
  kubectl delete -f $RESULT_DIR/${curns}-${curdomain}/traefik-rbac.yaml
done

for ((i=0;i<OCOUNT;i++)); do
  opns=${OPER_NAMESPACES[i]}
  echo @@ Deleting operator in namespace $opns
  kubectl delete -f $RESULT_DIR/${opns}/weblogic-operator.yaml 
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
                 weblogic-operator-cluster-role                              

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
kubectl delete crd domains.weblogic.oracle

echo @@ Deleting security
kubectl delete -f $PROJECT_ROOT/kubernetes/rbac.yaml

echo @@ Deleting kibani, logstash, and elasticsearch artifacts.
kubectl delete -f $PROJECT_ROOT/src/integration-tests/kubernetes/kibana.yaml 
kubectl delete -f $PROJECT_ROOT/src/integration-tests/kubernetes/logstash.yaml  
kubectl delete -f $PROJECT_ROOT/src/integration-tests/kubernetes/elasticsearch.yaml  

echo @@ Deleting elk pv and pvc
# TBD The next line can be deleted once the fix
#     to have uniquely scoped pv/pvc per operator is in place
kubectl delete pv elk-pv  
for ((i=0;i<OCOUNT;i++)); do
  curns=${OPER_NAMESPACES[i]}
  kubectl delete pv elk-pv-${curns}
  kubectl -n ${curns} delete pvc elk-pvc 
done

for ((i=0;i<OCOUNT;i++)); do
  opns=${OPER_NAMESPACES[i]}
  echo @@ Deleting weblogic-operator namespace
  kubectl delete namespace $opns
  sleep 1  # wait in case above needs below
done

echo @@ Deleting test namespace
kubectl delete namespace test

for ((i=0;i<DCOUNT;i++)); do
  curdomain=${DOMAINS[i]}
  curns=${DOMAIN_NAMESPACES[i]}
  echo @@ Deleting configmap domain-domain1-scripts in namespace $curns
  kubectl -n $curns delete cm domain-${curdomain}-scripts 
done

waitForDelete "all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,secrets" "logstash|kibana|elastisearch|weblogic|elk|domain"

echo @@ Deleting $RESULT_DIR
/usr/local/packages/aime/ias/run_as_root "rm -fr $RESULT_ROOT/acceptance_test_tmp"
