# !/bin/sh
# Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#############################################################################
#
# Description:
#
#   This test performs some basic end-to-end introspector tests while
#   emulating (mocking) the operator pod.  It's useful for verifying the
#   introspector is working correctly, and for quickly testing changes to
#   its overall flow.
#
#   The test calls the integration test 'cleanup.sh' when it starts, which
#   will delete all test resources and the domain_home that might
#   have been left over from a previous run.
#
#   See the README in this directory for overall flow and usage.
#
# Internal design:
#
#   The 'meat' of the test mainly works via a series of yaml and python
#   template files in combination with a set of environment variables.
#
#   The environment variables, such as PV_ROOT, DOMAIN_UID, NAMESPACE,
#   IMAGE_NAME, etc, all have defaults, or can be passed in.  See the 'export'
#   calls in the implementation below for the complete list.
#

#############################################################################
#
# Initialize basic globals
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SOURCEPATH="`echo $SCRIPTPATH | sed 's/weblogic-kubernetes-operator.*/weblogic-kubernetes-operator/'`"
traceFile=${SOURCEPATH}/operator/src/main/resources/scripts/utils.sh
source ${traceFile}
source ${SCRIPTPATH}/util_dots.sh
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
# Set env vars for to-be-created domain:
#

export WEBLOGIC_IMAGE_NAME=${WEBLOGIC_IMAGE_NAME:-container-registry.oracle.com/middleware/weblogic}
export WEBLOGIC_IMAGE_TAG=${WEBLOGIC_IMAGE_TAG:-12.2.1.3}
export WEBLOGIC_IMAGE_PULL_POLICY=${WEBLOGIC_IMAGE_PULL_POLICY:-IfNotPresent}

export DOMAIN_UID=${DOMAIN_UID:-domain1}
export NAMESPACE=${NAMESPACE:-default}

export LOG_HOME=${LOG_HOME:-/shared/logs}
export SERVER_OUT_IN_POD_LOG=${SERVER_OUT_IN_POD_LOG:-true}
export DOMAIN_HOME=${DOMAIN_HOME:-/shared/domains/${DOMAIN_UID}}

[ -z ${WEBLOGIC_CREDENTIALS_SECRET_NAME} ] && \
  export WEBLOGIC_CREDENTIALS_SECRET_NAME="${DOMAIN_UID}-weblogic-credentials"

export NODEMGR_HOME=${NODEMGR_HOME:-/shared/nodemanagers}

export ADMIN_NAME=${ADMIN_NAME:-"admin-server"}
export ADMIN_PORT=${ADMIN_PORT:-7001}
export MANAGED_SERVER_NAME_BASE=${MANAGED_SERVER_NAME_BASE:-"managed-server"}
export DOMAIN_NAME=${DOMAIN_NAME:-"base_domain"}
export ADMINISTRATION_PORT=${ADMINISTRATION_PORT:-7099}

#publicip="`kubectl cluster-info | grep KubeDNS | sed 's;.*//\([0-9]*\.[0-9]*\.[0-9]*\.[0-9]*\):.*;\1;'`"
#export TEST_HOST="`nslookup $publicip | grep 'name =' | sed 's/.*name = \(.*\)./\1/'`"
export TEST_HOST="mycustompublicaddress"

export CLUSTER_NAME="${CLUSTER_NAME:-mycluster}"
export MANAGED_SERVER_PORT=${MANAGED_SERVER_PORT:-8001}
export CONFIGURED_MANAGED_SERVER_COUNT=${CONFIGURED_MANAGED_SERVER_COUNT:-2}
export CLUSTER_TYPE="${CLUSTER_TYPE:-DYNAMIC}"
export T3CHANNEL1_PORT=${T3CHANNEL1_PORT:-30012}
export T3CHANNEL2_PORT=${T3CHANNEL2_PORT:-30013}
export T3CHANNEL3_PORT=${T3CHANNEL3_PORT:-30014}
export T3_PUBLIC_ADDRESS=${T3_PUBLIC_ADDRESS:-}
export PRODUCTION_MODE_ENABLED=${PRODUCTION_MODE_ENABLED:-true}
export ALLOW_DYNAMIC_CLUSTER_IN_FMW=${ALLOW_DYNAMIC_CLUSTER_IN_FMW:-false}

