#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# -----------------
# Summary and Usage
# -----------------
#
# This script does a best-effort delete of acceptance test k8s artifacts, the
# local test tmp directory, and the potentially remote domain pv directories.
#
# This script accepts optional env var overrides:
#
#   RESULT_ROOT     The root directory of the test temporary files.
#
#   PV_ROOT         The root directory on the kubernetes cluster
#                   used for persistent volumes.
#
#   LEASE_ID        Set this if you want cleanup to release the
#                   given lease on a failure.
#
#   SHARED_CLUSTER  Set this to true if you want cleanup to delete tiller
#                   TBD tiller delete is disabled
#
#   DELETE_FILES    Delete local test files, and launch a job to delete PV
#                   hosted test files (default true).
#
#   FAST_DELETE     Set to "--grace-period=1 --timeout=1" to speedup
#                   deletes. Default is "--timeout=60s"
#
#   BG_DELETE       Run deletes in background. Default is 'true'.
#
# Dry run option:
#
#   To show what the script would do without actually doing
#   any deletes pass "-dryrun" as the first parameter.
#
# --------------------
# Detailed Description
# --------------------
#
# The cleanup runs in phases:
#
#   Phase -3: Delete all domains and wait up to 60 seconds for pods to exit
#
#   Phase -2: Delete all operator deployments
#
#   Phase -1: Delete all WL and introspector pods and wait up to 60 seconds
#             for pods to exit
#
#   Phase 0:  If helm is installed, helm delete all helm charts.
#             Possibly also delete tiller (see SHARED_CLUSTER env var above.)
#             TBD tiller delete is disabled
#
#   Phase 1:  Delete any remaining test kubernetes artifacts with labels
#             in an ordered fashion (pods before pv, etc).
#             Then wait up to 15 seconds for deletes to finish.
#
#   Phase 2:  Repeatedly search for all test related kubectl
#             artifacts and try delete them directly for up to 60 more
#             seconds.
#
#   Phase 3:  Repeat phase 2 with '--force=true --grace-period=0'.
#
#   Phase 4:  Use a kubernetes job to delete the PV directories
#             on the kubernetes cluster.
#
#   Phase 5:  Delete the local test output directory.
#
#   Phase 6:  If we own a lease, then release it on a failure.
#             (See optional LEASE_ID env var above.)
#

function timestamp {
  echo -n [`date '+%m-%d-%YT%H:%M:%S'`]
}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
PROJECT_ROOT="$SCRIPTPATH/../../.."
RESULT_ROOT=${RESULT_ROOT:-/scratch/$USER/wl_k8s_test_results}
PV_ROOT=${PV_ROOT:-$RESULT_ROOT}
RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
USER_PROJECTS_DIR="$RESULT_DIR/user-projects"
TMP_DIR="$RESULT_DIR/cleanup_tmp"
JOB_NAME="weblogic-command-job"
DRY_RUN="false"
BG_DELETE="${BG_DELETE:-true}"

[ "$1" = "-dryrun" ] && DRY_RUN="true"

echo @@ `timestamp` Info: Starting cleanup.
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$(dirname "${script}")" > /dev/null 2>&1 ; pwd -P)"
source $PROJECT_ROOT/kubernetes/internal/utility.sh

if [ ! "$1" = "" ] && [ ! "$1" = "-dryrun" ]; then
  echo "@@ `timestamp` Usage: '$(basename $0) [-dryrun]'. Pass -dryrun to skip deletes."
  exit 1
fi

# wait for current jobs to finish, and kill any remaining after $1 seconds, default is 15 seconds
function jobWaitAndKill {
  local job_timeout=${1:-15}
  echo "@@ `timestamp` Info: jobWaitAndKill: Waiting up to $job_timeout seconds for $(jobs -rp | wc -w) background delete jobs to finish."
  local start_seconds=$SECONDS
  while [ $((SECONDS - start_seconds)) -le $job_timeout ] && [ $(jobs -rp | wc -w) -gt 0 ] ; do
    sleep 0.1
  done
  echo "@@ `timestamp` Info: jobWaitAndKill: Done waiting after $((SECONDS - start_seconds)) seconds, killing $(jobs -rp | wc -w) remaining jobs."
  [ ! -z "$(jobs -rp)" ] && kill -9 $(jobs -rp)
}

