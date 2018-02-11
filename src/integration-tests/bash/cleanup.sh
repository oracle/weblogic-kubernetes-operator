#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# -----------------
# Summary and Usage
# -----------------
#
# This script does a best-effort delete of acceptance test k8s artifacts, the
# local test tmp directory, and the potentially remote domain pv directories.
#
# This script accepts two optional env var overrides:
#
#   RESULT_ROOT  The root directory of the test temporary files.
#
#   PV_ROOT      The root directory on the kubernetes cluster
#                used for persistent volumes.
#
# See 'run.sh' for a detailed description of RESULT_ROOT and PV_ROOT.
#
# --------------------
# Detailed Description
# --------------------
#
# The test runs in 4 phases:
#
#   Phase 1:  Delete test kubernetes artifacts in an orderly
#             fashion via kubectl delete -f of previous tests's yaml
#             plus various direct kubectl deletes.
#
#   Phase 2:  Wait 15 seconds to see if stage 1 succeeded, and
#             if not, repeatedly search for all test related kubectl 
#             artifacts and try delete them directly for up to 60 more 
#             seconds.  This phase has no dependency on the 
#             previous test run's yaml files.  It makes no 
#             attempt to delete artifacts in a particular order.
#
#   Phase 3:  Use a kubernetes job to delete the PV directories
#             on the kubernetes cluster.
#
#   Phase 4:  Delete the local test output directory.
#

