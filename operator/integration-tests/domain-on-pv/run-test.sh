#!/bin/bash
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a stand-alone test for testing the DPV sample.
#
# Note! This is called by integration test 'ItWlsSamples.java'.
#
# See "usage()" below for usage.
#

set -eu
set -o pipefail
trap 'status=$? ; set +eu ; set +o pipefail ; kill $(jobs -pr) > /dev/null 2>&1 ; exit $status' SIGINT SIGTERM EXIT

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

source $TESTDIR/util-dots.sh
source $TESTDIR/util-misc.sh
source $TESTDIR/test-env.sh

trace "Running end to end DPV sample test."
echo "Is OKD set? $OKD"
echo "Is OKE_CLUSTER set? $OKE_CLUSTER"
echo "Is KIND_CLUSTER set? $KIND_CLUSTER"

DRY_RUN=false
DO_CLEANUP=false
DO_CLEANDB=false
DO_DB=false
DO_ASSUME_DB=false
DO_RCU=false
DO_OPER=false
DO_TRAEFIK=false
DO_CHECK_SAMPLE=false
DO_INITIAL_IMAGE=false
DO_INITIAL_MAIN=false
WDT_DOMAIN_TYPE=WLS

usage() {
  cat << EOF

  Usage: $(basename $0)

  Commonly tuned env vars and their defaults:

    POD_WAIT_TIMEOUT_SECS           : 1000 (max wait time for a domain to start)
    INTROSPECTOR_DEADLINE_SECONDS   : 600  (for JRF runs)
    WORKDIR                         : /tmp/\$USER/dpv-sample-work-dir
    DOMAIN_NAMESPACE                : sample-domain1-ns
    DOMAIN_CREATION_IMAGE_NAME      : wdt-domain-image
    DOMAIN_CREATION_IMAGE_TAG       : Defaults to JRF-v1, JRF-v2, WLS-v1, or WLS-v2 depending on use-case and domain type.
                            IMPORTANT: If setting this env var, do not run multiple use cases on the same command line,
                            as then all use cases will use the same image.
    IMAGE_PULL_SECRET_NAME          : (not set)
    DOMAIN_IMAGE_PULL_SECRET_NAME   : (not set)
    DB_NAMESPACE                    : default (used by -db and -rcu)
    DB_IMAGE_PULL_SECRET            : repo secret (used by -db and -rcu)
    TRAEFIK_NAMESPACE               : traefik-operator-ns (used by -traefik and by tests)
    TRAEFIK_HTTP_NODEPORT           : 30305 (used by -traefik and by tests, can be 0 to dynamically choose)
    TRAEFIK_HTTPS_NODEPORT          : 30433 (used by -traefik, can be 0 to dynamically choose)
    OPER_NAMESPACE                  : sample-weblogic-operator-ns (used by -oper)
    BASE_IMAGE_NAME                 : Base WebLogic Image
    BASE_IMAGE_TAG                  : Base WebLogic Image Tag

    (see test-env.sh for full list)

  Misc:

    -dry      : Dry run - show but don't do.
    -?        : This help.
    -jrf      : Run in JRF mode instead of WLS mode. 
                Note that this depends on the db being deployed
                and initialized via rcu. So either pre-deploy
                the db and run rcu or pass '-db' and '-rcu' too.
    -ai       : Run the tests in auxiliary images mode.
    -assume-db: Assume Oracle DB is running.
                If set, then the 'update4' test will include DB tests.
                Defaults to true for '-jrf' or '-db'.
                See 'test-env.sh' for DB settings.

  Precleanup (occurs first):

    -precleanup : Call cleanup.sh, pre-delete DPV image
                '\$DOMAIN_CREATION_IMAGE_NAME:*', and delete WORKDIR.
    -precleandb : Deletes db leftover from running -db.


  Deploying prerequisites:

    -oper     : Build and deploy Operator. This
                operator will monitor '\$DOMAIN_NAMESPACE'
                which defaults to 'sample-domain1-ns'.
    -traefik  : Deploy Traefik. This will monitor
                'DOMAIN_NAMESPACE' which defaults 
                to 'sample-domain1-ns', and open port 30305.
    -db       : Deploy Oracle DB. A DB is needed for JRF mode
                and optionally for 'update4'.
                See 'test-env.sh' for DB settings.
                See also '-assume-db'.
    -rcu      : Initialize FMWdomain1 and FMWdomain2 schemas
                in the DB. Needed for JRF.
                See 'test-env.sh' for DB settings.

  Tests:

    -check-sample : Generate sample and verify that this matches the source
                    checked into the dpv sample git location.

    -initial-image: Build image required for initial use case.
                    Image is named '\$DOMAIN_CREATION_IMAGE_NAME:WLS-v1' or '...:JRF-v1' if DOMAIN_CREATION_IMAGE_TAG not specified.
                    Image is named '\$DOMAIN_CREATION_IMAGE_NAME:\$DOMAIN_CREATION_IMAGE_TAG' if DOMAIN_CREATION_IMAGE_TAG is specified.

    -initial-main : Deploy initial use case (domain resource, secrets, etc).
                    Domain uid 'sample-domain1'.
                    Depends on '-initial-image'.

    -all          : All of the above tests.

EOF
}