# helper fn to speedup pvc deletes
function patchPVCFinalizer() {
  while read line; do
    if [ ! "$DRY_RUN" = "true" ]; then
      set -x
      kubectl patch pvc $line -p '{"metadata":{"finalizers":null}}'
      set +x
    else
      echo @@ `timestamp` Info: DRYRUN: "kubectl patch pvc $line -p '{\"metadata\":{\"finalizers\":null}}'"
    fi
  done
}

# use for kubectl delete of a specific name, exits silently if nothing found via 'get'
# usage: doDeleteByName [-n foobar] kind name
function doDeleteByName {

  # sneaky way to get current pid that works in ancient MacOS bash 3
  local mypid=$(bash -c "echo \$PPID")

  local tmpfile="/tmp/$(basename $0).doDeleteByName.$PPID.$mypid.$SECONDS"

  kubectl get "$@" -o=jsonpath='{.items[*]}{.kind}{" "}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}' > $tmpfile

  # exit silently if nothing to delete
  if [ `cat $tmpfile | wc -l` -eq 0 ]; then
    rm -f $tmpfile
    return
  fi

  local ttextt=""
  [ "$DRY_RUN" = "true" ] && ttextt="DRYRUN"
  echo @@ `timestamp` Info: doDeleteByName $ttextt: kubectl $FAST_DELETE delete "$@" --ignore-not-found
  cat $tmpfile
  rm $tmpfile

  if [ ! "$DRY_RUN" = true ]; then
    kubectl $FAST_DELETE delete "$@" --ignore-not-found
  fi
}

# use for kubectl delete of a potential set, exits silently if nothing found via 'get'
# usage: doDeleteByRange [-n foobar] kind -l labelexpression -l labelexpression ...
function doDeleteByRange {

  # sneaky way to get current pid that works in ancient MacOS bash 3
  local mypid=$(bash -c "echo \$PPID")

  local tmpfile="/tmp/$(basename $0).doDeleteByRange.$PPID.$mypid.$SECONDS"

  kubectl get "$@" -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}' > $tmpfile

  # exit silently if nothing to delete
  if [ `cat $tmpfile | wc -l` -eq 0 ]; then
    rm -f $tmpfile
    return
  fi

  local ttextt=""
  [ "$DRY_RUN" = "true" ] && ttextt="DRYRUN"
  echo @@ `timestamp` Info: doDeleteByRange $ttextt: kubectl $FAST_DELETE delete "$@" --ignore-not-found
  cat $tmpfile
  rm $tmpfile

  if [ ! "$DRY_RUN" = true ]; then
    kubectl $FAST_DELETE delete "$@" --ignore-not-found
  fi
}

# waits up to $1 seconds for WL pods and introspector pods to exit
waitForWebLogicPods() {
  local pod_count_wls=0
  local pod_count_int=0
  local pod_count_tot=0
  local max_secs=${1:-60}
  STARTSEC=$SECONDS
  echo "@@ `timestamp` Info: Waiting $max_secs seconds for WebLogic server and introspector pods to exit."
  echo -n "@@ `timestamp` Info: seconds/introspector-pod-count/wl-pod-count:"
  while [ $((SECONDS - STARTSEC)) -lt $max_secs ]; do
    # WebLogic server pods have the 'weblogic.serverName' label
    pod_count_wls="$(kubectl --all-namespaces=true get pods -l weblogic.serverName -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}' | wc -l)"
    # Introspector pods have the 'weblogic.domainUID' and 'job-name' labels
    pod_count_int="$(kubectl --all-namespaces=true get pods -l weblogic.domainUID -l job-name -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}' | wc -l)"
    pod_count_tot=$((pod_count_wls + pod_count_int))
      if [ $((pod_count_tot)) -eq 0 ]; then
      break
    fi
    echo -n " $((SECONDS - STARTSEC))/$pod_count_int/$pod_count_wls"
    sleep 0.5
  done
  echo

  if [ $((pod_count_tot)) -ne 0 ]; then
    echo "@@ `timestamp` Warning: Wait timed out after $max_secs seconds. There are still $pod_count_tot pods running:"
    kubectl --all-namespaces=true get pods -l weblogic.serverName
    kubectl --all-namespaces=true get pods -l weblogic.domainUID -l job-name
  else
    echo "@@ `timestamp` Info: No pods detected after $((SECONDS - STARTSEC)) seconds."
  fi
}

