# !/bin/sh
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#############################################################################
#
# Description:
#
#   This test performs some basic end-to-end introspector tests while
#   emulating (mocking) the operator pod.  It's useful for verifying the
#   introspector is working correctly, and for quickly testing changes to
#   its overall flow.
#
#   See the README in this directory for overall flow and usage.
#
# Notes:
#
#   The test can optionally work with any arbitrary exiting domain home, or
#   it can create a domain_home for you.  See CREATE_DOMAIN in the implementation
#   below, (default true).
#
#   The test calls the integration test 'cleanup.sh' when it starts.  It
#   passes a special parameter to cleanup.sh to skip domain_home deletion if
#   the test's CREATE_DOMAIN parameter is set to false.
#
# Internal design:
#
#   The 'meat' of the test mainly works via a series of yaml and python
#   template files in combination with a set of environment variables.  
#
#   The environmentvariables, such as PV_ROOT, DOMAIN_UID, NAMESPACE,
#   IMAGE_NAME, etc, all have defaults, or can be passed in.  See the 'export'
#   calls in the implementation below for the complete list.
#

#############################################################################
#
# Initialize basic globals
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SOURCEPATH="`echo $SCRIPTPATH | sed 's/weblogic-kubernetes-operator.*/weblogic-kubernetes-operator/'`"
traceFile=${SOURCEPATH}/operator/src/main/resources/scripts/traceUtils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

# Set TRACE_INCLUDE_FILE to true to cause tracing to include filename & line number.
export TRACE_INCLUDE_FILE=false

set -o pipefail

trace "Info: Starting."

#############################################################################
#
# Set root directory for PV
#   This matches env vars used by the 'cleanup.sh' call below. 
#

export PV_ROOT=${PV_ROOT:-/scratch/$USER/wl_k8s_test_results}

#############################################################################
#
# Set CREATE_DOMAIN to false to use an existing domain instead 
# of creating a new one.  
#   - If setting to true (the default), see "extra env var" section below
#     for additional related env vars.
#   - If setting to false, remember to also set PVCOMMENT if the 
#     pre-existing domain is not in a PV.
#

CREATE_DOMAIN=${CREATE_DOMAIN:-true}

#############################################################################
#
# Set PVCOMMENT to "#" to remove PV from wl-job/wl-pod yaml.
#   - Do this when the introspector job or wl-pod already has
#     the domain home burned into the image and so doesn't need
#     to mount a PV.
#   - Do not do this when CREATE_DOMAIN is true (create domain
#     depends on the PV).
#

export PVCOMMENT=${PVCOMMENT:-""}

#############################################################################
#
# Set env vars for an existing domain and/or a to-be-created domain:
#

#export WEBLOGIC_IMAGE_NAME=${WEBLOGIC_IMAGE_NAME:-store/oracle/weblogic:12.2.1.3}
export WEBLOGIC_IMAGE_NAME=${WEBLOGIC_IMAGE_NAME:-store/oracle/weblogic:19.1.0.0}
export WEBLOGIC_IMAGE_PULL_POLICY=${WEBLOGIC_IMAGE_PULL_POLICY:-IfNotPresent}

export DOMAIN_UID=${DOMAIN_UID:-domain1}
export NAMESPACE=${NAMESPACE:-default}

export LOG_HOME=${LOG_HOME:-/shared/logs}
export SERVER_OUT_IN_POD_LOG=${SERVER_OUT_IN_POD_LOG:-true}
export DOMAIN_HOME=${DOMAIN_HOME:-/shared/domains/${DOMAIN_UID}}

[ -z ${WEBLOGIC_CREDENTIALS_SECRET_NAME} ] && \
  export WEBLOGIC_CREDENTIALS_SECRET_NAME="${DOMAIN_UID}-weblogic-credentials"

export NODEMGR_HOME=${NODEMGR_HOME:-/shared/nodemanagers}