while [ ! -z "${1:-}" ]; do
  case "${1}" in
    -dry)            DRY_RUN="true" ;;
    -precleanup)     DO_CLEANUP="true" ;;
    -precleandb)     DO_CLEANDB="true" ;;
    -db)             DO_DB="true" 
                     DO_ASSUME_DB="true"
                     ;;
    -assume-db)      DO_ASSUME_DB="true" ;;
    -oper)           DO_OPER="true" ;;
    -traefik)        DO_TRAEFIK="true" ;;
    -jrf)            WDT_DOMAIN_TYPE="JRF"
                     DO_ASSUME_DB="true"
                     ;;
    -rcu)            DO_RCU="true" ;;
    -check-sample)   DO_CHECK_SAMPLE="true" ;;
    -initial-image)  DO_INITIAL_IMAGE="true" ;;
    -initial-main)   DO_INITIAL_MAIN="true" ;;
    -all)            DO_CHECK_SAMPLE="true" 
                     DO_INITIAL_IMAGE="true" 
                     DO_INITIAL_MAIN="true" 
                     ;;  
    -?)              usage; exit 0; ;;
    *)               trace "Error: Unrecognized parameter '${1}', pass '-?' for usage."; exit 1; ;;
  esac
  shift
done

# We check that WORKDIR ends in "domain-on-pv-sample-work-dir" as a safety feature.
# (We're going to 'rm -fr' this directory, and its safer to do that without relying on env vars).

bname=$(basename $WORKDIR)
if [ ! "$bname" = "domain-on-pv-sample-work-dir" ] \
   && [ ! "$bname" = "dpv-sample" ] ; then
  trace "Error: This test requires WORKDIR to end in 'dpv-sample' or 'domain-on-pv-sample-work-dir'. WORKDIR='$WORKDIR'."
  exit 1
fi

#
# Helper script ($1 == number of pods)
#

waitForDomain() {
  local wcmd="\$SRCDIR/kubernetes/samples/scripts/domain-lifecycle/waitForDomain.sh"
  local wargs="-p $1 -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE -t \$POD_WAIT_TIMEOUT_SECS -i"

  if [ "$1" = "0" ]; then
    doCommand -c "$wcmd $wargs -q"
  else
    doCommand    "$wcmd $wargs"
  fi
}

# 
# Clean
#