# waits up to $1 seconds for $LABEL_SELECTOR pods to exit
waitForLabelPods() {
  #
  # wait for pods with label $LABEL_SELECTOR to exit
  #

  local total=0
  local mstart=`date +%s`
  local mnow=mstart
  local maxwaitsecs=$1
  local pods
  echo "@@ `timestamp` Info: Waiting $maxwaitsecs for pods to stop running."
  while [ $((mnow - mstart)) -lt $maxwaitsecs ]; do
    pods=($(kubectl get pods --all-namespaces -l $LABEL_SELECTOR -o jsonpath='{range .items[*]}{.metadata.name} {end}'))
    total=${#pods[*]}
    if [ $total -eq 0 ] ; then
        break
    else
      echo "@@ `timestamp` Info: There are $total running pods with label $LABEL_SELECTOR: $pods".
    fi
    sleep 0.5
    mnow=`date +%s`
  done

  if [ $total -gt 0 ]; then
    echo "@@ `timestamp` Warning: after waiting $maxwaitsecs seconds, there are still $total running pods with label $LABEL_SELECTOR: $pods"
  fi
}

# delete all domains in all namespaces
# operator(s) should detect domain deletion and shutdown the domain's pods
deleteDomains() {
  local ns
  local dn
  local domain_crd=domains.weblogic.oracle
  local count=0

  echo "@@ `timestamp` Info: Setting /tmp/diefast on every WL pod to speedup its demise."

  if [ "$DRY_RUN" = "true" ]; then
    kubectl --all-namespaces=true get pods -l weblogic.serverName \
      -o=jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.metadata.name}{"\n"}' \
      | awk '{ system("echo @@ DRYRUN: kubectl -n " $1 " exec " $2 " touch /tmp/diefast") }'
  else
    kubectl --all-namespaces=true get pods -l weblogic.serverName \
      -o=jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.metadata.name}{"\n"}' \
      | awk '{ system("set -x ; kubectl -n " $1 " exec " $2 " touch /tmp/diefast") }'
  fi

  echo "@@ `timestamp` Info: About to delete each domain."
  if [ $(kubectl get crd $domain_crd --ignore-not-found | grep $domain_crd | wc -l) = 1 ]; then
    for ns in $(kubectl get namespace -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}')
    do
      for dn in $(kubectl -n $ns get domain -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}')
      do
        doDeleteByName -n $ns domain $dn
        count=$((count + 1))
      done
    done
  fi
  echo "@@ `timestamp` Info: Found and deleted $count domains."
  return 0
}

# delete all operator deployments
deleteOperators() {
  echo "@@ `timestamp` Info: Deleting operator deployments."
  local ns
  for ns in $(kubectl get namespace -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}')
  do
    if [ "$BG_DELETE" = "true" ]; then
      doDeleteByRange -n $ns deployments -l weblogic.operatorName &
    else
      doDeleteByRange -n $ns deployments -l weblogic.operatorName
    fi
  done
  [ "$BG_DELETE" = "true" ] && jobWaitAndKill
}

# delete all WL pods
deleteWebLogicPods() {
  echo "@@ `timestamp` Info: Deleting WebLogic pods."
  local ns
  for ns in $(kubectl get namespace -o=jsonpath='{range .items[*]}{.metadata.name}{"\n"}')
  do
    if [ "$BG_DELETE" = "true" ]; then
      # WLS pods
      doDeleteByRange -n $ns pods -l weblogic.serverName &
      # Introspector pods
      doDeleteByRange -n $ns pods -l weblogic.domainUID -l job-name &
    else
      # WLS pods
      doDeleteByRange -n $ns pods -l weblogic.serverName
      # Introspector pods
      doDeleteByRange -n $ns pods -l weblogic.domainUID -l job-name
    fi
  done
  [ "$BG_DELETE" = "true" ] && jobWaitAndKill
}