# TBD As a test enhancement, the following env vars could solely be derived
#     from the introspect topology file output, and 
#     so should only need to be specified when creating a new domain.
#
#     E.g. we ideally shouldn't need to define them here, and should 
#     only need to explicitly set them when 'CREATE_DOMAIN' is set to true.
#     Plus the introspection parsing step in this test should parse
#     the topology file and use the parse results to export the needed values
#     for the subsequent admin and managed server pod launches, or if
#     the macros are already set, the test should verify the topology file
#     values match the values in those macros.

export ADMIN_NAME=${ADMIN_NAME:-"admin-server"}
export ADMIN_PORT=${ADMIN_PROT:-7001}
export MANAGED_SERVER_NAME_BASE=${MANAGED_SERVER_NAME_BASE:-"managed-server"}
export DOMAIN_NAME=${DOMAIN_NAME:-"base_domain"}

#############################################################################
#
# Set extra env vars needed when CREATE_DOMAIN == true
#

if [ "$CREATE_DOMAIN" = "true" ]; then

  publicip="`kubectl cluster-info | grep KubeDNS | sed 's;.*https://\(.*\):.*;\1;'`"
  publicdns="`nslookup $publicip | grep 'name =' | sed 's/.*name = \(.*\)./\1/'`"

  export CLUSTER_NAME="${CLUSTER_NAME:-mycluster}"
  export MANAGED_SERVER_PORT=${MANAGED_SERVER_PORT:-8001}
  export CONFIGURED_MANAGED_SERVER_COUNT=${CONFIGURED_MANAGED_SERVER_COUNT:-2}
  export CLUSTER_TYPE="${CLUSTER_TYPE:-DYNAMIC}"
  export T3_CHANNEL_PORT=${T3_CHANNEL_PORT:-30012}
  export T3_PUBLIC_ADDRESS=${T3_PUBLIC_ADDRESS:-${publicdns}}
  export PRODUCTION_MODE_ENABLED=${PRODUCTION_MODE_ENABLED:-true}

fi

#############################################################################
#
# End of setup! All that follows is implementation.
#

#############################################################################
#
# Cleanup k8s artifacts and test files from previous run
#

# Location for this test to put its temporary files
test_home=/tmp/introspect

function cleanup() {
  trace "Info: Cleaning files and k8s artifacts from previous run."
 
  # first, let's delete the test's local tmp files for rm -fr
  #
  # CAUTION: We deliberately hard code the path here instead of using 
  #          using the test_home env var.  This helps prevent
  #          rm -fr from accidentally blowing away stuff it shouldn't!
   
  rm -fr /tmp/introspect
  mkdir -p $test_home || exit 1

  # now we use the generic integration test cleanup script to
  #
  #   1 - delete all operator related k8s artifacts
  #   2 - delete contents of k8s weblogic domain PV/PVC
  #       (if CREATE_DOMAIN has been set to "true")

  DELETE_FILES=${CREATE_DOMAIN:-false} \
    ${SOURCEPATH}/src/integration-tests/bash/cleanup.sh 2>&1 > \
    ${test_home}/cleanup.out

  if [ $? -ne 0 ]; then
    trace "Error:  cleanup failed.   Cleanup output:"
    cat ${test_home}/cleanup.out
    exit 1
  fi
}

#############################################################################
#
# Helper function for running a job
#

function runJob() {
  trace "Info: Running job '${1?}' for script '${2?}'."

  local job_name=${1?}
  local job_script=${2?}
  local yaml_template=${3?}
  local yaml_file=${4?}

  # Remove old job yaml in case its leftover from a previous run

  rm -f ${test_home}/${yaml_file}

  # Create the job yaml from its template

  env \
    JOB_SCRIPT=${job_script} \
    JOB_NAME=${job_name} \
    ${SCRIPTPATH}/util_subst.sh -g ${yaml_template} ${test_home}/${yaml_file} \
    || exit 1

  # Run the job

  env \
    KUBECONFIG=$KUBECONFIG \
    JOB_YAML=${test_home}/${yaml_file} \
    JOB_NAME=${job_name} \
    NAMESPACE=$NAMESPACE \
    ${SCRIPTPATH}/util_job.sh \
    2>&1 > ${test_home}/job-${1}.out

  if [ ! $? -eq 0 ]; then
    trace "Error:  job failed, job contents"
    cat ${test_home}/job-${1}.out
    trace "Error:  end of failed job contents"
    exit 1
  fi
}

