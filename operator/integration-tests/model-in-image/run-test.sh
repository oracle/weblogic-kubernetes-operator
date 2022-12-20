#!/bin/bash
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This is a stand-alone test for testing the MII sample.
#
# Note! This is called by integration test 'ItMiiSample.java'.
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

trace "Running end to end MII sample test."
echo "Is OKD set? $OKD"
echo "Is OKE_CLUSTER set? $OKE_CLUSTER"

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
DO_UPDATE1=false
DO_UPDATE2=false
DO_UPDATE3_IMAGE=false
DO_UPDATE3_MAIN=false
DO_UPDATE4=false
DO_AI=${DO_AI:-false}
WDT_DOMAIN_TYPE=WLS

usage() {
  cat << EOF

  Usage: $(basename $0)

  Commonly tuned env vars and their defaults:

    POD_WAIT_TIMEOUT_SECS : 1000 (max wait time for a domain to start)
    INTROSPECTOR_DEADLINE_SECONDS: 600  (for JRF runs)
    WORKDIR               : /tmp/\$USER/mii-sample-work-dir
    DOMAIN_NAMESPACE      : sample-domain1-ns
    MODEL_IMAGE_NAME      : model-in-image 
    MODEL_IMAGE_TAG       : Defaults to JRF-v1, JRF-v2, WLS-v1, or WLS-v2 depending on use-case and domain type.
                            IMPORTANT: If setting this env var, do not run multiple use cases on the same command line,
                            as then all use cases will use the same image.
    IMAGE_PULL_SECRET_NAME: (not set)
    DOMAIN_IMAGE_PULL_SECRET_NAME: (not set)
    DB_NAMESPACE          : default (used by -db and -rcu)
    DB_IMAGE_PULL_SECRET  : repo secret (used by -db and -rcu)
    TRAEFIK_NAMESPACE     : traefik-operator-ns (used by -traefik and by tests)
    TRAEFIK_HTTP_NODEPORT : 30305 (used by -traefik and by tests, can be 0 to dynamically choose)
    TRAEFIK_HTTPS_NODEPORT: 30433 (used by -traefik, can be 0 to dynamically choose)
    OPER_NAMESPACE        : sample-weblogic-operator-ns (used by -oper)
    BASE_IMAGE_NAME       : Base WebLogic Image
    BASE_IMAGE_TAG        : Base WebLogic Image Tag
    DO_AI                 : false (run the tests in auxiliary image mode)

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

    -precleanup : Call cleanup.sh, pre-delete MII image
                '\$MODEL_IMAGE_NAME:*', and delete WORKDIR.
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
                    checked into the mii sample git location.

    -initial-image: Build image required for initial use case.
                    Image is named '\$MODEL_IMAGE_NAME:WLS-v1' or '...:JRF-v1' if MODEL_IMAGE_TAG not specified.
                    Image is named '\$MODEL_IMAGE_NAME:\$MODEL_IMAGE_TAG' if MODEL_IMAGE_TAG is specified.

    -initial-main : Deploy initial use case (domain resource, secrets, etc).
                    Domain uid 'sample-domain1'.
                    Depends on '-initial-image'.

    -update1      : Deploy update1 use case (add data source to initial via configmap).
                    Domain uid 'sample-domain1'.
                    Rolls 'sample-domain1' if already running.
                    Depends on '-initial-image'.

    -update2      : Run update2 use case (deploy second domain similar to -update1).
                    Domain uid 'sample-domain2'.
                    Depends on '-initial-image'.
                    Depends on '-initial-main' (calls its app).

    -update3-image: Build image required for update3 use case.
                    Image is named '\$MODEL_IMAGE_NAME:WLS-v2' or '...:JRF-v2' if MODEL_IMAGE_TAG is not set.
                    And is named '\$MODEL_IMAGE_NAME:\$MODEL_IMAGE_TAG' if MODEL_IMAGE_TAG is set.'

    -update3-main : Run update3 use case (update initial domain's app via new image).
                    Domain uid 'sample-domain1'.
                    Depends on '-update3-image'.
                    Rolls 'sample-domain1' if already running.

    -update4      : Run update4 use case
                    (Online config update of work manager and data source.)
                    Domain uid 'sample-domain1'.
                    Deploys updated configmap and db secret.
                    Should _not_ roll 'sample-domain1' if already running.
                    Tests if datasource can contact db if also -assume-db, -jrf, or -db.
                    Depends on '-initial-image' and '-initial-main'.

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
    -ai)             DO_AI="true" ;;
    -update1)        DO_UPDATE1="true" ;;
    -update2)        DO_UPDATE2="true" ;;
    -update3-image)  DO_UPDATE3_IMAGE="true" ;;  
    -update3-main)   DO_UPDATE3_MAIN="true" ;;  
    -update4)        DO_UPDATE4="true" ;;
    -all)            DO_CHECK_SAMPLE="true" 
                     DO_INITIAL_IMAGE="true" 
                     DO_INITIAL_MAIN="true" 
                     DO_UPDATE1="true" 
                     DO_UPDATE2="true" 
                     DO_UPDATE3_IMAGE="true" 
                     DO_UPDATE3_MAIN="true" 
                     DO_UPDATE4="true" 
                     ;;  
    -?)              usage; exit 0; ;;
    *)               trace "Error: Unrecognized parameter '${1}', pass '-?' for usage."; exit 1; ;;
  esac
  shift