# delete everything with label $LABEL_SELECTOR
# - the delete order is order of NAMESPACED_TYPES and then NOT_NAMESPACED_TYPES
# - uses $1 as a temporary file
function deleteLabel {
  echo @@ `timestamp` Info: Delete resources with label $LABEL_SELECTOR.

  # clean the output file first

  rm -f $1

  #
  # first, let's get all namespaced types with -l $LABEL_SELECTOR
  #        in the order they're specified in NAMESPACED_TYPES
  #

  for resource_type in $NAMESPACED_TYPES
  do
    # patch PVCs to speedup their deletion
    if [ "$resource_type" = "pvc" ]; then
      echo "@@ `timestamp` Info: Disabling finalizers on pvc resources to speed up their deletion (LABEL_SELECTOR='$LABEL_SELECTOR')."
      kubectl get pvc \
        -l "$LABEL_SELECTOR" \
        -o=jsonpath='{range .items[*]}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}{end}' \
        --all-namespaces=true \
        | patchPVCFinalizer
    fi

    kubectl get $resource_type \
      -l "$LABEL_SELECTOR" \
      -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}{end}' \
      --all-namespaces=true >> $1
  done

  #
  # now, get all non-namespaced types with -l $LABEL_SELECTOR
  #      in the order they're specified in NOT_NAMESPACED_TYPES
  #

  for resource_type in $NOT_NAMESPACED_TYPES
  do
    kubectl get $resource_type \
      -l "$LABEL_SELECTOR" \
      -o=jsonpath='{range .items[*]}{.kind}{" "}{.metadata.name}{"\n"}{end}' \
      --all-namespaces=true >> $1
  done

  #
  # now, let's do the actual deletes, one by one, in the order above
  #

  cat $1 | while read line; do
    if [ "$BG_DELETE" = "true" ]; then
      doDeleteByName $line &
    else
      doDeleteByName $line
    fi
  done

  [ "$BG_DELETE" = "true" ] && jobWaitAndKill

  #
  # finally, let's wait for pods with label $LABEL_SELECTOR to exit
  #

  if [ "$DRY_RUN" = "true" ]; then
    waitForLabelPods 10
  else
    waitForLabelPods 60
  fi
}

# deletes all namespaces in the $1 file, assumes the namespaces are in column 4 of $1
# TBD: Currently not called
function deleteNamespaces {
  cat $1 | awk '{ print $4 }' | grep -v "^$" | sort -u | while read line; do
    if [ "$line" != "default" ]; then
      kubectl $FAST_DELETE delete namespace $line --ignore-not-found
    fi
  done
}

# Delete everything individually by name, one by one, in order of type, that matches given label $LABEL_SELECTOR
# The order is determined by NAMESPACED_TYPES NOT_NAMESPACED_TYPES below...
function deleteByTypeAndLabel {
  HANDLE_VOYAGER="false"
  VOYAGER_ING_NAME="ingresses.voyager.appscode.com"
  if [ `kubectl get crd $VOYAGER_ING_NAME --ignore-not-found | grep $VOYAGER_ING_NAME | wc -l` = 1 ]; then
    HANDLE_VOYAGER="true"
  else
    VOYAGER_ING_NAME=""
  fi

  DOMAIN_CRD="domains.weblogic.oracle"
  if [ ! `kubectl get crd $DOMAIN_CRD --ignore-not-found | grep $DOMAIN_CRD | wc -l` = 1 ]; then
    DOMAIN_CRD=""
  fi

  NAMESPACED_TYPES="$DOMAIN_CRD pod job deploy rs service ingress $VOYAGER_ING_NAME pvc cm serviceaccount role rolebinding secret"

  NOT_NAMESPACED_TYPES="pv crd clusterroles clusterrolebindings"

  tempfile="/tmp/$(basename $0).tmp.$$"  # == /tmp/[script-file-name].tmp.[pid]

  LABEL_SELECTOR="weblogic.domainUID"

  echo "@@ `timestamp` Info: Deleting wls domain resources by LABEL_SELECTOR='$LABEL_SELECTOR', NAMESPACED_TYPES='$NAMESPACED_TYPES', NOT_NAMESPACED_TYPES='$NOT_NAMESPACED_TYPES'."
  deleteLabel "$tempfile-0"

  LABEL_SELECTOR="weblogic.operatorName"
  echo "@@ `timestamp` Info: Deleting wls operator resources by LABEL_SELECTOR='$LABEL_SELECTOR', NAMESPACED_TYPES='$NAMESPACED_TYPES', NOT_NAMESPACED_TYPES='$NOT_NAMESPACED_TYPES'."
  deleteLabel "$tempfile-1"

  # TBD: This appears to hurt more than it helps. Doesn't protect against out of order deletes.
  # deleteNamespaces "$tempfile-0"
  # deleteNamespaces "$tempfile-1"

  rm -f $tempfile-0
  rm -f $tempfile-1

  if [ "$HANDLE_VOYAGER" = "true" ]; then
    if [ ! "$DRY_RUN" = "true" ]; then
      echo @@ `timestamp` Info: Deleting voyager controller.
      # calls script in utility.sh
      deleteVoyagerOperator
    fi
  fi
}