if [ "$DO_CLEANUP" = "true" ]; then
  doCommand -c "echo ====== CLEANUP ======"

  doCommand -c mkdir -p \$WORKDIR
  doCommand -c cd \$WORKDIR/..
  if [ "$bname" = "dpv-sample" ]; then
    doCommand -c rm -fr ./dpv-sample
  else
    doCommand -c rm -fr ./domain-on-pv-sample-work-dir
  fi
  doCommand    "\$SRCDIR/operator/integration-tests/bash/cleanup.sh"

  # delete domain creation images, if any, and dangling images
  for m_image in \
    "${DOMAIN_CREATION_IMAGE_NAME:-domain-on-pv}:${DOMAIN_CREATION_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v1}" \
    "${DOMAIN_CREATION_IMAGE_NAME:-domain-on-pv}:${DOMAIN_CREATION_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v2}" 
  do
    if [ ! "$DRY_RUN" = "true" ]; then
      if [ ! -z "$(${WLSIMG_BUILDER:-docker} images -q $m_image)" ]; then
        trace "Info: Forcing domain creation image rebuild by removing old image '$m_image'!"
        ${WLSIMG_BUILDER:-docker} image rm $m_image
      fi
    else
      echo "dryrun: [ ! -z "$(${WLSIMG_BUILDER:-docker} images -q $m_image)" ] && ${WLSIMG_BUILDER:-docker} image rm $m_image"
    fi
  done
fi

if [ "$DO_CLEANDB" = "true" ]; then
  doCommand -c "echo ====== CLEANDB ======"
  doCommand -c "${KUBERNETES_CLI:-kubectl} -n $DB_NAMESPACE delete deployment oracle-db --ignore-not-found"
  doCommand -c "${KUBERNETES_CLI:-kubectl} -n $DB_NAMESPACE delete service oracle-db --ignore-not-found"
  doCommand -c "${KUBERNETES_CLI:-kubectl} -n $DB_NAMESPACE delete secret oracle-db-secret --ignore-not-found"
fi

# 
# Env var pre-reqs
#

doCommand -c "echo ====== SETUP ======"
doCommand -c set -e
doCommand -c SRCDIR=$SRCDIR
doCommand -c TESTDIR=$TESTDIR
doCommand -c DPVSAMPLEDIR=$DPVSAMPLEDIR
doCommand -c DPVWRAPPERDIR=$DPVWRAPPERDIR
doCommand -c DBSAMPLEDIR=$DBSAMPLEDIR
doCommand -c POD_WAIT_TIMEOUT_SECS=$POD_WAIT_TIMEOUT_SECS
doCommand -c source \$TESTDIR/test-env.sh
doCommand -c export WORKDIR=$WORKDIR
doCommand -c export WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE
doCommand -c export DOMAIN_NAMESPACE=$DOMAIN_NAMESPACE
doCommand -c mkdir -p \$WORKDIR
doCommand -c mkdir -p \$WORKDIR/domain-on-pv
doCommand -c mkdir -p \$WORKDIR/wdt-artifacts
doCommand -c mkdir -p \$WORKDIR/ingresses
doCommand -c cp -r \$DPVSAMPLEDIR/* \$WORKDIR/domain-on-pv
doCommand -c cp -r \$DPVSAMPLEDIR/../wdt-artifacts/* \$WORKDIR/wdt-artifacts
doCommand -c cp -r \$DPVSAMPLEDIR/../ingresses/* \$WORKDIR/ingresses
doCommand -c export OKD=$OKD
doCommand -c export OKE_CLUSTER=$OKE_CLUSTER
doCommand -c export KIND_CLUSTER=$KIND_CLUSTER

#
# Build pre-req (operator)
#

if [ "$DO_OPER" = "true" ]; then
  doCommand -c "echo ====== OPER BUILD ======"
  doCommand  "\$TESTDIR/build-operator.sh"
  if [ "$KIND_CLUSTER" = "true" ]; then
      doCommand -c "kind load docker-image ${OPER_IMAGE_NAME:-weblogic-kubernetes-operator}:${OPER_IMAGE_TAG:-test} --name kind"
  fi
fi

#
# Deploy pre-reqs (db, rcu schema, traefik, operator)
#

if [ "$DO_DB" = "true" ]; then
  doCommand -c "echo ====== DB DEPLOY ======"
  if [ "$OKD" = "true" ]; then
    doCommand -c "echo adding scc to the DB namespace \$DB_NAMESPACE "
    doCommand -c "oc adm policy add-scc-to-user anyuid -z default -n \$DB_NAMESPACE"
  fi

  echo "@@ Info: Creating db sys secret"
  # password must match the value specified in ./dpv-sample-wrapper/create-secrets.sh
  secCommand="\$WORKDIR/domain-on-pv/utils/create-secret.sh -s oracle-db-secret -d \$DOMAIN_UID1 -n \$DB_NAMESPACE"
  secCommand+=" -l \"password=Oradoc_db1\""
  doCommand "$secCommand" | sed 's/word=\([^"]*\)/word=***/g'

  doCommand  "\$DBSAMPLEDIR/stop-db-service.sh -n \$DB_NAMESPACE"
  if [ ! -z "$DB_IMAGE_PULL_SECRET" ]; then
    doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n \$DB_NAMESPACE -i \$DB_IMAGE_NAME:\$DB_IMAGE_TAG -p \$DB_NODE_PORT -s \$DB_IMAGE_PULL_SECRET"
  else
    doCommand  "\$DBSAMPLEDIR/start-db-service.sh -n \$DB_NAMESPACE -i \$DB_IMAGE_NAME:\$DB_IMAGE_TAG -p \$DB_NODE_PORT"
  fi