done

# We check that WORKDIR ends in "model-in-image-sample-work-dir" as a safety feature.
# (We're going to 'rm -fr' this directory, and its safer to do that without relying on env vars).

bname=$(basename $WORKDIR)
if [ ! "$bname" = "model-in-image-sample-work-dir" ] \
   && [ ! "$bname" = "mii-sample" ] ; then
  trace "Error: This test requires WORKDIR to end in 'mii-sample' or 'model-in-image-sample-work-dir'. WORKDIR='$WORKDIR'."
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
  if [ "$bname" = "mii-sample" ]; then
    doCommand -c rm -fr ./mii-sample
  else
    doCommand -c rm -fr ./model-in-image-sample-work-dir
  fi
  doCommand    "\$SRCDIR/operator/integration-tests/bash/cleanup.sh"

  # delete model images, if any, and dangling images
  for m_image in \
    "${MODEL_IMAGE_NAME:-model-in-image}:${MODEL_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v1}" \
    "${MODEL_IMAGE_NAME:-model-in-image}:${MODEL_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v2}" 
  do
    if [ ! "$DRY_RUN" = "true" ]; then
      if [ ! -z "$(${WLSIMG_BUILDER:-docker} images -q $m_image)" ]; then
        trace "Info: Forcing model image rebuild by removing old image '$m_image'!"
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
doCommand -c MIISAMPLEDIR=$MIISAMPLEDIR
doCommand -c MIIWRAPPERDIR=$MIIWRAPPERDIR
doCommand -c DBSAMPLEDIR=$DBSAMPLEDIR
doCommand -c POD_WAIT_TIMEOUT_SECS=$POD_WAIT_TIMEOUT_SECS
doCommand -c source \$TESTDIR/test-env.sh
doCommand -c export WORKDIR=$WORKDIR
doCommand -c export WDT_DOMAIN_TYPE=$WDT_DOMAIN_TYPE
doCommand -c export DOMAIN_NAMESPACE=$DOMAIN_NAMESPACE
doCommand -c mkdir -p \$WORKDIR
doCommand -c cp -r \$MIISAMPLEDIR/* \$WORKDIR
doCommand -c export OKD=$OKD
doCommand -c export OKE_CLUSTER=$OKE_CLUSTER

#
# Build pre-req (operator)
#

if [ "$DO_OPER" = "true" ]; then
  doCommand -c "echo ====== OPER BUILD ======"
  doCommand  "\$TESTDIR/build-operator.sh" 
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
  # password must match the value specified in ./mii-sample-wrapper/create-secrets.sh
  secCommand="\$WORKDIR/utils/create-secret.sh -s oracle-db-secret -d \$DOMAIN_UID1 -n \$DB_NAMESPACE"
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

  # sys_password and password must match the values specified in ./mii-sample-wrapper/create-secrets.sh
  secCommand="\$WORKDIR/utils/create-secret.sh -s oracle-rcu-secret -d \$DOMAIN_UID1 -n \$DB_NAMESPACE"
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
  doCommand "export DO_AI=$DO_AI"
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
  doCommand  "\$MIIWRAPPERDIR/generate-sample-doc.sh" 
fi

#
# Deploy initial domain, wait for its pods to be ready, and test its cluster app
#

if [ "$DO_INITIAL_IMAGE" = "true" ]; then
  doCommand -c "echo ====== USE CASE: INITIAL-IMAGE ======"

  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
  fi
  doCommand -c "export OKD=${OKD}"
  doCommand    "\$MIIWRAPPERDIR/stage-tooling.sh"
  doCommand    "\$MIIWRAPPERDIR/build-model-image.sh"
fi

if [ "$DO_INITIAL_MAIN" = "true" ]; then
  doCommand -c "echo ====== USE CASE: INITIAL-MAIN ======"

  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
  fi
  doCommand -c "export DOMAIN_UID=$DOMAIN_UID1"
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-initial.yaml"
  doCommand -c "export INCLUDE_CONFIGMAP=false"
  doCommand -c "export CORRECTED_DATASOURCE_SECRET=false"

  dumpInfo

  doCommand    "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand    "\$MIIWRAPPERDIR/create-secrets.sh"
  if [ "$OKD" = "false" ]; then
    doCommand    "\$MIIWRAPPERDIR/stage-and-create-ingresses.sh"
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

#
# Add datasource to the running domain, patch its
# restart version, wait for its pods to roll, and use
# the test app to verify that the datasource deployed.
#

if [ "$DO_UPDATE1" = "true" ]; then
  doCommand -c "echo ====== USE CASE: UPDATE1 ======"

  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
  fi
  doCommand -c "export DOMAIN_UID=$DOMAIN_UID1"
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-update1.yaml"
  doCommand -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand -c "export CORRECTED_DATASOURCE_SECRET=false"

  dumpInfo

  doCommand    "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand    "\$MIIWRAPPERDIR/create-secrets.sh"
  doCommand -c "\$WORKDIR/utils/create-configmap.sh -c \${DOMAIN_UID}-wdt-config-map -f \${WORKDIR}/model-configmaps/datasource -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE"

  doCommand -c "${KUBERNETES_CLI:-kubectl} apply -f \$WORKDIR/\$DOMAIN_RESOURCE_FILENAME"
  doCommand    "\$WORKDIR/utils/patch-restart-version.sh -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE"
  waitForDomain Completed

  if [ ! "$DRY_RUN" = "true" ]; then
    diefast # (cheat to speedup a subsequent roll/shutdown)
    testapp internal cluster-1 "mynewdatasource"
    if [ "$OKD" = "true" ]; then
      routeHost=$(getRouteHost "${DOMAIN_UID}-cluster-cluster-1")
      echo $routeHost
      doCommand -c export ROUTE_HOST=${routeHost}
      testapp OKD  cluster-1 "mynewdatasource" 
    else
      testapp traefik  cluster-1 "mynewdatasource"
    fi
  fi

  dumpInfo
fi

#
# Deploy a second domain to the same ns similar to the
# update1 ns, wait for it to start, and use the test
# app to verify its up. Also verify that the original
# domain is responding to its calls.
#

if [ "$DO_UPDATE2" = "true" ]; then
  doCommand -c "echo ====== USE CASE: UPDATE2 ======"

  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
  fi
  doCommand -c "export DOMAIN_UID=$DOMAIN_UID2"
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-update2.yaml"
  doCommand -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand -c "export CORRECTED_DATASOURCE_SECRET=false"
  doCommand -c "export CUSTOM_DOMAIN_NAME=domain2"

  dumpInfo

  doCommand    "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand    "\$MIIWRAPPERDIR/create-secrets.sh"
  if [ "$OKD" = "false" ]; then
    doCommand    "\$MIIWRAPPERDIR/stage-and-create-ingresses.sh"
  fi
  doCommand -c "\$WORKDIR/utils/create-configmap.sh -c \${DOMAIN_UID}-wdt-config-map -f \${WORKDIR}/model-configmaps/datasource -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE"

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
    testapp internal cluster-1 "name....domain2"
    if [ "$OKD" = "true" ]; then
      testapp OKD cluster-1 "name....domain2" 
    else
      testapp traefik  cluster-1 "name....domain2"
    fi
    doCommand -c export DOMAIN_UID=$DOMAIN_UID1
    testapp internal cluster-1 "name....domain1"
    if [ "$OKD" = "true" ]; then
      testapp OKD  cluster-1 "name....domain2" 
    else
      testapp traefik  cluster-1 "name....domain1"
    fi
  fi

  dumpInfo
fi

#
# Deploy an updated application to the first domain
# using an updated image, wait for it to roll, and 
# test the app to verify the update took effect.
#

if [ "$DO_UPDATE3_IMAGE" = "true" ]; then
  doCommand -c "echo ====== USE CASE: UPDATE3-IMAGE ======"
  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
    doCommand -c "export MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-${IMAGE_TYPE}-v2}"
  else
    doCommand -c "export MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v2}"
  fi
  doCommand -c "export ARCHIVE_SOURCEDIR=archives/archive-v2"
  doCommand    "\$MIIWRAPPERDIR/build-model-image.sh"
fi

if [ "$DO_UPDATE3_MAIN" = "true" ]; then
  doCommand -c "echo ====== USE CASE: UPDATE3-MAIN ======"

  dumpInfo

  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
    doCommand -c "export MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-${IMAGE_TYPE}-v2}"
  else
    doCommand -c "export MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v2}"
  fi
  doCommand -c "export DOMAIN_UID=$DOMAIN_UID1"
  doCommand -c "export DOMAIN_RESOURCE_FILENAME=domain-resources/mii-update3.yaml"
  doCommand -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand -c "export CORRECTED_DATASOURCE_SECRET=false"
  doCommand -c "export CUSTOM_DOMAIN_NAME=domain1"

  doCommand    "\$MIIWRAPPERDIR/stage-domain-resource.sh"

  doCommand -c "${KUBERNETES_CLI:-kubectl} apply -f \$WORKDIR/\$DOMAIN_RESOURCE_FILENAME"
  waitForDomain Completed

  if [ ! "$DRY_RUN" = "true" ]; then
    diefast # (cheat to speedup a subsequent roll/shutdown)
    testapp internal cluster-1 "v2"
    if [ "$OKD" = "true" ]; then
      routeHost=$(getRouteHost "${DOMAIN_UID}-cluster-cluster-1")
      echo $routeHost
      doCommand -c export ROUTE_HOST=${routeHost}
      testapp OKD  cluster-1 "v2" 
    else
      testapp traefik  cluster-1 "v2"
    fi
  fi

  dumpInfo
fi

#
# Update work manager configuration and datasource on the running domain
# using online update. Specifically:
#   - Update WM config in the MII configmap
#   - Update secret containing DB config
#   - Patch running domain resource to enable online update
#   - Patch introspect version
#   - Wait for introspector to complete
#   - Use the test app to verify the updated WM and data source configurations.
#   - Use the test app to verify updated data source can contact DB
#     (this last check is skipped if DO_ASSUME_DB is not 'true'.)
#

if [ "$DO_UPDATE4" = "true" ]; then
  doCommand -c "echo ====== USE CASE: UPDATE4 ======"

  if [ "$DO_AI" = "true" ]; then
    doCommand -c "echo Running in auxiliary image mode"
    doCommand -c "export IMAGE_TYPE=${WDT_DOMAIN_TYPE}-AI"
  fi

  doCommand -c "export DOMAIN_UID=$DOMAIN_UID1"
  doCommand -c "export INCLUDE_MODEL_CONFIGMAP=true"
  doCommand -c "export CORRECTED_DATASOURCE_SECRET=true"

  dumpInfo
  podInfoBefore="$(getPodInfo | grep -v introspectVersion)"

  doCommand    "\$MIIWRAPPERDIR/stage-domain-resource.sh"
  doCommand    "\$MIIWRAPPERDIR/create-secrets.sh"
  doCommand -c "\$WORKDIR/utils/create-configmap.sh -c \${DOMAIN_UID}-wdt-config-map -f  \${WORKDIR}/model-configmaps/datasource -f \${WORKDIR}/model-configmaps/workmanager -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE"

  doCommand    "\$WORKDIR/utils/patch-enable-online-update.sh -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE"
  doCommand    "\$WORKDIR/utils/patch-introspect-version.sh -d \$DOMAIN_UID -n \$DOMAIN_NAMESPACE"
  waitForDomain Completed

  if [ ! "$DRY_RUN" = "true" ]; then
    testapp internal cluster-1 "'SampleMinThreads' with configured count: 2" 60 quiet
    testapp internal cluster-1 "'SampleMaxThreads' with configured count: 20" 
    if [ "$DO_ASSUME_DB" = "true" ]; then
      testapp internal cluster-1 "Datasource 'mynewdatasource':  State='Running', testPool='Passed'"
    fi

    podInfoAfter="$(getPodInfo | grep -v introspectVersion)"
    if [ "$podInfoBefore" = "$podInfoAfter" ]; then
      trace "No roll detected. Good!"
    else
      dumpInfo
      trace "Info: Pods before:" && echo "${podInfoBefore}"
      trace "Info: Pods after:" && echo "${podInfoAfter}"
      trace "Error: Unexpected roll detected."
      exit 1
    fi
  fi
  dumpInfo
fi

trace "Woo hoo! Finished without errors! Total runtime $SECONDS seconds."

# TBD Add JRF wallet export/import testing?  There's another test that already tests the sample's import/export script.