# function genericDelete
#
#   This function is a 'generic kubernetes delete' that takes four arguments:
#
#     arg1:  Comma separated list of types of kubernetes namespaced types to search/delete.
#            example: "all,cm,pvc,ns,roles,rolebindings,secrets"
#
#     arg2:  Comma separated list of types of kubernetes non-namespaced types to search/delete.
#            example: "crd,pv,clusterroles,clusterrolebindings"
#
#     arg3:  '|' (pipe) separated list of keywords.
#            Artifacts with a label or name that contains one
#            or more of the keywords are delete candidates.
#            example:  "logstash|kibana|elastisearch|weblogic|elk|domain"
#
#     arg4:  Action to take.
#
#            -wait:             Wait 15 seconds for objects to exit on their own.
#
#            -friendlyDelete:   Repeatedly delete objects using default delete
#                               for no more than 60 seconds total.
#
#            -forceDelete:      Try delete objects using "--force=true" and
#                               "--grace-period=0" for no more than 60 seconds total. 
#                               Note that this is incompatible with "FAST_DELETE" so 
#                               FAST_DELETE is overridden in this path.
#
function genericDelete {

  local mode="$4"

  if [ "$mode" = "-wait" ]; then
    local maxwaitsecs=15
  else
    if [ "$DRY_RUN" = "true" ]; then
      local maxwaitsecs=15
    else
      local maxwaitsecs=60
    fi
  fi

  echo "@@ `timestamp` Info: In genericDelete with mode '$mode'"
  echo "@@ `timestamp` Info: Waiting up to $maxwaitsecs seconds for ${1:?} and ${2:?} artifacts that contain string ${3:?} to delete."

  local artcount_no
  local artcount_yes
  local artcount_total
  local resfile_no
  local resfile_yes

  local mstart=`date +%s`

  # patch PVCs to speedup their deletion
  if [ ! "$1" = "${1/pvc//}" ]; then
    echo "@@ `timestamp` Info: Disabling finalizers on pvc resources to speed up their deletion (filter='${3}')."
    kubectl get pvc \
      -o=jsonpath='{range .items[*]}{.metadata.name}{" -n "}{.metadata.namespace}{"\n"}{end}' \
      --all-namespaces=true \
      | egrep -e "($3)" | patchPVCFinalizer
  fi

  while : ; do
    resfile_no="$TMP_DIR/kinv_filtered_nonamespace.out.tmp"
    resfile_yes="$TMP_DIR/kinv_filtered_yesnamespace.out.tmp"

    # leftover namespaced artifacts
    kubectl get $1 \
        -o=jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.kind}{"/"}{.metadata.name}{"\n"}{end}' \
        --all-namespaces=true 2>&1 \
        | egrep -e "($3)" | sort > $resfile_yes 2>&1
    artcount_yes="`cat $resfile_yes | wc -l`"

    # leftover non-namespaced artifacts
    kubectl get $2 \
        -o=jsonpath='{range .items[*]}{.kind}{"/"}{.metadata.name}{"\n"}{end}' \
        --all-namespaces=true 2>&1 \
        | egrep -e "($3)" | sort > $resfile_no 2>&1
    artcount_no="`cat $resfile_no | wc -l`"

    artcount_total=$((artcount_yes + artcount_no))

    mnow=`date +%s`

    if [ $((artcount_total)) -eq 0 ]; then
      echo "@@ `timestamp` Info: No artifacts found."
      return 0
    fi

    if [ "$mode" = "-wait" ]; then
      # just wait to see if artifacts go away on there own

      echo "@@ `timestamp` Info: Waiting for $artcount_total artifacts to delete.  Wait time $((mnow - mstart)) seconds (max=$maxwaitsecs).  Waiting for:"

      cat $resfile_yes | awk '{ print "n=" $1 " " $2 }'
      cat $resfile_no | awk '{ print $1 }'

    else
      # try to delete remaining artifacts

      echo "@@ `timestamp` Info: Trying to delete ${artcount_total} leftover artifacts, including ${artcount_yes} namespaced artifacts and ${artcount_no} non-namespaced artifacts, wait time $((mnow - mstart)) seconds (max=$maxwaitsecs)."

      if [ "$mode" = "-forceDelete" ]; then
        local fast_delete_orig="$FAST_DELETE"
        FAST_DELETE="--force=true --grace-period=0"
      fi

      if [ ${artcount_yes} -gt 0 ]; then
        cat "$resfile_yes" | while read line; do
          local args="`echo \"$line\" | awk '{ print " " $2 " -n " $1  }'`"
          if [ "$BG_DELETE" = "true" ]; then
            doDeleteByName $args &
          else
            doDeleteByName $args
          fi
        done
      fi

      if [ ${artcount_no} -gt 0 ]; then
        cat "$resfile_no" | while read line; do
          if [ "$BG_DELETE" = "true" ]; then
            doDeleteByName $line &
          else
            doDeleteByName $line
          fi
        done
      fi

      [ "$BG_DELETE" = "true" ] && jobWaitAndKill

      if [ "$mode" = "-forceDelete" ]; then
        FAST_DELETE="$fast_delete_orig"
      fi

    fi

    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      if [ "$mode" = "-wait" ]; then
        echo "@@ `timestamp` Warning:  ${maxwaitsecs} seconds reached.   Will try deleting unexpected resources via kubectl delete."
      else
        echo "@@ `timestamp` Error:  ${maxwaitsecs} seconds reached and possibly ${artcount_total} artifacts remaining.  Giving up."
      fi
      break
    fi

    sleep 0.5
  done

  return 1
}