DOMAINS=(domain1 domain2 domain3 domain4)
DOMAIN_NAMESPACES=(default default test1 test2)
DCOUNT=${#DOMAINS[@]}

OPER_NAMESPACES=(weblogic-operator-1 weblogic-operator-2)
OCOUNT=${#OPER_NAMESPACES[@]}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
PROJECT_ROOT="$SCRIPTPATH/../../.."
RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
TMP_DIR="$RESULT_DIR/cleanup_tmp"
JOB_NAME="weblogic-command-job"

# function genericDelete
#
#   This function is a 'generic kubernetes delete' that takes two arguments:
#
#     arg1:  Comma separated list of types of kubernetes types to search/delete.
#
#            example: "all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,secrets" 
#
#     arg2:  '|' (pipe) separated list of keywords.  
#
#            Artifacts with a label or name that contains one
#            or more of the keywords are delete candidates.
#
#            example:  "logstash|kibana|elastisearch|weblogic|elk|domain"
#
#   It runs in two stages:
#     In the first, wait to see if artifacts delete on their own.
#     In the second, try to delete any leftovers.
# 
function genericDelete {

  for iteration in first second; do
    # In the first iteration, we wait to see if artifacts delete.
    # in the second iteration, we try to delete any leftovers.

    if [ "$iteration" = "first" ]; then
      maxwaitsecs=15
    else
      maxwaitsecs=60
    fi

    echo "@@ Waiting up to $maxwaitsecs seconds for ${1:?} artifacts that contain string ${2:?} to delete."
    artcount=1
    mstart=`date +%s`
    while : ; do
      resfile1="$TMP_DIR/kinv_all.out.tmp"
      resfile2="$TMP_DIR/kinv_filtered.out.tmp"
      resfile3no="$TMP_DIR/kinv_filtered_nonamespace.out.tmp"
      resfile3yes="$TMP_DIR/kinv_filtered_yesnamespace.out.tmp"

      kubectl get $1 --show-labels=true --all-namespaces=true > $resfile1 2>&1

      egrep -e "($2)" $resfile1 | grep -v 'secrets.*traefik.token' > $resfile2
      artcount="`cat $resfile2 | wc -l`"

      mnow=`date +%s`

      if [ $((artcount)) -eq 0 ]; then
        echo "@@ No artifacts found."
        return 0
      fi

      # names of remaining resources that have no namespace in form type/name
      cat $resfile2 | grep -v "^ *$" | egrep -e "^ " | awk '{ print $1 }' > $resfile3no
      unexpected_nonamespace="`cat $resfile3no | sort`"
      unexpected_nonamespace_count="`cat $resfile3no | wc -l`"

      # names of remaining resources that have a namespace in form namespace type/name
      cat $resfile2 | grep -v "^ *$" | egrep -e "^([a-z]|[A-Z])" | awk '{ print $1 " " $2 }' > $resfile3yes
      unexpected_yesnamespace="`cat $resfile3yes | sort`"
      unexpected_yesnamespace_count="`cat $resfile3yes | wc -l`"

      if [ "$iteration" = "first" ]; then
        # in the first thirty seconds we just wait to see if artifacts go away on there own

        echo "@@ Waiting for $artcount artifacts to delete.  Wait time $((mnow - mstart)) seconds (max=$maxwaitsecs).  Waiting for:"

        echo "$unexpected_nonamespace"
        echo "$unexpected_yesnamespace"

      else
        # in the second thirty seconds we try to delete remaining artifacts

        echo "@@ Trying to delete $artcount leftover artifacts, including ${unexpected_yesnamespace_count} namespaced artifacts and ${unexpected_nonamespace_count} non-namespaced artifacts, wait time $((mnow - mstart)) seconds (max=$maxwaitsecs)."

        if [ ${unexpected_yesnamespace_count} -gt 0 ]; then 
          echo "$unexpected_yesnamespace" | while read line; do
            curns="`echo \"$line\" | awk '{ print $1 }'`" 
            curitem="`echo \"$line\" | awk '{ print $2 }'`" 
            echo "kubectl -n $curns delete $curitem --ignore_not-found"
            kubectl -n $curns delete $curitem --ignore-not-found
          done
        fi

        if [ ${unexpected_nonamespace_count} -gt 0 ]; then 
          echo "$unexpected_nonamespace" | while read line; do
            echo "kubectl delete $line --ignore-not-found"
            kubectl delete $line --ignore-not-found
          done
        fi

      fi

      if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
        if [ "$iteration" = "first" ]; then
          echo "@@ Warning:  $maxwaitsecs seconds reached.   Will try deleting unexpected resources via kubectl delete."
        else
          echo "@@ Error:  $maxwaitsecs seconds reached and possibly $artcount artifacts remaining.  Giving up."
        fi
        break
      fi

      sleep 5
    done
  done
  return 1
}

function kubectlDeleteF {
   if [ -f "$1" ]; then
      kubectl delete -f "$1" --ignore-not-found
   else
      echo @@ File \"$1\" not found.  Skipping kubectl delete -f.
   fi
}

#
# This function looks for specific weblogic operator kubernetes artifacts and deletes them
#
function orderlyDelete {
  # TODO: Some of the cleanup in this function depends on yaml files generated by run.sh which
  #       probably won't be there in Wercker, and so will be skipped.  The 'genericDelete' function that
  #       is called after this function seems to take care of the leftovers, but we may want to
  #       have it delete in a certain order.  We also want to revisit this orderly delete to make
  #       it less dependent on yaml and pre-defined lists of domain and operator names & namespaces.
  #       Eventually, the two methods could converge and one of them could then go away...

  for ((i=0;i<DCOUNT;i++)); do
    curdomain=${DOMAINS[i]}
    curns=${DOMAIN_NAMESPACES[i]}
  
    echo @@ Deleting domain ${curdomain} in namespace $curn
    kubectl -n $curns delete domain $curdomain 2>&1 --ignore-not-found | grep -v "the server doesn.t have a resource type"
  
    # Give operator some time to digest the domain deletion
    sleep 3
  
    echo @@ Deleting create domain ${curdomain} job in namespace $curns
    kubectl -n $curns delete job domain-${curdomain}-job --ignore-not-found
  
    echo @@ Deleting domain pv and pvc for domain ${curdomain} in namespace $curns
    kubectl delete pv ${curdomain}-pv --ignore-not-found
    kubectl -n $curns delete pvc ${curdomain}-pv-claim --ignore-not-found
  
    echo @@ Deleting ${curdomain}-weblogic-credentials secret in namespace $curns
    kubectl -n $curns delete secret ${curdomain}-weblogic-credentials --ignore-not-found
  
    echo @@ Deleting ${curdomain} traefik in namespace $curns
    kubectlDeleteF "$RESULT_DIR/${curns}-${curdomain}/traefik-deployment.yaml" 
    kubectlDeleteF "$RESULT_DIR/${curns}-${curdomain}/traefik-rbac.yaml"
  
    echo @@ Deleting configmap domain-${curdomain}-scripts in namespace $curns
    kubectl -n $curns delete cm domain-${curdomain}-scripts  --ignore-not-found
    
    kubectl -n $curns delete deploy ${curdomain}-cluster-1-traefik --ignore-not-found=true
    kubectl -n $curns delete service ${curdomain}-cluster-1-traefik --ignore-not-found=true
    kubectl -n $curns delete service ${curdomain}-cluster-1-traefik-dashboard --ignore-not-found=true
    kubectl -n $curns delete cm ${curdomain}-cluster-1-traefik --ignore-not-found=true
    kubectl -n $curns delete serviceaccount ${curdomain}-cluster-1-traefik --ignore-not-found=true
    kubectl -n $curns delete clusterrole ${curdomain}-cluster-1-traefik --ignore-not-found=true
    kubectl -n $curns delete clusterrolebinding ${curdomain}-cluster-1-traefik --ignore-not-found=true
  done
  
  for ((i=0;i<OCOUNT;i++)); do
    opns=${OPER_NAMESPACES[i]}
    echo @@ Deleting operator in namespace $opns
    kubectlDeleteF "$RESULT_DIR/${opns}/weblogic-operator.yaml"
    # Try delete the operator directly in case above yaml file DNE:
    kubectl -n $opns delete deploy weblogic-operator  --ignore-not-found
  done
  
  kubectl -n $curns delete clusterrolebinding weblogic-operator-operator-rolebinding --ignore-not-found=true
  kubectl -n $curns delete clusterrolebinding weblogic-operator-operator-rolebinding-auth-delegator --ignore-not-found=true
  kubectl -n $curns delete clusterrolebinding weblogic-operator-operator-rolebinding-discovery --ignore-not-found=true
  kubectl -n $curns delete clusterrolebinding weblogic-operator-operator-rolebinding-nonresource --ignore-not-found=true
  
  sleep 10
  
  echo @@ Deleting various operator artifacts.

  kubectl delete crd domains.weblogic.oracle --ignore-not-found
  
  for ((i=0;i<DCOUNT;i++)); do
    curdomain=${DOMAINS[i]}
    curns=${DOMAIN_NAMESPACES[i]}
    kubectl -n $curns delete rolebinding weblogic-operator-rolebinding  --ignore-not-found
    kubectl -n $curns delete rolebinding weblogic-operator-operator-rolebinding           --ignore-not-found
  done
  
  kubectl delete clusterrole \
                   weblogic-operator-cluster-role-nonresource  \
                   weblogic-operator-namespace-role \
                   weblogic-operator-cluster-role                               --ignore-not-found
  
  for ((i=0;i<OCOUNT;i++)); do
    opns=${OPER_NAMESPACES[i]}
    echo @@ Deleting clusterrolebindings for operator in $opns
    kubectl -n $opns delete clusterrolebinding \
                   ${opns}-operator-rolebinding-auth-delegator \
                   ${opns}-operator-rolebinding-discovery      \
                   ${opns}-operator-rolebinding-nonresource    \
                   ${opns}-operator-rolebinding --ignore-not-found
  done
  
  kubectlDeleteF $PROJECT_ROOT/kubernetes/rbac.yaml 
  
  echo @@ Deleting kibani, logstash, and elasticsearch artifacts.
  
  kubectlDeleteF $PROJECT_ROOT/src/integration-tests/kubernetes/kibana.yaml
  kubectlDeleteF $PROJECT_ROOT/src/integration-tests/kubernetes/logstash.yaml
  kubectlDeleteF $PROJECT_ROOT/src/integration-tests/kubernetes/elasticsearch.yaml
  
  echo @@ Deleting oper namespaces
  
  for ((i=0;i<OCOUNT;i++)); do
    curns=${OPER_NAMESPACES[i]}
    if [ ! "${curns}" = "default" ]; then
      echo @@ Deleting ns ${curns}
      kubectl delete ns ${curns} --ignore-not-found
    fi
  done
  
  echo @@ Deleting domain namespaces
  
  for ((i=0;i<DCOUNT;i++)); do
    curns=${DOMAIN_NAMESPACES[i]}
    if [ ! "${curns}" = "default" ]; then
      echo @@ Deleting ns ${curns}
      kubectl delete ns ${curns} --ignore-not-found
    fi
  done

  kubectl delete job $JOB_NAME --ignore-not-found=true
}

function fail {
  echo @@ cleanup.sh: Error "$@"
  exit 1
}

echo @@ Starting cleanup.

echo "@@ RESULT_ROOT=$RESULT_ROOT TMP_DIR=$TMP_DIR RESULT_DIR=$RESULT_DIR PROJECT_ROOT=$PROJECT_ROOT"

mkdir -p $TMP_DIR || fail No permision to create directory $TMP_DIR

# try an ordered/controlled delete first

orderlyDelete

# try a generic delete in case the orderly delete missed something, this runs in two phases:
#   phase 1:  wait to see if artificts dissappear naturally due to the above orderlyDelete
#   phase 2:  kubectl delete left over artifacts

genericDelete "all,crd,cm,pv,pvc,ns,roles,rolebindings,clusterroles,clusterrolebindings,serviceaccount,secrets" "logstash|kibana|elastisearch|weblogic|elk|domain|traefik"
SUCCESS="$?"

# Delete pv directories using a job (/scratch maps to PV_ROOT on the k8s cluster machines).

echo @@ Launching job to delete all pv contents.  This runs in the k8s cluster, /scratch mounts PV_ROOT.
$SCRIPTPATH/job.sh "rm -fr /scratch/acceptance_test_pv"
[ "$?" = "0" ] || SUCCESS="1"

# Delete old test files owned by the current user.  

echo @@ Deleting local $RESULT_DIR contents.
rm -fr $RESULT_ROOT/acceptance_test_tmp
[ "$?" = "0" ] || SUCCESS="1"

echo @@ Deleting /tmp/test_suite.\* files.
rm -f /tmp/test_suite.*

# Bye

echo @@ Exiting with status $SUCCESS
exit $SUCCESS