# whether this test run is expecting a domain validation error
export EXPECT_INVALID_DOMAIN=${EXPECT_INVALID_DOMAIN:-false}

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

function cleanupMajor() {
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

  tracen "Info: Waiting for cleanup.sh to complete."
  printdots_start
  FAST_DELETE="--grace-period=1 --timeout=1s" \
    ${SOURCEPATH}/src/integration-tests/bash/cleanup.sh 2>&1 > \
    ${test_home}/cleanup.out
  status=$?
  printdots_end

  if [ $status -ne 0 ]; then
    trace "Error:  cleanup failed.   Cleanup output:"
    cat ${test_home}/cleanup.out
    trace "Error:  cleanup failed."
    return 1
  fi
}

function cleanupMinor() {
  trace "Info: RERUN_INTROSPECT_ONLY==true, skipping cleanup.sh and domain home setup, and only deleting wl pods + introspector job."

  kubectl -n $NAMESPACE delete pod ${DOMAIN_UID}-${ADMIN_NAME}                --grace-period=2 > /dev/null 2>&1
  kubectl -n $NAMESPACE delete pod ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE}1 --grace-period=2 > /dev/null 2>&1
  kubectl -n $NAMESPACE delete job ${DOMAIN_UID}-introspect-domain-job        --grace-period=2 > /dev/null 2>&1
  kubectl -n $NAMESPACE delete pod ${DOMAIN_UID}--introspect-domain-pod       --grace-period=2 > /dev/null 2>&1
  rm -fr ${test_home}/jobfiles
  tracen "Info: Waiting for wl pods to completely go away before continuing."
  while [ 1 -eq 1 ]; do
    echo -n "."
    # echo
    # echo "Waiting for: '`kubectl -n ${NAMESPACE} get pods | grep \"${DOMAIN_UID}.*server\"`'"
    # echo "Waiting for: '`kubectl -n ${NAMESPACE} get pods | grep \"${DOMAIN_UID}.*introspect\"`'"
    [ "`kubectl -n ${NAMESPACE} get pods | grep \"${DOMAIN_UID}.*server\"`" = "" ] \
     && [ "`kubectl -n ${NAMESPACE} get pods | grep \"${DOMAIN_UID}.*introspect\"`" = "" ] \
     && break
    sleep 1
  done
  echo
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
      2>&1 | tracePipe "Info: kubectl output: "
    rm -f ${test_home}/${yaml_file}
  fi

  # Apply template and create its k8s resource

  ${SCRIPTPATH}/util_subst.sh -g ${yaml_file}t ${test_home}/${yaml_file} || exit 1

  kubectl create -f ${test_home}/${yaml_file} \
    2>&1 | tracePipe "Info: kubectl output: " || exit 1
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
    2>&1 | tracePipe "Info: kubectl output: " || exit 1

  kubectl -n $NAMESPACE label cm ${cm_name} \
    weblogic.createdByOperator=true \
    weblogic.operatorName=look-ma-no-hands \
    weblogic.resourceVersion=domain-v2 \
    2>&1 | tracePipe "Info: kubectl output: " || exit 1
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
#   - this emulates what the operator pod would do
#   - contains the operator's introspect, nm, start server scripts, etc.
#   - mounted by create domain job, introspect job, and wl pods
#

function deployDomainConfigMap() {
  trace "Info: Deploying 'weblogic-domain-cm'."

  kubectl -n $NAMESPACE delete cm weblogic-domain-cm \
    --ignore-not-found  \
    2>&1 | tracePipe "Info: kubectl output: "

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

  cp ${SOURCEPATH}/operator/src/main/resources/scripts/utils* ${test_home}/test-scripts || exit 1
  cp ${SCRIPTPATH}/wl-create-domain-pod.sh ${test_home}/test-scripts || exit 1
  cp ${SCRIPTPATH}/createTestRoot.sh ${test_home}/test-scripts || exit 1
  cp ${SCRIPTPATH}/wl-introspect-pod.sh ${test_home}/test-scripts || exit 1

  export DATA_HOME=${DATA_HOME:-/shared/data}

  rm -f ${test_home}/test-scripts/wl-create-domain-pod.py
  ${SCRIPTPATH}/util_subst.sh -g wl-create-domain-pod.pyt ${test_home}/test-scripts/wl-create-domain-pod.py || exit 1

  kubectl -n $NAMESPACE delete cm test-script-cm \
    --ignore-not-found  \
    2>&1 | tracePipe "Info: kubectl output: "

  createConfigMapFromDir test-script-cm ${test_home}/test-scripts

}