# if helm is installed, delete all helm releases
function deleteHelmReleases {
  [ ! -x "$(command -v helm)" ] && return

  helm version --short --client  | grep v2
  [[ $? == 0 ]] && HELM_VERSION=V2
  [[ $? == 1 ]] && HELM_VERSION=V3
  echo "@@ `timestamp` Info: Detected Helm Version [$(helm version --short --client)]"

  echo @@ `timestamp` Info: Deleting installed helm charts

  if [ "$HELM_VERSION" == "V2" ]; then
   helm list --short | while read helm_name; do
   if [ ! "$DRY_RUN" = "true" ]; then
   (
     set -x
     helm delete --purge  $helm_name
    )
   else
     echo @@ `timestamp` Info: DRYRUN: helm delete --purge  $helm_name
   fi
   done
  fi

  if [ "$HELM_VERSION" == "V3" ]; then
    if [ ! "$DRY_RUN" = "true" ]; then
    (
      set -x
      helm list --all-namespaces | grep -v NAME | awk '{system("helm uninstall " $1 " --namespace " $2)}'
    )
    else
      echo @@ `timestamp` Info: DRYRUN: helm uninstall
      helm list --all-namespaces | grep -v NAME | awk '{print "DRYRUN: helm uninstall " $1 " --namespace " $2}'
   fi
  fi

  # cleanup tiller artifacts
  if [ "$SHARED_CLUSTER" = "true" ]; then
    echo @@ `timestamp` Info: Skipping tiller delete.
    # TBD: According to MarkN no Tiller delete is needed.
    # kubectl $FAST_DELETE -n kube-system delete deployment tiller-deploy --ignore-not-found=true
    # kubectl $FAST_DELETE delete clusterrolebinding tiller-cluster-rule --ignore-not-found=true
    # kubectl $FAST_DELETE -n kube-system delete serviceaccount tiller --ignore-not-found=true
  fi
}


FAST_DELETE=${FAST_DELETE:---timeout=60s}

echo "@@ `timestamp` Info: RESULT_ROOT=$RESULT_ROOT TMP_DIR=$TMP_DIR RESULT_DIR=$RESULT_DIR PROJECT_ROOT=$PROJECT_ROOT PV_ROOT=$PV_ROOT"

mkdir -p $TMP_DIR || exit 1

#
# Phase -3: Delete every domain, then wait for their pods to go away
#