#############################################################################
#
# Helper function for deploying a yaml template.  Template $1 is converted
# to ${test_home}/$2, and then ${test_home}/$2 is deployed.
#

function deployYamlTemplate() {
  local yamlt_file="${1?}"
  local yaml_file="${2?}"

  # Delete anything left over from a previous invocation of this function

  if [ -f "{test_home}/${yaml_file}" ]; then
    kubectl -n $NAMESPACE delete -f ${test_home}/${yaml_file} \
      --ignore-not-found \
      2>&1 | tracePipe "kubectl output: "
    rm -f ${test_home}/${yaml_file}
  fi

  # Apply template and create its k8s resource

  ${SCRIPTPATH}/util_subst.sh -g ${yaml_file}t ${test_home}/${yaml_file} || exit 1

  kubectl create -f ${test_home}/${yaml_file} \
    2>&1 | tracePipe "kubectl output: " || exit 1 
}

#############################################################################
#
# Helper function for deploying a configmap that contains the files in 
# a directory.
#

createConfigMapFromDir() {
  local cm_name=${1?}
  local cm_dir=${2?}

  kubectl -n $NAMESPACE create cm ${cm_name} \
    --from-file ${cm_dir} \
    2>&1 | tracePipe "kubectl output: " || exit 1 

  kubectl -n $NAMESPACE label cm ${cm_name} \
    weblogic.createdByOperator=true \
    weblogic.operatorName=look-ma-no-hands \
    weblogic.resourceVersion=domain-v2 \
    2>&1 | tracePipe "kubectl output: " || exit 1 
}


#############################################################################
#
# Helper function to lowercase a value and make it a legal DNS1123 name
# $1 - value to convert to lowercase
#