#############################################################################
#
# Deploy custom override cm, just like a customer would
#


function deployCustomOverridesConfigMap() {
  local cmdir="${test_home}/customOverrides"
  local cmname="${DOMAIN_UID}-mycustom-overrides-cm"

  trace "Info: Setting up custom overrides map '$cmname' using directory '$cmdir'."

  mkdir -p $cmdir
  rm -f $cmdir/*.xml
  rm -f $cmdir/*.txt
  local bfilname dfilname filname
  for filname in override--*.xmlt override--*.txtt; do
     bfilname="`basename $filname`"
     bfilname="${bfilname/override--/}"
     bfilname="${bfilname/xmlt/xml}"
     bfilname="${bfilname/txtt/txt}"
     #echo $filname "+" $bfilname "+" ${cmdir}/${bfilname}
     #cp ${filname} ${cmdir}/${bfilname} || exit 1
     ${SCRIPTPATH}/util_subst.sh -g ${filname} ${cmdir}/${bfilname}  || exit 1
  done

  kubectl -n $NAMESPACE delete cm $cmname \
    --ignore-not-found  \
    2>&1 | tracePipe "Info: kubectl output: "

  createConfigMapFromDir $cmname $cmdir || exit 1
}


#############################################################################
#
# Create base directory for PV (uses a job)
#

function createTestRootPVDir() {
  trace "Info: PV_ROOT='$PV_ROOT'"

  # TBD on shared cluster/Jenkins PV_ROOT will differ and may already exist or be remote
  #     so we need to add logic/booleans to skip the following mkdir/chmod as needed

  mkdir -p ${PV_ROOT} || exit 1
  chmod 777 ${PV_ROOT} || exit 1

  trace "Info: Creating k8s cluster physical directory 'PV_ROOT/introspect/acceptance_test_pv/domain-${DOMAIN_UID}-storage' via 'kubectl run'."
  trace "Info: Test k8s resources use this physical directory via a PV/PVC '/shared' logical directory."

  ${SCRIPTPATH}/util_krun.sh -m ${PV_ROOT}:/pv-root \
                             -i ${WEBLOGIC_IMAGE_NAME}:${WEBLOGIC_IMAGE_TAG} \
                             -l ${WEBLOGIC_IMAGE_PULL_POLICY} \
                             -f ${SCRIPTPATH}/createTestRoot.sh \
                             -c "sh /tmpmount/createTestRoot.sh ${DOMAIN_UID}" \
                             || exit 1
}

#############################################################################
#
# Deploy WebLogic pv, pvc, & admin user/pass secret
#

function deployWebLogic_PV_PVC_and_Secret() {
  trace "Info: Deploying WebLogic domain's pv, pvc, & secret."

  deployYamlTemplate wl-pv.yamlt wl-pv.yaml
  deployYamlTemplate wl-pvc.yamlt wl-pvc.yaml
  deployYamlTemplate wl-secret.yamlt wl-secret.yaml
}

function deployMySQL() {
  trace "Info: Deploying MySQL secret, pv, pvc, & pod."
  # Create local custom mysql image that runs as 'oracle' user with uid/gid 1000/1000:
  docker build -t mysql:5.6o -f ${SCRIPTPATH}/Dockerfile.adduser . 2>&1 > ${test_home}/docker_build.out 2>&1
  if [ $? -ne 0 ]; then
    trace "Error: 'docker build -t mysql:5.6o -f ${SCRIPTPATH}/Dockerfile.adduser .' failed, results in ${test_home}/docker_build.out:"
    cat ${test_home}/docker_build.out
    exit 1
  fi
  deployYamlTemplate mysql.yamlt mysql.yaml
}

#############################################################################
#
# Run create domain "JobPod" - This is a pod that acts somewhat like a job
#

function deployCreateDomainJobPod() {
  trace "Info: Run create domain pod."

  local target_yaml=${test_home}/wl-create-domain-pod.yaml
  local pod_name=${DOMAIN_UID}-create-domain-pod

  # delete anything left over from a previous invocation of this function, assume all pods
  # have already been cleaned up

  rm -f ${target_yaml}

  trace "Info: Deploying job pod '$pod_name' and waiting for it to be ready."

  (
    export JOB_SCRIPT=/test-scripts/wl-create-domain-pod.sh
    ${SCRIPTPATH}/util_subst.sh -g wl-create-domain-pod.yamlt ${target_yaml}  || exit 1
  ) || exit 1

  kubectl create -f ${target_yaml} \
    2>&1 | tracePipe "Info: kubectl output: " || exit 1

  # Wait for pod to come up successfully

  waitForPod $pod_name
}

#############################################################################
#
# Run introspection "JobPod", parse its output to files, and put files in a cm
#   - this emulates what the operator pod would do prior to start wl-pods
#

# Here we emulate the introspect job by directly starting an introspect pod and monitoring it.
# (Running a pod directly is helpful for debugging.)

function deployIntrospectJobPod() {
  local introspect_output_cm_name=${DOMAIN_UID}-weblogic-domain-introspect-cm
  local target_yaml=${test_home}/wl-introspect-pod.yaml
  local pod_name=${DOMAIN_UID}--introspect-domain-pod

  trace "Info: Run introspection job, parse its output to files, and put files in configmap '$introspect_output_cm_name'."

  # delete anything left over from a previous invocation of this function, assume all pods
  # have already been cleaned up

  rm -f ${target_yaml}

  kubectl -n $NAMESPACE delete cm $introspect_output_cm_name \
    --ignore-not-found  \
    2>&1 | tracePipe "Info: kubectl output: "

  trace "Info: Deploying job pod '$pod_name' and waiting for it to be ready."

  (
    export JOB_NAME=${DOMAIN_UID}--introspect-domain-pod
    export JOB_SCRIPT=/test-scripts/wl-introspect-pod.sh
    export DATA_HOME=${DATA_HOME:-/shared/data}
    ${SCRIPTPATH}/util_subst.sh -g wl-introspect-pod.yamlt ${target_yaml}  || exit 1
  ) || exit 1

  kubectl create -f ${target_yaml} \
    2>&1 | tracePipe "Info: kubectl output: " || exit 1

  # Wait for pod to come up successfully

  waitForPod $pod_name

  # parse job pod's output files

  kubectl -n $NAMESPACE logs $pod_name > ${test_home}/job-${DOMAIN_UID}-introspect-domain-pod-job.out

  ${SCRIPTPATH}/util_fsplit.sh \
    ${test_home}/job-${DOMAIN_UID}-introspect-domain-pod-job.out \
    ${test_home}/jobfiles || exit 1

  # put the outputfile in a cm

  createConfigMapFromDir $introspect_output_cm_name ${test_home}/jobfiles


  # check domainValid value from domain introspector job output
  domainValid=`cat ${test_home}/jobfiles/topology.yaml | awk '/domainValid:/{sub(/.*domainValid: /, ""); print}'`

  if [ "$domainValid" = "false" ]; then
    if [ "$EXPECT_INVALID_DOMAIN" = "true" ]; then
      trace "Info: Success! domainValid is false as expected"
    else
      trace "Error: Exiting test due to domainValid from introspecting domain is false!"
    fi
    exit 1
  fi

  if [ "$EXPECT_INVALID_DOMAIN" = "true" ]; then
    trace "Exiting test due to domainValid from introspecting domain not returning false for an invalid domain"
    exit 1
  fi

}

#############################################################################
#
# Launch pod and wait up to 180 seconds for it to succeed, also launch
# services.
#   - this emulates what the operator pod would do after running the introspect job
#

function waitForPod() {
  local pod_name=${1?}
  local status="0/1"
  local startsecs=$SECONDS
  local maxsecs=180
  tracen "Info: Waiting up to $maxsecs seconds for pod '$pod_name' readiness"
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
    status=`kubectl -n $NAMESPACE get pods 2>&1 | egrep $pod_name | awk '{print $2}'`
  done
  echo "  ($((SECONDS - startsecs)) seconds)"
}

function deployPod() {
  local server_name=${1?}
  local pod_name=${DOMAIN_UID}-${server_name}
  local target_yaml=${test_home}/wl-${server_name}-pod.yaml

  trace "Info: Deploying pod '$pod_name'."

  # delete anything left over from a previous invocation of this function

  if [ -f "${target_yaml}" ]; then
    kubectl -n $NAMESPACE delete -f ${target_yaml} \
      --ignore-not-found \
      2>&1 | tracePipe "Info: kubectl output: "
    rm -f ${target_yaml}
  fi

  # Generate server pod yaml from template and deploy it

  (
    export SERVER_NAME=${server_name}
    export SERVICE_NAME=`toDNS1123Legal ${DOMAIN_UID}-${server_name}`
    export AS_SERVICE_NAME=`toDNS1123Legal ${DOMAIN_UID}-${ADMIN_NAME}`
    export DATA_HOME=${DATA_HOME:-/shared/data}
    if [ "${SERVER_NAME}" = "${ADMIN_NAME}" ]; then
      export LOCAL_SERVER_DEFAULT_PORT=$ADMIN_PORT
      export KEEP_DEFAULT_DATA_HOME="true"
      export EXPERIMENTAL_LINK_SERVER_DEFAULT_DATA_DIR=""
      export NODEMGR_MEM_ARGS="-Xms32m -Xmx200m -Djava.security.egd=file:/dev/./urandom"
    else
      export LOCAL_SERVER_DEFAULT_PORT=$MANAGED_SERVER_PORT
      export KEEP_DEFAULT_DATA_HOME=""
      export EXPERIMENTAL_LINK_SERVER_DEFAULT_DATA_DIR="true"
      export NODEMGR_MEM_ARGS=""
    fi
    ${SCRIPTPATH}/util_subst.sh -g wl-pod.yamlt ${target_yaml}  || exit 1
  ) || exit 1

  kubectl create -f ${target_yaml} \
    2>&1 | tracePipe "Info: kubectl output: " || exit 1
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
      2>&1 | tracePipe "Info: kubectl output: "
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
    2>&1 | tracePipe "Info: kubectl output: " || exit 1

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
# Check if automatic overrides and custom overrides took effect on the admin pod
#

function checkOverrides() {

  trace "Info: Checking admin server stdout to make sure situational config was loaded and there are no reported situational config errors."

  # Check for exactly 3 occurances of Info.*.BEA.*situational lines -- one for each file we're overriding.
  #   the awk expression below gets the tail of the log, everything after the last occurance of 'Starting WebLogic...'

  linecount="`kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${ADMIN_NAME} | awk '/.*Starting WebLogic server with command/ { buf = "" } { buf = buf "\n" $0 } END { print buf }' | grep -ci 'BEA.*situational'`"
  logstatus=0

  if [ "$linecount" != "5" ]; then
    trace "Error: The latest boot in 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${ADMIN_NAME}' does not contain exactly 5 lines that match ' grep 'BEA.*situational' ', this probably means that it's reporting situational config problems."
    logstatus=1
  fi

  #
  # Call on-line WLST on the admin-server to determine if overrides are
  # taking effect in the admin tree
  #

  trace "Info: Checking beans to see if sit-cfg took effect.  Input file '$test_home/checkBeans.input', output file '$test_home/checkBeans.out'."

  rm -f ${test_home}/checkBeans.input
  ${SCRIPTPATH}/util_subst.sh -g checkBeans.inputt ${test_home}/checkBeans.input || exit 1
  kubectl -n ${NAMESPACE} cp ${test_home}/checkBeans.input ${DOMAIN_UID}-${ADMIN_NAME}:/shared/checkBeans.input || exit 1
  kubectl -n ${NAMESPACE} cp ${SCRIPTPATH}/checkBeans.py ${DOMAIN_UID}-${ADMIN_NAME}:/shared/checkBeans.py || exit 1
  tracen "Info: Waiting for WLST checkBeans.py to complete."
  printdots_start
  # TBD weblogic/welcome1 should be deduced via a base64 of the admin secret
  kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${ADMIN_NAME} \
    wlst.sh /shared/checkBeans.py \
      weblogic welcome1 t3://${DOMAIN_UID}-${ADMIN_NAME}:${ADMIN_PORT} \
      /shared/checkBeans.input \
      > $test_home/checkBeans.out 2>&1
  status=$?
  printdots_end
  if [ $status -ne 0 ]; then
    trace "Error: The checkBeans verification failed, see '$test_home/checkBeans.out'."
  fi

  if [ $status -ne 0 ] || [ $logstatus -ne 0 ]; then
    exit 1
  fi
}

#############################################################################
#
# Check if wl version checks are working on the admin pod
#

function checkWLVersionChecks() {

  trace "Info: Checking pod log for 'error' strings."

  # Check for exactly 0 occurances of 'Error:' lines.
  #   a version issue is reported as an 'error:' to the log
  #   the awk expression below gets the tail of the log, everything after the last occurance of 'Starting WebLogic...'

  linecount="`kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${ADMIN_NAME} | awk '/.*Starting WebLogic server with command/ { buf = "" } { buf = buf "\n" $0 } END { print buf }' | grep -ci 'error:'`"
  logstatus=0

  if [ ! "$linecount" == "0" ]; then
    trace "Error: The latest boot in 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${ADMIN_NAME}' contains lines with the keyword 'error'."
    logstatus=1
  fi

  # Copy version test file up to admin server and run it

  local testscript="util_testwlversion.sh"
  local outfile="$test_home/${testscript}.out"

  trace "Info: Running version checks on admin-server, output file '$outfile'."

  kubectl -n ${NAMESPACE} \
    cp ${SCRIPTPATH}/${testscript} \
       ${DOMAIN_UID}-${ADMIN_NAME}:/shared/${testscript} \
    || exit 1

  rm -f ${outfile}
  kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${ADMIN_NAME} \
      /shared/${testscript} \
      > ${outfile} 2>&1
  status=$?

  if [ $status -ne 0 ]; then
    trace "Error: The version checks failed, see '${outfile}'."
  fi

  if [ $status -ne 0 ] || [ $logstatus -ne 0 ]; then
    exit 1
  fi
}

#############################################################################
#
# Check if datasource is working
#

function checkDataSource() {

  local pod_name=${1?}
  local admin_url=${2?}
  local wl_server_name=${3?}
  local data_source_name=${4?}
  local script_file=checkDataSource.py
  local out_file=$test_home/checkDataSource-${pod_name}-${data_source_name}.out

  local script_cmd="wlst.sh /shared/${script_file} ${admin_url} ${wl_server_name} ${data_source_name}"

  trace "Info: Checking datasource via '$script_cmd' on pod '$pod_name'."

  kubectl -n ${NAMESPACE} cp ${SCRIPTPATH}/${script_file} ${pod_name}:/shared/${script_file} || exit 1

  tracen "Info: Waiting for script to complete"
  printdots_start
  kubectl exec -it -n ${NAMESPACE} ${pod_name} ${script_cmd} > ${out_file} 2>&1
  status=$?
  printdots_end
  if [ $status -ne 0 ]; then
    trace "Error: The '$script_cmd' failed, see '$out_file'."
    exit 1
  fi
}

#############################################################################
#
# Check .DAT default and custom filestores created when overridden with
# a location specified by DATA_HOME environment variable
#

function checkFileStores() {

  # Copy file store test file up to admin server and run it

  local testscript=${1?}
  local server_name=${2?}
  local outfile="$test_home/${testscript}.out"

  trace "Info: Verifying .DAT file store checks for ${server_name}, output file '$outfile'."

  kubectl -n ${NAMESPACE} \
    cp ${SCRIPTPATH}/${testscript} \
       ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1:/shared/${testscript} \
    || exit 1

  rm -f ${outfile}
  kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
      /shared/${testscript} \
      > ${outfile} 2>&1
  status=$?

  if [ $status -ne 0 ]; then
    trace "Error: The version checks failed, see '${outfile}'."
  fi

  if [ $status -ne 0 ] || [ $logstatus -ne 0 ]; then
    exit 1
  fi
}

#############################################################################
#
# Validate NODEMGR_MEM_ARGS environment variable values (-Xms64m -Xmx100m)
# applied to Node Manager command line.
#

function checkNodeManagerMemArg() {

  trace "Verifying node manager memory arguments"

  # Verify that default NODEMGR_MEM_ARGS environment value (-Xms64m -Xmx100m) was applied to the Node Manager
  # command line when NODEMGR_MEM_ARGS was not defined.
  linecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "\-Xms64m -Xmx100m" /shared/logs/${MANAGED_SERVER_NAME_BASE?}1_nodemanager.out \
       | grep -v "NODEMGR_MEM_ARGS"  | wc -l`"
  logstatus=0

  if [ "$linecount" != "1" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1' does not contain exactly 1 line that match ' grep '-Xms64m -Xmx100m' ', this probably means that it's reporting NODEMGR_MEM_ARGS not applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi

  # Verify that NODEMGR_MEM_ARGS environment value (-Xms32m -Xmx200m) was applied to the Node Manager
  # command line, of the Admin Server pod, when NODEMGR_MEM_ARGS was explicitly defined.
  adminLinecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "\-Xms32m -Xmx200m" /shared/logs/${ADMIN_NAME?}_nodemanager.out \
       | grep -v "NODEMGR_MEM_ARGS"  | wc -l`"
  logstatus=0

  if [ "$adminLinecount" != "1" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${ADMIN-NAME?}' does not contain exactly 1 line that match ' grep '-Xms32m -Xmx200m' ', this probably means that it's reporting NODEMGR_MEM_ARGS not applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi

  # Verify that NODEMGR_MEM_ARGS environment value contains "-Djava.security.egd=file:/dev/./urandom" in the Node Manager
  # command line of the Managed Server pod.
  adminLinecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "urandom" /shared/logs/${MANAGED_SERVER_NAME_BASE?}1_nodemanager.out \
       | grep -v "NODEMGR_MEM_ARGS"  |  grep -v "JAVA_OPTIONS" | wc -l`"
  logstatus=0

  if [ "$adminLinecount" != "1" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${ADMIN-NAME?}' does not contain exactly 1 line that match ' grep '-Djava.security.egd=file:/dev/./urandom' ', this probably means that it's reporting NODEMGR_MEM_ARGS not applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi

  # Verify that USER_MEM_ARGS environment value did not get applied to the Node Manager command line
  maxRamlinecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "MaxRAMFraction=1" /shared/logs/${MANAGED_SERVER_NAME_BASE?}1_nodemanager.out \
       | grep -v "JAVA_OPTIONS" | wc -l`"
  logstatus=0

  if [ "$maxRamlinecount" != "0" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1' does not contain exactly 0 lines that match ' grep 'MaxRAMFraction=1' ', this probably means that it's reporting USER_MEM_ARGS was applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi
}

#############################################################################
#
# Validate USER_MEM_ARGS environment variable values (-MaxRAMFraction)
# applied to Managed Server command line.
#
function checkManagedServer1MemArg() {

  trace "Verifying managed server memory arguments"

  # Verify that USER_MEM_ARGS environment value was applied to the Managed Server 1 command line
  maxRamlinecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "MaxRAMFraction=1"  /shared/logs/${MANAGED_SERVER_NAME_BASE?}1.out \
       | grep -v "JAVA_OPTIONS" | wc -l`"
  logstatus=0

  if [ "$maxRamlinecount" != "1" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1' does not contain exactly 1 line that match ' grep 'MaxRAMFraction=1' ', this probably means that it's reporting USER_MEM_ARGS not applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi

  # Verify that NODEMGR_MEM_ARGS environment value did not get applied to the Managed Server 1 command line
  linecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "\-Xms64m -Xmx100m" /shared/logs/${MANAGED_SERVER_NAME_BASE?}1.out \
       | grep -v "NODEMGR_MEM_ARGS"  | wc -l`"
  logstatus=0

  if [ "$linecount" != "0" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1' does not contain exactly 0 lines that match ' grep '-Xms64m -Xmx100m' ', this probably means that it's reporting NODEMGR_MEM_ARGS was applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi
}

#############################################################################
#
# Validate NODEMGR_JAVA_OPTIONS environment variable values (-Dnodemgr.java.options)
# applied to Node Manager command line.
#

function checkNodeManagerJavaOptions() {

  trace "Verifying node manager java options"

  # Verify that NODEMGR_JAVA_OPTIONS environment value was applied to the Node Manager command line
  nodeMgrlinecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "\-Dnodemgr.java.options" /shared/logs/${MANAGED_SERVER_NAME_BASE?}1_nodemanager.out \
       | grep -v "NODEMGR_JAVA_OPTIONS"  | wc -l`"
  logstatus=0

  if [ "$nodeMgrlinecount" != "1" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1' does not contain exactly 1 line that match ' grep '-Dnodemgr.java.options' ', this probably means that it's reporting NODEMGR_JAVA_OPTIONS not applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi

  # Verify that NODEMGR_JAVA_OPTIONS environment value did not get applied to the Managed Server command line
  nmJavaOptlinecount="`kubectl exec -it -n ${NAMESPACE} ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1 \
       grep "\-Dnodemgr.java.options" /shared/logs/${MANAGED_SERVER_NAME_BASE?}1.out \
       | grep -v "NODEMGR_JAVA_OPTIONS" | wc -l`"
  logstatus=0

  if [ "$nmJavaOptlinecount" != "0" ]; then
    trace "Error: The latest log from 'kubectl -n ${NAMESPACE} logs ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1' does not contain exactly 0 lines that match ' grep 'M-Dnodemgr.java.options' ', this probably means that it's reporting NODEMGR_JAVA_OPTIONS was applied"
    logstatus=1
  fi

  if [ $logstatus -ne 0 ]; then
    exit 1
  fi
}


#############################################################################
#
# Main
#

if [ ! "$RERUN_INTROSPECT_ONLY" = "true" ]; then
  # retry on failure
  cleanupMajor || cleanupMajor || exit 1
else
  # This path assumes we've already run the test succesfully once, it re-uses
  # the existing domain-home/pv/pvc/secret/etc, deletes wl pods, deletes introspect job, then
  # redeploys the custom overrides, reruns the introspect job, and restarts the admin server pod.
  cleanupMinor
fi

deployDomainConfigMap
deployTestScriptConfigMap
deployCustomOverridesConfigMap

kubectl -n $NAMESPACE delete secret my-secret > /dev/null 2>&1
kubectl -n $NAMESPACE create secret generic my-secret \
        --from-literal=key1=supersecret  \
        --from-literal=key2=topsecret 2>&1 | tracePipe "Info: kubectl output: "

if [ ! "$RERUN_INTROSPECT_ONLY" = "true" ]; then
  createTestRootPVDir
  deployMySQL
  deployWebLogic_PV_PVC_and_Secret
  deployCreateDomainJobPod
fi

deployIntrospectJobPod

#
# TBD ADMIN_NAME, ADMIN_PORT, and MANAGED_SERVER_NAME_BASE, etc env vars
#     should be checked to see if topology file the introspector generated
#     matches
#

deployPod ${ADMIN_NAME?}
deploySinglePodService ${ADMIN_NAME?} ${ADMIN_PORT?} 30701
waitForPod ${DOMAIN_UID}-${ADMIN_NAME?}

deployPod ${MANAGED_SERVER_NAME_BASE?}1
deploySinglePodService ${MANAGED_SERVER_NAME_BASE?}1 ${MANAGED_SERVER_PORT?} 30801
waitForPod ${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE?}1

# Check admin-server pod log for wl version errors and run some
# tests on the life-cycle WL version checking code.

checkWLVersionChecks

# Check admin-server pod log and also call on-line WLST to check if
# automatic and custom overrides are taking effect in the bean tree:

checkOverrides

# Check DS to see if it can contact the DB.  This will only pass if the
# overrides actually took effect:

checkDataSource ${DOMAIN_UID}-${ADMIN_NAME?} t3://${DOMAIN_UID}-${ADMIN_NAME}:${ADMIN_PORT} ${ADMIN_NAME?} mysqlDS

# Verify default and custom file stores were created for admin-server
checkFileStores util_test_adminfilestores.sh ${ADMIN_NAME}

# Verify default file store was created for managed-server1
checkFileStores util_test_ms1filestores.sh ${MANAGED_SERVER_NAME_BASE?}1

# Verify node manager memory args
checkNodeManagerMemArg

# Verify Managed Server memory args
checkManagedServer1MemArg

# Verify node manager java options
checkNodeManagerJavaOptions

trace "Info: Success!"