deleteDomains

if [ "$DRY_RUN" = "true" ]; then
  waitForWebLogicPods 10
else
  waitForWebLogicPods 60
fi

#
# Phase -2: Delete every operator deployment
#

deleteOperators

#
# Phase -1: Delete every WL pod, including introspector pods, then wait for the pods to go away
#  (If the operators were healthy when domains were deleted above, there should be no pods, but just in case.)
#

deleteWebLogicPods

if [ "$DRY_RUN" = "true" ]; then
  waitForWebLogicPods 10
else
  waitForWebLogicPods 60
fi

#
# Phase 0: if helm is installed, delete all installed helm releases
#

deleteHelmReleases

#
# Phase 1, try an orderly mass delete, in order of type, looking for Operator related labels
#

deleteByTypeAndLabel

#
# arguments for genericDelete
#   g_arg1 - namespaced kubernetes artifacts
#   g_arg2 - non-namespaced artifacts
#   g_arg3 - keywords in deletable artifacts
#

g_arg1="all,cm,pvc,roles,rolebindings,serviceaccount,secrets,ingress,deployments"
g_arg2="crd,pv,ns,clusterroles,clusterrolebindings"
g_arg3="Namespace/ns-|logstash|kibana|elastisearch|weblogic|elk|domain|traefik|voyager|apache-webtier|mysql|test|opns|oracle-db|rcu|prometheus-server|prometheus-alertmanager|prometheus-kube-state-metrics|grafana-clusterrole|grafana-clusterrolebinding"

#
# Phase 1 (continued):  wait 15 seconds to see if artifacts dissappear naturally due to phase 1 effort
#

# TBD: Commenting out. Assume no longer needed as previous phase already has waits for labeled pods
# genericDelete "$g_arg1" "$g_arg2" "$g_arg3" -wait

#
# Phase 2: "friendly" kubectl delete left over artifacts individually
#          in no specific order for up to 60 seconds
#

genericDelete "$g_arg1" "$g_arg2" "$g_arg3" -friendlyDelete

#
# Phase 3: "--force=true --grace-period=0" kubectl delete left over artifacts individually
#          in no specific order for up to 60 seconds
#

genericDelete "$g_arg1" "$g_arg2" "$g_arg3" -forceDelete
SUCCESS="$?"

#
# Phase 4: Delete pv host directories.
#

if [ "${DELETE_FILES:-true}" = "true" ] && [ "$DRY_RUN" = "false" ]; then

  # Delete pv directories using a run (/sharedparent maps to PV_ROOT on the k8s cluster machines).

  echo @@ `timestamp` Info: Launching run to delete all pv contents.  This runs in the k8s cluster, /sharedparent mounts PV_ROOT.
  # $SCRIPTPATH/job.sh "rm -fr /scratch/acceptance_test_pv"
  $SCRIPTPATH/krun.sh -i openjdk:11-oracle -t 600 -m "${PV_ROOT}:/sharedparent" -c 'rm -fr /sharedparent/*/acceptance_test_pv'
  [ "$?" = "0" ] || SUCCESS="1"
  echo @@ `timestamp` Info: SUCCESS=$SUCCESS

  # Delete old test files owned by the current user.

  echo @@ `timestamp` Info: Deleting local $RESULT_DIR contents.
  rm -fr $RESULT_ROOT/*/acceptance_test_tmp
  [ "$?" = "0" ] || SUCCESS="1"
  echo @@ `timestamp` Info: SUCCESS=$SUCCESS

  echo @@ `timestamp` Info: Deleting /tmp/test_suite.\* files.
  rm -f /tmp/test_suite.*

fi

# Bye

if [ ! "$LEASE_ID" = "" ] && [ ! "$SUCCESS" = "0" ]; then
  # release the lease if we own it
  ${SCRIPTPATH}/lease.sh -d "$LEASE_ID" > /tmp/release_lease.out 2>&1
  if [ "$?" = "0" ]; then
    echo @@ `timestamp` Lease released.
  else
    echo @@ `timestamp` Lease could not be released:
    cat /tmp/release_lease.out
  fi
  rm -f /tmp/release_lease.out
fi

echo @@ `timestamp` Info: Exiting after $SECONDS seconds with status $SUCCESS 
exit $SUCCESS