fi

if [ "$DO_RCU" = "true" ]; then

  doCommand -c "echo ====== DB RCU: Creating RCU setup secret"

  # sys_password and password must match the values specified in ./dpv-sample-wrapper/create-secrets.sh
  secCommand="\$WORKDIR/domain-on-pv/utils/create-secret.sh -s oracle-rcu-secret -d \$DOMAIN_UID1 -n \$DB_NAMESPACE"
  secCommand+=" -l \"sys_username=sys\""
  secCommand+=" -l \"sys_password=Oradoc_db1\""
  secCommand+=" -l \"password=Oradoc_db1\""
  doCommand "$secCommand" | sed 's/word=\([^"]*\)/word=***/g'

  defaultBaseImage="container-registry.oracle.com/middleware/fmw-infrastructure"
  BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-$defaultBaseImage}"
  BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.4}

  # delete old rcu pod in case one is already running from an old test run
  doCommand "${KUBERNETES_CLI:-kubectl} -n $DB_NAMESPACE delete pod rcu --ignore-not-found"

  for _custom_domain_name_ in domain1 domain2
  do

  doCommand -c "echo ====== DB RCU Schema Init for domain $_custom_domain_name_ ======"
  doCommand -c "cd \$SRCDIR/kubernetes/samples/scripts/create-rcu-schema"

  rcuCommand="./create-rcu-schema.sh"
  rcuCommand+=" -d oracle-db.\$DB_NAMESPACE.svc.cluster.local:1521/devpdb.k8s" # DB url
  rcuCommand+=" -s FMW$_custom_domain_name_"    # RCU schema prefix
  if [ ! -z "$DB_IMAGE_PULL_SECRET" ]; then
    rcuCommand+=" -p \$DB_IMAGE_PULL_SECRET"   # FMW infra image pull secret for rcu pod
  fi
  rcuCommand+=" -i ${BASE_IMAGE_NAME}:${BASE_IMAGE_TAG}"  # image for rcu pod
  rcuCommand+=" -n \$DB_NAMESPACE"             # namespace to run rcu pod
  rcuCommand+=" -o \$WORKDIR/rcuoutput_${_custom_domain_name_}" # output directory for generated YAML
  doCommand "$rcuCommand"

  done

  # The rcu command leaves a pod named 'rcu' running:
  doCommand "${KUBERNETES_CLI:-kubectl} -n $DB_NAMESPACE delete pod rcu --ignore-not-found"
  doCommand -c "${KUBERNETES_CLI:-kubectl} -n $DB_NAMESPACE delete secret oracle-rcu-secret --ignore-not-found"