function toDNS1123Legal {
  local val=`echo $1 | tr "[:upper:]" "[:lower:]"`
  val=${val//"_"/"-"}
  echo "$val"
}


#############################################################################
#
# Deploy domain cm 
#   - contains introspect, nm, start server scripts, etc.
#   - mounted by create domain job, introspect job, and wl pods
#

function deployDomainConfigMap() {
  trace "Info: Deploying 'weblogic-domain-cm'."

  kubectl -n $NAMESPACE delete cm weblogic-domain-cm \
    --ignore-not-found  \
    2>&1 | tracePipe "kubectl output: "

  createConfigMapFromDir weblogic-domain-cm ${SOURCEPATH}/operator/src/main/resources/scripts
}

#############################################################################
#
# Deploy test script cm 
#   - contains create domain script, create test root script, and helpers for
#     same
#   - mounted by create test root job, and by create domain job
#

function deployTestScriptConfigMap() {
  trace "Info: Deploying 'test-script-cm'."

  mkdir -p ${test_home}/test-scripts

  cp ${SOURCEPATH}/operator/src/main/resources/scripts/traceUtils* ${test_home}/test-scripts || exit 1
  cp ${SCRIPTPATH}/createDomain.sh ${test_home}/test-scripts || exit 1
  cp ${SCRIPTPATH}/createTestRoot.sh ${test_home}/test-scripts || exit 1

  if [ "$CREATE_DOMAIN" = "true" ]; then
    rm -f ${test_home}/scripts/createDomain.py
    ${SCRIPTPATH}/util_subst.sh -g createDomain.pyt ${test_home}/test-scripts/createDomain.py || exit 1
  fi
  
  kubectl -n $NAMESPACE delete cm test-script-cm \
    --ignore-not-found  \
    2>&1 | tracePipe "kubectl output: "

  createConfigMapFromDir test-script-cm ${test_home}/test-scripts

}

#############################################################################
#
# Create base directory for PV (uses a job)
# (Skip if PVCOMMENT="#".)
#

function createTestRootPVDir() {

  [ "$PVCOMMENT" = "#" ] && return

  trace "Info: Creating k8s cluster physical directory 'PV_ROOT/acceptance_test_pv/domain-${DOMAIN_UID}-storage'."
  trace "Info: PV_ROOT='$PV_ROOT'"
  trace "Info: Test k8s resources use this physical directory via a PV/PVC '/shared' logical directory."

  # TBD on Wercker/Jenkins PV_ROOT will differ and may already exist or be remote
  #     so we need to add logic/booleans to skip the following mkdir/chmod as needed
  mkdir -p ${PV_ROOT} || exit 1
  chmod 777 ${PV_ROOT} || exit 1

  # Create test root within PV_ROOT via a job

  deployYamlTemplate create-test-root-pv.yamlt create-test-root-pv.yaml
  deployYamlTemplate create-test-root-pvc.yamlt create-test-root-pvc.yaml

  runJob ${DOMAIN_UID}-create-test-root-job \
         /test-scripts/createTestRoot.sh \
         create-test-root-job.yamlt \
         create-test-root-job.yaml
}

#############################################################################
#
# Deploy WebLogic pv, pvc, & admin user/pass secret
# (Skip pv/pvc if PVCOMMENT="#".)
#

function deployWebLogic_PV_PVC_and_Secret() {
  trace "Info: Deploying WebLogic domain's pv, pvc, & secret."

  [ "$PVCOMMENT" = "#" ] || deployYamlTemplate wl-pv.yamlt wl-pv.yaml
  [ "$PVCOMMENT" = "#" ] || deployYamlTemplate wl-pvc.yamlt wl-pvc.yaml
  deployYamlTemplate wl-secret.yamlt wl-secret.yaml
}

#############################################################################
#
# Run create domain job if CREATE_DOMAIN is true
#

function deployCreateDomainJob() {
  [ ! "$CREATE_DOMAIN" = "true" ] && return 0

  trace "Info: Run create domain job."

  [ "$PVCOMMENT" = "#" ] \
    && trace "Error: Cannot run create domain job, PV is disabled via PVCOMMENT." \
    && exit 1

  runJob ${DOMAIN_UID}-create-domain-job \
         /test-scripts/createDomain.sh \
         wl-job.yamlt \
         wl-create-domain-job.yaml
}

#############################################################################
#
# Run introspection job, parse its output to files, and put files in a cm
#

function deployIntrospectJob() {
  local introspect_output_cm_name=${DOMAIN_UID}-weblogic-domain-introspect-cm

  trace "Info: Run introspection job, parse its output to files, and put files in configmap '$introspect_output_cm_name'."

  # delete anything left over from a previous invocation of this function

  kubectl -n $NAMESPACE delete cm $introspect_output_cm_name \
    --ignore-not-found  \
    2>&1 | tracePipe "kubectl output: "

  # run introspection job

  runJob ${DOMAIN_UID}-introspect-domain-job \
         /weblogic-operator/scripts/introspectDomain.sh \
         wl-job.yamlt \
         wl-introspect-domain-job.yaml 

  # parse job's output files

  ${SCRIPTPATH}/util_fsplit.sh \
    ${test_home}/job-${DOMAIN_UID}-introspect-domain-job.out \
    ${test_home}/jobfiles || exit 1

  # put the outputfile in a cm

  createConfigMapFromDir $introspect_output_cm_name ${test_home}/jobfiles 

}

#############################################################################
#
# Launch admin pod and wait up to 180 seconds for it to succeed, then launch
# a managed server pod.
#

function deployPod() {
  local server_name=${1?}
  local pod_name=${DOMAIN_UID}-${server_name}
  local target_yaml=${test_home}/wl-${server_name}-pod.yaml 

  trace "Info: Deploying pod '$pod_name' and waiting for it to be ready."

  # delete anything left over from a previous invocation of this function

  if [ -f "${target_yaml}" ]; then
    kubectl -n $NAMESPACE delete -f ${target_yaml} \
      --ignore-not-found \
      2>&1 | tracePipe "kubectl output: "
    rm -f ${target_yaml}
  fi

  # Generate server pod yaml from template and deploy it

  ( 
    export SERVER_NAME=${server_name}
    # TBD SERVER_NAME should be derived from introspect results
    export SERVICE_NAME=`toDNS1123Legal ${DOMAIN_UID}-${server_name}`
    export AS_SERVICE_NAME=`toDNS1123Legal ${DOMAIN_UID}-${ADMIN_NAME}`
    ${SCRIPTPATH}/util_subst.sh -g wl-pod.yamlt ${target_yaml}  || exit 1
  ) || exit 1

  kubectl create -f ${target_yaml} \
    2>&1 | tracePipe "kubectl output: " || exit 1 

  # Wait for pod to come up successfully

  tracen "Waiting for pod readiness"
  local status="0/1"
  local startsecs=$SECONDS
  local maxsecs=180
  while [ "${status}" != "1/1" ] ; do
    if [ $((SECONDS - startsecs)) -gt $maxsecs ]; then
      echo
      trace "Error: pod $pod_name failed to start within $maxsecs seconds.  kubectl describe:"
      kubectl -n $NAMESPACE describe pod $pod_name
      trace "Error: pod $pod_name failed to start within $maxsecs seconds.  kubectl log:"
      kubectl -n $NAMESPACE logs $pod_name
      exit 1
    fi
    echo -n "."
    sleep 1
    status=`kubectl get pods -n $NAMESPACE 2>&1 | egrep $pod_name | awk '{print $2}'`
  done
  echo
}

function deploySinglePodService() {
  local server_name=${1?}
  local internal_port=${2?}
  local external_port=${3?}
  local service_name=`toDNS1123Legal ${DOMAIN_UID}-${server_name}`
  local target_yaml=${test_home}/wl-nodeport-svc-${service_name}.yaml

  trace "Info: Launching service '$service_name' internal_port=$internal_port external_port=$external_port."

  # delete anything left over from a previous invocation of this function
  if [ -f "${target_yaml}" ]; then
    kubectl -n $NAMESPACE delete -f ${target_yaml} \
      --ignore-not-found \
      2>&1 | tracePipe "kubectl output: "
    rm -f ${target_yaml}
  fi

  ( # Generate svc yaml from template 
    export SERVER_NAME="${server_name}"
    export SERVICE_INTERNAL_PORT="${internal_port}"
    export SERVICE_EXTERNAL_PORT="${external_port}"
    export SERVICE_NAME=${service_name}
    ${SCRIPTPATH}/util_subst.sh -g wl-nodeport-svc.yamlt ${target_yaml} || exit 1
  )

  kubectl create -f ${target_yaml} \
    2>&1 | tracePipe "kubectl output: " || exit 1 

  local svc=""
  local startsecs=$SECONDS
  local maxsecs=5
  while [ -z "$svc" ] ; do
    if [ $((SECONDS - startsecs)) -gt $maxsecs ]; then
      trace "Error: Service '$service_name' not found after waiting $maxsecs seconds."
      exit 1
    fi
    local cmd="kubectl get services -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$service_name\")]}'"
    svc="`eval $cmd`"
    [ -z "$svc" ] && sleep 1
  done
}

#############################################################################
#
# Main
#
# Some of the following calls will be a partial or complete no-op if
# PVCOMMENT is set, or if CREATE_DOMAIN is set to false.
#

cleanup

deployDomainConfigMap

deployTestScriptConfigMap

createTestRootPVDir

deployWebLogic_PV_PVC_and_Secret

deployCreateDomainJob

deployIntrospectJob

#
# TBD ADMIN_NAME, 7001, and MANAGED_SERVER_NAME_BASE
#     below should all be derived from introspect
#     topology file
#

deployPod ${ADMIN_NAME}

deploySinglePodService ${ADMIN_NAME} 7001 30701

deployPod ${MANAGED_SERVER_NAME_BASE}1

#
# TBD potentially add additional checks to verify wl pods are healthy
#

trace "Info: success!"