fi

if [ "$DO_OPER" = "true" ]; then
  doCommand -c "echo ====== OPER DEPLOY ======"
  doCommand  "\$TESTDIR/deploy-operator.sh"
fi

if [ "$DO_TRAEFIK" = "true" ] && [ "$OKD" = "false" ]; then
  doCommand -c "echo ====== TRAEFIK DEPLOY ======"
  doCommand  "\$TESTDIR/deploy-traefik.sh"
fi

if [ "$DO_CHECK_SAMPLE" = "true" ]; then
  doCommand -c "echo ====== GEN SOURCE AND CHECK MATCHES GIT SOURCE ======"
  doCommand  "export GENROOTDIR=\$WORKDIR/generated-sample"
  doCommand -c cd \$WORKDIR
  doCommand -c rm -fr ./generated-sample
  doCommand  "\$DPVWRAPPERDIR/generate-sample-doc.sh" 
fi

#
# Deploy initial domain, wait for its pods to be ready, and test its cluster app
#

if [ "$DO_INITIAL_IMAGE" = "true" ]; then
  doCommand -c "echo ====== USE CASE: INITIAL-IMAGE ======"

  doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}"
  doCommand -c "export OKD=${OKD}"
  doCommand    "\$DPVWRAPPERDIR/stage-tooling.sh"
  doCommand    "\$DPVWRAPPERDIR/build-wdt-domain-image.sh"
fi

if [ "$DO_INITIAL_MAIN" = "true" ]; then
  doCommand -c "echo ====== USE CASE: INITIAL-MAIN ======"

  doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}"
  doCommand -c "export DOMAIN_UID=$DOMAIN_UID1"
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/dpv-initial.yaml"
  doCommand -c "export INCLUDE_CONFIGMAP=false"
  doCommand -c "export CORRECTED_DATASOURCE_SECRET=false"

  dumpInfo

  doCommand    "\$DPVWRAPPERDIR/stage-domain-resource.sh"
  doCommand    "\$DPVWRAPPERDIR/create-secrets.sh"
  if [ "$OKD" = "false" ]; then
    doCommand    "\$DPVWRAPPERDIR/stage-and-create-ingresses.sh"
  fi

  doCommand -c "${KUBERNETES_CLI:-kubectl} -n \$DOMAIN_NAMESPACE delete domain \$DOMAIN_UID --ignore-not-found"
  waitForDomain 0

  doCommand -c "${KUBERNETES_CLI:-kubectl} apply -f \$WORKDIR/\$DOMAIN_RESOURCE_FILENAME"
  waitForDomain Completed

  if [ "$OKD" = "true" ]; then
    # expose the cluster service as an route
    doCommand -c "oc expose service \${DOMAIN_UID}-cluster-cluster-1 -n \$DOMAIN_NAMESPACE"
    routeHost=$(getRouteHost "${DOMAIN_UID}-cluster-cluster-1")
    echo $routeHost
    doCommand -c export ROUTE_HOST=${routeHost}
    sleep 10
  fi

  if [ ! "$DRY_RUN" = "true" ]; then
    diefast # (cheat to speedup a subsequent roll/shutdown)
    testapp internal cluster-1 "Hello World!"
    if [ "$OKD" = "true" ]; then
      testapp OKD  cluster-1 "Hello World!" 
    else
      testapp traefik  cluster-1 "Hello World!"
    fi
  fi

  dumpInfo
fi

trace "Woo hoo! Finished without errors! Total runtime $SECONDS seconds."

# TBD Add JRF wallet export/import testing?  There's another test that already tests the sample's import/export script.
