#!/bin/bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

function processJson {
    python -c "
import sys, json
j=json.load(sys.stdin)
$1
"
}

function trace {
  #Date reported in same format as oper-log for easier correlation.  01-22-2018T21:49:01
  #See also a similar echo in function fail
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] ${FUNCNAME[1]}: ""$@"
}

function fail {
  set +x
  #See also a similar echo in function trace
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] ${FUNCNAME[1]}: [ERROR] ""$@"

  echo "Stack trace:"
  local deptn=${#FUNCNAME[@]}
  local i
  for ((i=1; i<$deptn; i++)); do
      local func="${FUNCNAME[$i]}"
      local line="${BASH_LINENO[$((i-1))]}"
      local src="${BASH_SOURCE[$((i-1))]}"
      printf '%*s' $i '' # indent
      echo "at: $func(), $src, line $line"
  done

  echo "Exiting with status 1"

  exit 1
}

trap ctrl_c INT

function ctrl_c() {
    fail "Trapped CTRL-C"
}

# setup_local is for arbitrary dev hosted linux - it assumes docker & k8s are already installed
function setup_local {

  docker pull store/oracle/weblogic:12.2.1.3
  docker pull store/oracle/serverjre:8

  # we call mkdir here because clean will delete the RESULT_DIR if it is in the same directory as the k8s install.
  /usr/local/packages/aime/ias/run_as_root "mkdir -m 777 -p $RESULT_DIR"
}

function create_image_pull_secret {

    trace "Creating Secret"
    kubectl create secret docker-registry wlsldi-secret  \
    --docker-server=wlsldi-v2.docker.oraclecorp.com \
    --docker-username=teamsldi_us@oracle.com \
    --docker-password=$docker_pass \
    --docker-email=teamsldi_us@oracle.com

    trace "Checking Secret"
    local SECRET=`kubectl get secret wlsldi-secret | grep wlsldi | wc | awk ' { print $1; }'`
    if [ "$SECRET" != "1" ]; then
        fail 'secret wlsldi-secret was not created successfully'
    fi

}

# op_define OP_KEY NAMESPACE TARGET_NAMESPACES EXTERNAL_REST_HTTPSPORT
#   sets up table of operator values.
#
# op_get    OP_KEY
#   gets an operator value
#
# op_echo   OP_KEY
#   lists the values
#
# Usage example:
#   op_define myop mynamespace ns1,ns2 34007
#   opkey="myop"
#   echo Defined operator $opkey with `op_echo $opkey`
#   local nspace="`op_get $opkey NAMESPACE`"
#

function op_define {
    if [ "$#" != 4 ] ; then
      fail "requires 4 parameters: OP_KEY NAMESPACE TARGET_NAMESPACES EXTERNAL_REST_HTTPSPORT"
    fi
    local opkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    eval export OP_${opkey}_NAMESPACE="$2"
    eval export OP_${opkey}_TARGET_NAMESPACES="$3"
    eval export OP_${opkey}_EXTERNAL_REST_HTTPSPORT="$4"

    # derived TMP_DIR for operator = $RESULT_DIR/$NAMESPACE :
    eval export OP_${opkey}_TMP_DIR="$RESULT_DIR/$2"

    trace Defined operator $1 with values `op_echo_all $1`
}

function op_get {
    local opkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    eval "echo \${OP_${opkey}_${2?}}"
}

function op_echo_all {
    local opkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    env | grep "^OP_${opkey}_"
}

function deploy_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: opkey"
    fi

    local opkey=${1}
    local NAMESPACE="`op_get $opkey NAMESPACE`"
    local TARGET_NAMESPACES="`op_get $opkey TARGET_NAMESPACES`"
    local EXTERNAL_REST_HTTPSPORT="`op_get $opkey EXTERNAL_REST_HTTPSPORT`"
    local TMP_DIR="`op_get $opkey TMP_DIR`"

    trace 'customize the yaml'
    local inputs="$TMP_DIR/create-operator-inputs.yaml"
    mkdir -p $TMP_DIR
    cp $PROJECT_ROOT/kubernetes/create-weblogic-operator.sh $TMP_DIR/create-weblogic-operator.sh
    # copy the template file and dependent scripts too
    mkdir $TMP_DIR/internal
    cp $PROJECT_ROOT/kubernetes/internal/* $TMP_DIR/internal/
    cp $PROJECT_ROOT/kubernetes/create-operator-inputs.yaml $inputs

    trace 'customize the inputs yaml file to use our pre-built docker image'
    sed -i -e "s|\(imagePullPolicy:\).*|\1${IMAGE_PULL_POLICY}|g" $inputs
    sed -i -e "s|\(image:\).*|\1${IMAGE_NAME}:${IMAGE_TAG}|g" $inputs
    if [ -n "${IMAGE_PULL_SECRET}" ]; then
      sed -i -e "s|#imagePullSecretName:.*|imagePullSecretName: ${IMAGE_PULL_SECRET}|g" $inputs    	
    fi
    trace 'customize the inputs yaml file to generate a self-signed cert for the external Operator REST https port'
    sed -i -e "s|\(externalRestOption:\).*|\1self-signed-cert|g" $inputs
    sed -i -e "s|\(externalSans:\).*|\1DNS:${host}|g" $inputs
    trace 'customize the inputs yaml file to set the java logging level to FINER'
    sed -i -e "s|\(javaLoggingLevel:\).*|\1FINER|g" $inputs
    sed -i -e "s|\(externalRestHttpsPort:\).*|\1${EXTERNAL_REST_HTTPSPORT}|g" $inputs
    trace 'customize the inputs yaml file to add test namespace' 
    sed -i -e "s/^namespace:.*/namespace: ${NAMESPACE}/" $inputs
    sed -i -e "s/^targetNamespaces:.*/targetNamespaces: ${TARGET_NAMESPACES}/" $inputs
    sed -i -e "s/^serviceAccount:.*/serviceAccount: ${NAMESPACE}/" $inputs

    trace 'run the script to deploy the weblogic operator'
    sh $TMP_DIR/create-weblogic-operator.sh -i $inputs

    # Prepend "+" to detailed debugging to make it easy to filter out
    echo 'weblogic-operator.yaml contents:' 2>&1 | sed 's/^/+/' 2>&1
    cat $TMP_DIR/weblogic-operator.yaml 2>&1 | sed 's/^/+/' 2>&1
    echo 2>&1 | sed 's/^/+/' 2>&1

    trace "Checking the operator pods"
    local namespace=$NAMESPACE
    local REPLICA_SET=`kubectl describe deploy weblogic-operator -n ${namespace} | grep NewReplicaSet: | awk ' { print $2; }'`
    local POD_TEMPLATE=`kubectl describe rs ${REPLICA_SET} -n ${namespace} | grep ^Name: | awk ' { print $2; } '`
    local POD=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | awk ' { print $1; } '`

    trace "Checking image for pod $POD"
    local IMAGE=`kubectl describe pod $POD -n ${namespace} | grep "Image:" | awk ' { print $2; } '`
    if [ "$IMAGE" != "${IMAGE_NAME}:${IMAGE_TAG}" ]; then
        fail "pod image should be ((${IMAGE_NAME}:${IMAGE_TAG})) but image is ((${IMAGE}))"
    fi

}

function test_first_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: opkey"
    fi
    local OP_KEY=${1}
    deploy_operator $OP_KEY
    verify_no_domain_via_oper_rest $OP_KEY
}

# dom_define   DOM_KEY NAMESPACE DOMAIN_UID WL_CLUSTER_NAME MS_BASE_NAME ADMIN_PORT ADMIN_WLST_PORT ADMIN_NODE_PORT MS_PORT LOAD_BALANCER_WEB_PORT LOAD_BALANCER_ADMIN_PORT
#   Sets up a table of domain values:  all of the above, plus TMP_DIR which is derived.
#
# dom_get      DOM_KEY
#   Gets a domain value.
#
# dom_echo_all DOM_KEY
#   Lists domain values for the given key.
#
# Usage example:
#   dom_define mydom default domain1 cluster-1 managed-server 7001 30012 30701 8001 30305 30315
#   local DOM_KEY=mydom
#   local nspace="`dom_get $DOM_KEY NAMESPACE`"
#   echo Defined operator $opkey with `dom_echo_all $DOM_KEY`
#
function dom_define {
    if [ "$#" != 11 ] ; then
      fail "requires 11 parameters: DOM_KEY NAMESPACE DOMAIN_UID WL_CLUSTER_NAME MS_BASE_NAME ADMIN_PORT ADMIN_WLST_PORT ADMIN_NODE_PORT MS_PORT LOAD_BALANCER_WEB_PORT LOAD_BALANCER_ADMIN_PORT"
    fi
    local DOM_KEY="`echo \"${1}\" | sed 's/-/_/g'`"
    eval export DOM_${DOM_KEY}_NAMESPACE="$2"
    eval export DOM_${DOM_KEY}_DOMAIN_UID="$3"
    eval export DOM_${DOM_KEY}_WL_CLUSTER_NAME="$4"
    eval export DOM_${DOM_KEY}_MS_BASE_NAME="$5"
    eval export DOM_${DOM_KEY}_ADMIN_PORT="$6"
    eval export DOM_${DOM_KEY}_ADMIN_WLST_PORT="$7"
    eval export DOM_${DOM_KEY}_ADMIN_NODE_PORT="$8"
    eval export DOM_${DOM_KEY}_MS_PORT="$9"
    eval export DOM_${DOM_KEY}_LOAD_BALANCER_WEB_PORT="${10}"
    eval export DOM_${DOM_KEY}_LOAD_BALANCER_ADMIN_PORT="${11}"

    # derive TMP_DIR $RESULT_DIR/$NAMESPACE-$DOMAIN_UID :
    eval export DOM_${DOM_KEY}_TMP_DIR="$RESULT_DIR/$2-$3"

    trace Defined domain $1 with values `dom_echo_all $1`
}

function dom_get {
    local domkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    eval "echo \${DOM_${domkey}_${2?}}"
}

function dom_echo_all {
    local domkey="`echo \"${1?}\" | sed 's/-/_/g'`"
    env | grep "^DOM_${domkey}_"
}

function run_create_domain_job {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
    local ADMIN_NODE_PORT="`dom_get $1 ADMIN_NODE_PORT`"
    local MS_PORT="`dom_get $1 MS_PORT`"
    local LOAD_BALANCER_WEB_PORT="`dom_get $1 LOAD_BALANCER_WEB_PORT`"
    local LOAD_BALANCER_ADMIN_PORT="`dom_get $1 LOAD_BALANCER_ADMIN_PORT`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    if [ -z $JVM_ARGS ] || [ "$JVM_ARGS" == "" ] ; then
        local WLS_JAVA_OPTIONS="-Dweblogic.StdoutDebugEnabled=false"
    else
        local WLS_JAVA_OPTIONS="$JVM_ARGS"
    fi

    trace "WLS_JAVA_OPTIONS = \"$WLS_JAVA_OPTIONS\""

    local PV="pv"
    local PV_DIR="persistentVolume-${DOMAIN_UID}"

    trace "Create $DOMAIN_UID in $NAMESPACE namespace "

    local tmp_dir="$TMP_DIR"
    mkdir -p $tmp_dir

    local CREDENTIAL_NAME="$DOMAIN_UID-weblogic-credentials"
    local CREDENTIAL_FILE="${tmp_dir}/$CREDENTIAL_NAME.yaml"

    trace 'Create the secret with weblogic admin credentials'
    cp $CUSTOM_YAML/domain1-weblogic-credentials.yaml  $CREDENTIAL_FILE

    sed -i -e "s|namespace: default|namespace: $NAMESPACE|g" $CREDENTIAL_FILE
    sed -i -e "s|name: domain1-weblogic-credentials|name: $CREDENTIAL_NAME|g" $CREDENTIAL_FILE

    kubectl apply -f $CREDENTIAL_FILE -n $NAMESPACE

    trace 'Check secret'
    local ADMINSECRET=`kubectl get secret $CREDENTIAL_NAME -n $NAMESPACE | grep $CREDENTIAL_NAME | wc -l `
    if [ "$ADMINSECRET" != "1" ]; then
        fail 'could not create the secret with weblogic admin credentials'
    fi

    trace 'Prepare the job customization script'
    local internal_dir="$tmp_dir/internal"
    mkdir $tmp_dir/internal
    cp $PROJECT_ROOT/kubernetes/create-domain-job.sh ${tmp_dir}/create-domain-job.sh
    cp $PROJECT_ROOT/kubernetes/internal/* ${internal_dir}/

    # Common inputs file for creating a domain
    cp $PROJECT_ROOT/kubernetes/create-domain-job-inputs.yaml ${tmp_dir}/create-domain-job-inputs.yaml

    # copy testwebapp.war for testing
    cp $PROJECT_ROOT/qa/testwebapp.war ${tmp_dir}/testwebapp.war

    # Customize the create domain job inputs
    sed -i -e "s/^exposeAdminT3Channel:.*/exposeAdminT3Channel: true/" ${tmp_dir}/create-domain-job-inputs.yaml

    # Customize more configuraiton 
    sed -i -e "s/^persistenceVolumeName:.*/persistenceVolumeName: ${PV}/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^persistenceVolumeClaimName:.*/persistenceVolumeClaimName: $PV-claim/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s;^persistencePath:.*;persistencePath: $RESULT_DIR/$PV_DIR;" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^domainUid:.*/domainUid: $DOMAIN_UID/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^clusterName:.*/clusterName: $WL_CLUSTER_NAME/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^namespace:.*/namespace: $NAMESPACE/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^t3ChannelPort:.*/t3ChannelPort: $ADMIN_WLST_PORT/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^adminNodePort:.*/adminNodePort: $ADMIN_NODE_PORT/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^exposeAdminNodePort:.*/exposeAdminNodePort: true/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^t3PublicAddress:.*/t3PublicAddress: $host/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^adminPort:.*/adminPort: $ADMIN_PORT/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^managedServerPort:.*/managedServerPort: $MS_PORT/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^secretName:.*/secretName: $CREDENTIAL_NAME/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^secretsMountPath:.*/secretsMountPath: \/var\/run\/secrets-$DOMAIN_UID/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^loadBalancerWebPort:.*/loadBalancerWebPort: $LOAD_BALANCER_WEB_PORT/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^loadBalancerAdminPort:.*/loadBalancerAdminPort: $LOAD_BALANCER_ADMIN_PORT/" ${tmp_dir}/create-domain-job-inputs.yaml
    sed -i -e "s/^javaOptions:.*/javaOptions: $WLS_JAVA_OPTIONS/" ${tmp_dir}/create-domain-job-inputs.yaml

    # we will test cluster scale up and down in domain1 and domain4 
    if [ "$DOMAIN_UID" == "domain1" ] || [ "$DOMAIN_UID" == "domain4" ] ; then
      sed -i -e "s/^managedServerCount:.*/managedServerCount: 3/"  ${tmp_dir}/create-domain-job-inputs.yaml
    fi

    trace 'create the host directory that we will use as a persistent volume AND set its permissions to 777'
    mkdir -m 777 -p $RESULT_DIR/$PV_DIR

    trace 'Run the script to create the domain'

    sh ${tmp_dir}/create-domain-job.sh -i ${tmp_dir}/create-domain-job-inputs.yaml

    trace 'run_create_domain_job done'
}

# note that this function has slightly different parameters than deploy_webapp_via_WLST
function deploy_webapp_via_REST {

    #TODO Instead of sleeping, create a py script that verifies admin server
    #     & managed server JMX communication has been established, and retry
    #     until the script succeeds.
    #     Rose suggests: domainRuntime(); cd("ServerRuntimes"); cd("<managedServer>"); cd("<othermgdServer"); etc

    trace "Sleeping 120 seconds to give time for admin server and cluster to establish communication."
    sleep 120

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local ADMIN_PORT="`dom_get $1 ADMIN_PORT`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local WLS_ADMIN_USERNAME="`get_wladmin_user`"
    local WLS_ADMIN_PASSWORD="`get_wladmin_pass`"

    local AS_NAME="$DOMAIN_UID-admin-server"

    trace "deploy the web app to domain $DOMAIN_UID in $NAMESPACE namespace"

    # call the wls rest api to deploy the app

    local CURL_RESPONSE_BODY="$TMP_DIR/deploywebapp.rest.response.body"

    local get_admin_host="kubectl get services -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$AS_NAME\")].spec.clusterIP}'"

    trace admin host query is $get_admin_host

    local ADMIN_HOST=`eval $get_admin_host`

    trace admin host is $get_admin_host

    local REST_ADDR="http://${ADMIN_HOST}:${ADMIN_PORT}"

    echo "NO_DATA" > $CURL_RESPONSE_BODY

    local HTTP_RESPONSE=$(curl --noproxy "*" \
      --user ${WLS_ADMIN_USERNAME}:${WLS_ADMIN_PASSWORD} \
      -H X-Requested-By:Integration-Test \
      -H Accept:application/json \
      -H Content-Type:multipart/form-data \
      -F "model={
        name: 'testwebapp',
        targets: [ '$WL_CLUSTER_NAME' ]
      }" \
      -F "deployment=@$TMP_DIR/testwebapp.war" \
      -X POST ${REST_ADDR}/management/wls/latest/deployments/application \
      -o ${CURL_RESPONSE_BODY} \
      --write-out "%{http_code}" \
    )

    echo $HTTP_RESPONSE
    cat $CURL_RESPONSE_BODY

    # verify that curl returned a status code of 200 or 201

    if [ "${HTTP_RESPONSE}" != "200" ] && [ "${HTTP_RESPONSE}" != "201" ]; then
        fail "curl did not return a 200 or 201 status code, got ${HTTP_RESPONSE}"
    fi

    trace 'done'
}

function get_cluster_replicas {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"

    local find_num_replicas="kubectl get domain $DOMAIN_UID -n $NAMESPACE -o jsonpath='{.spec.clusterStartup[?(@.clusterName == \"$WL_CLUSTER_NAME\")].replicas }'"
    local replicas=`eval $find_num_replicas`

    if [ -z ${replicas} ]; then
      fail "replicas not found for $WL_CLUSTER_NAME in domain $DOMAIN_UID"
    fi
    echo $replicas
}

function verify_managed_servers_ready {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local DOM_KEY="$1"
    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"

    local replicas=`get_cluster_replicas $DOM_KEY`

    local max_count=50
    local wait_time=10

    local i
    trace "verify $replicas number of managed servers for readiness"
    for i in $(seq 1 $replicas);
    do
      local MS_NAME="$DOMAIN_UID-${MS_BASE_NAME}$i"
      trace "verify that $MS_NAME pod is ready"
      local count=1
      local status="0/1"
      while [ "${status}" != "1/1" -a $count -lt $max_count ] ; do
        local status=`kubectl get pods -n $NAMESPACE | egrep $MS_NAME | awk '{print $2}'`
        local count=`expr $count + 1`
        if [ "${status}" != "1/1" ] ; then
          trace "kubectl ready status is ${status}, iteration $count of $max_count"
          sleep $wait_time
        fi
      done

      if [ "${status}" != "1/1" ] ; then
        kubectl get pods -n $NAMESPACE
        fail "ERROR: the $MS_NAME pod is not running and ready, exiting!"
      fi
    done
}

function test_wls_liveness_probe {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local DOM_KEY="$1"
    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local POD_NAME="${DOMAIN_UID}-${MS_BASE_NAME}1"

    local initial_restart_count=`kubectl describe pod $POD_NAME --namespace=$NAMESPACE | egrep Restart | awk '{print $3}'`

    # First, we kill the mgd server process in the container three times to cause the node manager
    # to mark the server 'failed not restartable'.  This in turn is detected by the liveness probe,
    # which initiates a pod restart.

    cat <<EOF > ${TMP_DIR}/killserver.sh
#!/bin/bash
kill -9 \`jps | grep Server | awk '{print \$1}'\`
EOF

    chmod a+x ${TMP_DIR}/killserver.sh
    kubectl cp ${TMP_DIR}/killserver.sh $POD_NAME:/shared/killserver.sh --namespace=$NAMESPACE
    [ ! "$?" = "0" ] && fail "Error: Make sure that ${TMP_DIR}/killserver.sh exists and $POD_NAME pod is running."

    for value in {1..3}
    do
      kubectl exec -it $POD_NAME /shared/killserver.sh --namespace=$NAMESPACE
      sleep 1
    done
    kubectl exec -it $POD_NAME rm /shared/killserver.sh --namespace=$NAMESPACE

    # Now we verify the pod restarts.
   
    local maxwaitsecs=180
    local mstart=`date +%s`
    while : ; do
      local mnow=`date +%s`
      local final_restart_count=`kubectl describe pod $POD_NAME --namespace=$NAMESPACE | egrep Restart | awk '{print $3}'`
      local restart_count_diff=$((final_restart_count-initial_restart_count))
      if [ $restart_count_diff -eq 1 ]; then
        trace 'WLS liveness probe test is successful.'
        break
      fi
      if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
        fail 'WLS liveness probe is not working.'
      fi
      sleep 5
    done
}

function verify_webapp_load_balancing {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey managedServerCount"
    fi 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local LOAD_BALANCER_WEB_PORT="`dom_get $1 LOAD_BALANCER_WEB_PORT`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local MS_NUM="$2"
    if [ "$MS_NUM" -lt "2" ] || [ "$MS_NUM" -gt "3" ] ; then
       fail "wrong parameters, managed server count must be 2 or 3"
    fi

    local list=()
    local i
    for i in $(seq 1 $MS_NUM);
    do
      local msname="$DOMAIN_UID-${MS_BASE_NAME}$i"
      list+=("$msname")
    done

    trace "confirm the load balancer is working"

    trace 'verify that ingress is created'
    kubectl describe ingress -n $NAMESPACE

    local TEST_APP_URL="http://${host}:${LOAD_BALANCER_WEB_PORT}/testwebapp/"
    local CURL_RESPONSE_BODY="$TMP_DIR/testapp.response.body"

    trace 'wait for test app to become available'
    local max_count=30
    local wait_time=6
    local count=1

    while [ "${HTTP_RESPONSE}" != "200" -a $count -lt $max_count ] ; do
      local count=`expr $count + 1`
      echo "NO_DATA" > $CURL_RESPONSE_BODY
      local HTTP_RESPONSE=$(curl --noproxy ${host} ${TEST_APP_URL} \
        --write-out "%{http_code}" \
        -o ${CURL_RESPONSE_BODY} \
      )

      if [ "${HTTP_RESPONSE}" != "200" ]; then
        trace "testwebapp did not return 200 status code, got ${HTTP_RESPONSE}, iteration $count of $max_count"
        sleep $wait_time
      fi
    done


    if [ "${HTTP_RESPONSE}" != "200" ]; then
        kubectl get services --all-namespaces
        kubectl get pods -n $NAMESPACE
        local i
        for i in $(seq 1 $MS_NUM);
        do
          kubectl logs ${DOMAIN_UID}-${MS_BASE_NAME}$i -n $NAMESPACE
        done
        fail "ERROR: testwebapp is not available"
    fi

    local i
    for i in "${list[@]}"; do
      local SERVER_SEARCH_STRING="InetAddress.hostname: $i"
      trace "check if the load balancer can reach $i"
      local from_server=false
      local j
      for j in `seq 1 20`
      do
        echo "NO_DATA" > $CURL_RESPONSE_BODY

        local HTTP_RESPONSE=$(curl --noproxy ${host} ${TEST_APP_URL} \
          --write-out "%{http_code}" \
          -o ${CURL_RESPONSE_BODY} \
        )

        echo $HTTP_RESPONSE
        cat $CURL_RESPONSE_BODY

        if [ "${HTTP_RESPONSE}" != "200" ]; then
          trace "curl did not return a 200 status code, got ${HTTP_RESPONSE}"
          continue
        else
          if grep -q "${SERVER_SEARCH_STRING}" $CURL_RESPONSE_BODY; then
            local from_server=true
            trace " get response from $i"
            break
          fi
        fi
      done
      if [ "$from_server" = false ]; then
        fail "load balancer can not reach server $i"
      fi
    done

    trace 'done'
}

function verify_admin_server_ext_service {

    # Pre-requisite: requires admin server to be already up and running and able to service requests

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"
    local WLS_ADMIN_USERNAME="`get_wladmin_user`"
    local WLS_ADMIN_PASSWORD="`get_wladmin_pass`"

    local ADMIN_SERVER_NODEPORT_SERVICE="$DOMAIN_UID-admin-server"

    trace "verify that admin server REST and console are accessible from outside of the kubernetes cluster"

    local get_configured_nodePort="kubectl get domains -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$DOMAIN_UID\")].spec.asNodePort}'"

    local configuredNodePort=`eval $get_configured_nodePort`

    trace "configured asNodePort in domain $DOMAIN_UID is ${configuredNodePort}"

    if [ -z ${configuredNodePort} ]; then
      kubectl describe domain $DOMAIN_UID -n $NAMESPACE
      trace "Either domain $DOMAIN_UID does not exist or asNodePort is not configured in domain $DOMAIN_UID. Skipping this verify"
      return
    fi

    local get_service_nodePort="kubectl get services -n $NAMESPACE -o jsonpath='{.items[?(@.metadata.name == \"$ADMIN_SERVER_NODEPORT_SERVICE\")].spec.ports[0].nodePort}'"
   
    trace get_service_nodePort
 
    local nodePort=`eval $get_service_nodePort`

    if [ -z ${nodePort} ]; then
      fail "nodePort not found in domain $DOMAIN_UID"
    fi

    if [ "$nodePort" -ne "$configuredNodePort" ]; then
      fail "Configured asNodePort of ${configuredNodePort} is different from nodePort found in service ${ADMIN_SERVER_NODEPORT_SERVICE}: ${nodePort}"
    fi

    local TEST_REST_URL="http://${host}:${nodePort}/management/weblogic/latest/serverRuntime"

    local CURL_RESPONSE_BODY="$TMP_DIR/testconsole.response.body"

    echo "NO_DATA" > $CURL_RESPONSE_BODY

    local HTTP_RESPONSE=$(curl --noproxy ${host} ${TEST_REST_URL} \
      --user ${WLS_ADMIN_USERNAME}:${WLS_ADMIN_PASSWORD} \
      -H X-Requested-By:Integration-Test \
      --write-out "%{http_code}" \
      -o ${CURL_RESPONSE_BODY} \
    )

    trace "REST test: $HTTP_RESPONSE "

    if [ "${HTTP_RESPONSE}" != "200" ]; then
      cat $CURL_RESPONSE_BODY
      fail "accessing admin server REST endpoint did not return 200 status code, got ${HTTP_RESPONSE}"
    fi

    local TEST_CONSOLE_URL="http://${host}:${nodePort}/console/login/LoginForm.jsp"

    echo "NO_DATA" > $CURL_RESPONSE_BODY

    local HTTP_RESPONSE=$(curl --noproxy ${host} ${TEST_CONSOLE_URL} \
      --write-out "%{http_code}" \
      -o ${CURL_RESPONSE_BODY} \
    )

    trace "console test: $HTTP_RESPONSE "

    if [ "${HTTP_RESPONSE}" != "200" ]; then
      cat $CURL_RESPONSE_BODY
      fail "accessing admin console did not return 200 status code, got ${HTTP_RESPONSE}"
    fi

    trace 'done'
}

function test_domain_creation {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi 

    local DOM_KEY="$1"
    local OP_KEY="$2"

    run_create_domain_job $DOM_KEY
    verify_domain_created $DOM_KEY $OP_KEY
    verify_managed_servers_ready $DOM_KEY

    #deploy_webapp_via_REST $DOM_KEY
    deploy_webapp_via_WLST $DOM_KEY
    verify_webapp_load_balancing $DOM_KEY 2

    verify_admin_server_ext_service $DOM_KEY

    extra_weblogic_checks
}

function verify_domain {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi 

    local DOM_KEY="$1"
    local OP_KEY="$2"
    
    verify_domain_created $DOM_KEY $OP_KEY
    verify_managed_servers_ready $DOM_KEY

    verify_webapp_load_balancing $DOM_KEY 2
    verify_admin_server_ext_service $DOM_KEY
}

function extra_weblogic_checks {

    trace 'Pani will contribute some extra checks here'

}

# This function call operator Rest api with the given url
function call_operator_rest {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: operatorKey urlTail"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local OPERATOR_TMP_DIR="`op_get $OP_KEY TMP_DIR`"
    local URL_TAIL="${2}"

    trace "URL_TAIL=$URL_TAIL"

    trace "Checking REST service is running"
    local REST_SERVICE=`kubectl get services -n $OPERATOR_NS -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-service")]}'`
    if [ -z "$REST_SERVICE" ]; then
        fail 'operator rest service was not created'
    fi

    local REST_PORT=`kubectl get services -n $OPERATOR_NS -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-service")].spec.ports[?(@.name == "rest-https")].nodePort}'`
    local REST_ADDR="https://${host}:${REST_PORT}"
    local SECRET=`kubectl get serviceaccount weblogic-operator -n $OPERATOR_NS -o jsonpath='{.secrets[0].name}'`
    local ENCODED_TOKEN=`kubectl get secret ${SECRET} -n $OPERATOR_NS -o jsonpath='{.data.token}'`
    local TOKEN=`echo ${ENCODED_TOKEN} | base64 --decode`
    local OPERATOR_CERT_DATA=`grep externalOperatorCert ${OPERATOR_TMP_DIR}/weblogic-operator.yaml | awk '{ print $2 }'`
    local OPERATOR_CERT_FILE="${OPERATOR_TMP_DIR}/operator.cert.pem"
    echo ${OPERATOR_CERT_DATA} | base64 --decode > ${OPERATOR_CERT_FILE}
    cat ${OPERATOR_CERT_FILE}

    trace "Calling some operator REST APIs via ${REST_ADDR}/${URL_TAIL}"

    #pod=`kubectl get pod -n $OPERATOR_NS | grep $OPERATOR_NS | awk '{ print $1 }'`
    #kubectl logs $pod -n $OPERATOR_NS > "${OPERATOR_TMP_DIR}/operator.pre.rest.log"

    # turn off all of the https proxying so that curl will work
    OLD_HTTPS_PROXY="${HTTPS_PROXY}"
    old_https_proxy="${https_proxy}"
    export HTTPS_PROXY=""
    export https_proxy=""

    local OPER_CURL_STDERR="${OPERATOR_TMP_DIR}/operator.rest.stderr"
    local OPER_CURL_RESPONSE_BODY="${OPERATOR_TMP_DIR}/operator.rest.response.body"

    echo "NO_DATA" > $OPER_CURL_STDERR
    echo "NO_DATA" > $OPER_CURL_RESPONSE_BODY

    local STATUS_CODE=`curl \
        -v \
        --cacert ${OPERATOR_CERT_FILE} \
        -H "Authorization: Bearer ${TOKEN}" \
        -H Accept:application/json \
        -X GET ${REST_ADDR}/${URL_TAIL} \
        -o ${OPER_CURL_RESPONSE_BODY} \
        --stderr ${OPER_CURL_STDERR} \
        -w "%{http_code}"`

    cat $OPER_CURL_STDERR
    cat $OPER_CURL_RESPONSE_BODY

    # restore the https proxying now that we're done using curl
    export HTTPS_PROXY="${OLD_HTTPS_PROXY}"
    export https_proxy="${old_https_proxy}"

    #kubectl logs $pod -n $OPERATOR_NS > "${OPERATOR_TMP_DIR}/operator.post.rest.log"
    #diff ${OPERATOR_TMP_DIR}/operator.pre.rest.log ${OPERATOR_TMP_DIR}/operator.post.rest.log

    # verify that curl returned a status code of 200
    # e.g. < HTTP/1.1 200 OK
    if [ "${STATUS_CODE}" != "200" ]; then
        fail "curl did not return a 200 status code, it returned ${STATUS_CODE}"
    fi
}

function mvn_build_check {
    local fail_out=`grep FAIL ${1:?}`
    local success_out=`grep SUCCESS $1`

    [ "$fail_out" != "" ] && fail "ERROR: found FAIL in output $1" 
    [ "$success_out" = "" ] && fail "ERROR: didn't find SUCCESS in output $1" 
}

function mvn_integration_test {

    trace "generating job to run mvn -P integration-tests clean install "

    local job_name=integration-test-$RANDOM
    local job_yml=$RESULT_DIR/$job_name.yml
    local job_workspace=$RESULT_DIR/$job_name/workspace
    local uid=`id -u`
    local gid=`id -g`

    local JOBDEF=`cat <<EOF > $job_yml
apiVersion: batch/v1
kind: Job
metadata:
  name: $job_name
spec:
    template:
       metadata:
         name: $job_name
       spec:
         containers:
         - name: $job_name
           image: store/oracle/serverjre:8
           command: ["/workspace/run_test.sh"]
           volumeMounts:
           - name: workspace
             mountPath: /workspace
         restartPolicy: Never
         securityContext:
           runAsUser: $uid
           fsGroup: $gid
         volumes:
         - name: workspace
           hostPath:
             path:  "$job_workspace"
EOF
`
    trace "copying source code, java and mvn into workspace folder to be shared with pod"

    mkdir -p  $job_workspace
    rsync -a $PROJECT_ROOT $job_workspace/weblogic-operator
    rsync -a $M2_HOME/ $job_workspace/apache-maven
    rsync -a $JAVA_HOME/ $job_workspace/java 

    cat <<EOF > $job_workspace/run_test.sh
#!/bin/sh
export M2_HOME=/workspace/apache-maven
export M2=\$M2_HOME/bin
export JAVA_HOME=/workspace/java
export PATH=\$M2:\$JAVA_HOME/bin:\$PATH
set -x
cd /workspace/weblogic-operator
mvn --settings ../settings.xml -P integration-tests clean install > /workspace/mvn.out
EOF


    cat <<EOF > $job_workspace/settings.xml
<settings>
  <proxies>
   <proxy>
      <id>www-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>www-proxy.us.oracle.com</host>
      <port>80</port>
      <nonProxyHosts>*.oraclecorp.com|*.oracle.com|*.us.oracle.com|127.0.0.1</nonProxyHosts>
    </proxy>
   <proxy>
      <id>wwws-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>www-proxy.us.oracle.com</host>
      <port>80</port>
      <nonProxyHosts>*.oraclecorp.com|*.oracle.com|*.us.oracle.com|127.0.0.1</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
EOF

    chmod a+x $job_workspace/run_test.sh

    kubectl create -f $job_yml

    trace "job created to run mvn -P integration-tests clean install , check $job_workspace/mvn.out"

    local status="0"
    local max=20
    local count=1
    while [ ${status:=0} != "1" -a $count -lt $max ] ; do
      sleep 30
      local status=`kubectl get job $job_name | egrep $job_name | awk '{print $3}'`
      trace "kubectl status is ${status:=Error}, iteration $count of $max"
      local count=`expr $count + 1`
    done
    local status=`kubectl get job $job_name | egrep $job_name | awk '{print $3}'`
    if [ ${status:=0} != "1" ] ; then
      fail "ERROR: kubectl get job reports status=${status:=0} after running, exiting!"
    fi

    mvn_build_check $job_workspace/mvn.out

    cat $job_workspace/mvn.out

    kubectl delete job $job_name

    # save the mvn.out somewhere before rm?
    rm -rf $job_workspace
    rm $job_yml
}

function mvn_integration_test_local {

    trace "Running mvn -P integration-tests clean install.  Output in `pwd`/mvn.out"

    which mvn || fail "Error: Could not find mvn in path."

    local mstart=`date +%s`
    mvn -P integration-tests clean install > mvn.out 2>&1
    local mend=`date +%s`
    local msecs=$((mend-mstart))
    trace "mvn complete, runtime $msecs seconds"

    mvn_build_check mvn.out

    docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" --no-cache=true .
    [ "$?" = "0" ] || fail "Error:  Failed to docker tag operator image".
}

function mvn_integration_test_wercker {

    trace "Running mvn -P integration-tests install.  Output in `pwd`/mvn.out"

    local mstart=`date +%s`
    mvn -P integration-tests install | tee mvn.out 2>&1
    local mend=`date +%s`
    local msecs=$((mend-mstart))
    trace "mvn complete, runtime $msecs seconds"

    mvn_build_check mvn.out
    
}

function check_pv {

    trace "Checking if the persistent volume ${1:?} is ${2:?}"
    local pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
    local attempts=0
    while [ ! "$pv_state" = "$2" ] && [ ! $attempts -eq 10 ]; do
        local attempts=$((attempts + 1))
        sleep 1
        local pv_state=`kubectl get pv $1 -o jsonpath='{.status.phase}'`
    done
    if [ "$pv_state" != "$2" ]; then
        fail "The Persistent Volume should be $2 but is $pv_state"
    fi
}

function get_wladmin_cred {
  if [ "$#" != 1 ]; then
    fail "requires one parameter, keyword 'username' or 'password'."
  fi
  # All domains use the same user/pass
  if ! val=`grep "^  $1:" $CUSTOM_YAML/domain1-weblogic-credentials.yaml | awk '{ print $2 }' | base64 -d`
  then
    fail "get_wladmin_cred:  Could not determine $1"
  fi
  echo $val
}

function get_wladmin_pass {
  get_wladmin_cred password
}

function get_wladmin_user {
  get_wladmin_cred username
}

function verify_wlst_access {
  #Note:  uses admin server pod's ADMIN_WLST_PORT

  if [ "$#" != 1 ] ; then
    fail "requires 1 parameter: domainKey"
  fi 

  local DOM_KEY="$1"

  local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
  local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
  local TMP_DIR="`dom_get $1 TMP_DIR`"

  local AS_NAME="$DOMAIN_UID-admin-server"

  local username=`get_wladmin_user` 
  local password=`get_wladmin_pass`
  local tmp_dir="$TMP_DIR"
  local t3url="t3://$host:$ADMIN_WLST_PORT"

  trace "Testing external WLST connectivity to pod $AS_NAME via $t3url ns=$namespace.".

  trace "Verifying java is available."
  # Prepend "+" to detailed debugging to make it easy to filter out
  if ! java -version 2>&1
  then
    fail "Could not run java.  Make sure java is in PATH."
  fi

  local pyfile=$tmp_dir/tmp.py
  trace "Verifying weblogic.WLST is available using \"java weblogic.WLST ${pyfile}\""
  cat << EOF > ${pyfile}
EOF

  if ! java weblogic.WLST ${pyfile} > ${pyfile}.out
  then
    cat ${pyfile}.out
    fail "Could not run WLST.  Make sure a WL env is in PATH and CLASSPATH"
  fi

  local pyfile=$tmp_dir/connect.py
  trace "Verifying connectivity using \"java weblogic.WLST ${pyfile} ${username} somepassword ${t3url}\""
  cat << EOF > ${pyfile}
  connect(sys.argv[1],sys.argv[2],sys.argv[3])
EOF

  # it's theoretically possible for a booting pod to have its default port running but
  # not its t3-channel port, so we retry this call on a failure

  local mstart=`date +%s`
  local maxwaitsecs=180
  while : ; do
    echo
    java weblogic.WLST ${pyfile} ${username} ${password} ${t3url} > ${pyfile}.out 2>&1
    local result="$?"

    # '+' marks verbose tracing
    cat ${pyfile}.out | sed 's/^/+/'

    if [ "$result" = "0" ];
    then 
      break
    fi

    local mnow=`date +%s`
    if [ $((mnow - mstart)) -gt $((maxwaitsecs)) ]; then
      cat ${pyfile}.out
      fail "Could not contact admin server at ${t3url} within ${maxwaitsecs} seconds.  Giving up"
    fi

    trace "Call failed.  Wait time $((mnow - mstart)) seconds (max=${maxwaitsecs}).  Will retry."
    sleep 10
  done

  trace "Passed."
}

#
# deploy_webapp_via_WLST
#
# This function
#   waits until the admin server has established JMX communication with managed servers
#   copies a webapp to the admin pod /shared/applications directory
#   copies a deploy WLST script to the admin pod
#   executes the WLST script from within the pod
#
# It is possible to modify this function to run deploy.py outside of the k8s cluster
# and upload the file.
#
# Note that this function has slightly different parameters than deploy_webapp_via_REST.
#
function deploy_webapp_via_WLST {
    #TODO Instead of sleeping, create a py script that verifies admin server
    #     & managed server JMX communication has been established, and retry
    #     until the script succeeds.
    #     Rose suggests: domainRuntime(); cd("ServerRuntimes"); cd("<managedServer>"); cd("<othermgdServer"); etc

    trace "Sleeping 120 seconds to give time for admin server and cluster to establish communication."
    sleep 120

    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainkey"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local WLS_ADMIN_USERNAME="`get_wladmin_user`"
    local WLS_ADMIN_PASSWORD="`get_wladmin_pass`"

    local as_name="$DOMAIN_UID-admin-server"
    local appname="testwebapp"
    local tmp_dir="$TMP_DIR"

    local t3url_lcl="t3://$host:$ADMIN_WLST_PORT"
    local t3url_pod="t3://$as_name:$ADMIN_WLST_PORT"

    local appwar_lcl="$tmp_dir/testwebapp.war"
    local appwar_pod="/shared/applications/testwebapp.war"

    local pyfile_lcl="$tmp_dir/deploy.py"
    local pyfile_pod="/shared/deploy.py"

    local wlcmdscript_lcl="$tmp_dir/wlcmd.sh"
    local wlcmdscript_pod="/shared/wlcmd.sh"

    cat << EOF > $wlcmdscript_lcl
#!/usr/bin/bash
#
# This is a script for running arbitrary WL command line commands.
# Usage example:  $wlcmdscript_pod java weblogic.version
#
ARG="\$*"
ENVSCRIPT="\`find /shared -name setDomainEnv.sh\`" || exit 1
echo Sourcing \$ENVSCRIPT
. \$ENVSCRIPT || exit 1
echo "\$@"
echo Calling \$ARG
eval \$ARG || exit 1
exit 0
EOF

    local mycommand="kubectl cp $wlcmdscript_lcl ${NAMESPACE}/${as_name}:$wlcmdscript_pod"
    trace "Copying wlcmd to pod $as_name in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl cp command failed."

    local mycommand="kubectl -n ${NAMESPACE} exec -it ${as_name} chmod 777 $wlcmdscript_pod"
    trace "Changing file permissions on pod wlcmd script using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl exec chmod command failed."

    cat << EOF > ${pyfile_lcl}
connect(sys.argv[1],sys.argv[2],sys.argv[3])
deploy(sys.argv[4],sys.argv[5],sys.argv[6],upload='false',remote='false')
# deploy(sys.argv[4],sys.argv[5],sys.argv[6],upload='false',remote='true')
EOF

    local mycommand="kubectl cp ${pyfile_lcl} ${NAMESPACE}/${as_name}:${pyfile_pod}"
    trace "Copying wlst to $DOMAIN_UID in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl cp command failed."

    local mycommand="kubectl cp ${appwar_lcl} ${NAMESPACE}/${as_name}:${appwar_pod}"
    trace "Copying webapp to $DOMAIN_UID in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "kubectl cp command failed."

    #For a remote activated deploy, use commented version below, plus modify above deploy.py script so remote='true'
    #local mycommand=                                                         "java weblogic.WLST ${pyfile_lcl} ${WLS_ADMIN_USERNAME} ${WLS_ADMIN_PASSWORD} ${t3url_lcl} ${appname} ${appwar_pod} ${WL_CLUSTER_NAME} "
    local mycommand="kubectl -n ${NAMESPACE} exec -it ${as_name} $wlcmdscript_pod java weblogic.WLST ${pyfile_pod} ${WLS_ADMIN_USERNAME} ${WLS_ADMIN_PASSWORD} ${t3url_pod} ${appname} ${appwar_pod} ${WL_CLUSTER_NAME} "
    trace "Deploying webapp to $DOMAIN_UID in namespace $NAMESPACE using \"$mycommand\""
    eval "$mycommand" 2>&1 || fail "Deploy command failed."

    trace "Done."
}

function verify_service_and_pod_created {
    if [ "$#" != 3 ] ; then
      fail "requires 3 parameters: domainkey operatorKey serverNum, set serverNum to 0 to indicate the admin server"
    fi

    local DOM_KEY="${1}"
    local OP_KEY="${2}"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local ADMIN_WLST_PORT="`dom_get $1 ADMIN_WLST_PORT`"

    local OPERATOR_NS="`op_get $2 NAMESPACE`"
    local OPERATOR_TMP_DIR="`op_get $2 TMP_DIR`"

    local SERVER_NUM="$3"

    if [ "$SERVER_NUM" = "0" ]; then 
      local IS_ADMIN_SERVER="true"
      local POD_NAME="${DOMAIN_UID}-admin-server"
    else
      local IS_ADMIN_SERVER="false"
      local POD_NAME="${DOMAIN_UID}-${MS_BASE_NAME}${SERVER_NUM}"
    fi

    local SERVICE_NAME="${POD_NAME}"

    local PV="pv"
    local PV_DIR="persistentVolume-${DOMAIN_UID}"

    local max_count_srv=50
    local max_count_pod=50
    local wait_time=10
    local count=1
    local srv_count=0

    trace "checking if service $SERVICE_NAME is created"
    while [ "${srv_count:=Error}" != "1" -a $count -lt $max_count_srv ] ; do
      local count=`expr $count + 1`
      local srv_count=`kubectl -n $NAMESPACE get services | grep "^$SERVICE_NAME " | wc -l`
      if [ "${srv_count:=Error}" != "1" ]; then
        trace "Did not find service $SERVICE_NAME, iteration $count of $max_count_srv"
        sleep $wait_time
      fi
    done

    if [ "${srv_count:=Error}" != "1" ]; then
      local pod=`kubectl get pod -n $OPERATOR_NS | grep $OPERATOR_NS | awk '{ print $1 }'`
      local debuglog="${OPERATOR_TMP_DIR}/verify_domain_debugging.log"
      kubectl logs $pod -n $OPERATOR_NS > "${debuglog}"
      if [ -f ${debuglog} ] ; then
        tail -20 ${debuglog}
      fi

      fail "ERROR: the service $SERVICE_NAME is not created, exiting!"
    fi

    if [ "${IS_ADMIN_SERVER}" == "true" ]; then
      local EXTCHANNEL_T3CHANNEL_SERVICE_NAME=${SERVICE_NAME}-extchannel-t3channel
      trace "checking if service ${EXTCHANNEL_T3CHANNEL_SERVICE_NAME} is created"
      while [ "${srv_count:=Error}" != "1" -a $count -lt $max_count_srv ] ; do
        local count=`expr $count + 1`
        local srv_count=`kubectl -n $NAMESPACE get services | grep "^$SERVICE_NAME " | wc -l`
        if [ "${srv_count:=Error}" != "1" ]; then
          trace "Did not find service $EXTCHANNEL_T3CHANNEL_SERVICE_NAME, iteration $count of $max_count_srv"
          sleep $wait_time
        fi
      done
    fi

    if [ "${srv_count:=Error}" != "1" ]; then
      local pod=`kubectl get pod -n $OPERATOR_NS | grep $OPERATOR_NS | awk '{ print $1 }'`
      local debuglog="${OPERATOR_TMP_DIR}/verify_domain_debugging.log"
      kubectl logs $pod -n $OPERATOR_NS > "${debuglog}"
      if [ -f ${debuglog} ] ; then
        tail -20 ${debuglog}
      fi

      fail "ERROR: the service $EXTCHANNEL_T3CHANNEL_SERVICE_NAME is not created, exiting!"
    fi

    trace "checking if pod $POD_NAME is successfully started"
    local status="NotRunning"
    local count=1
    while [ ${status:=Error} != "Running" -a $count -lt $max_count_pod ] ; do
      local status=`kubectl -n $NAMESPACE describe pod $POD_NAME | grep "^Status:" | awk ' { print $2; } '`
      local count=`expr $count + 1`
      if [ ${status:=Error} != "Running" ] ; then
        trace "pod status is ${status:=Error}, iteration $count of $max_count_pod"
        sleep $wait_time
      fi
    done

    if [ ${status:=Error} != "Running" ] ; then
      fail "ERROR: pod $POD_NAME is not running, exiting!"
    fi

    trace "checking if pod $POD_NAME is ready"
    local ready="false"
    local count=1
    while [ ${ready:=Error} != "true" -a $count -lt $max_count_pod ] ; do
      local ready=`kubectl -n $NAMESPACE get pod $POD_NAME -o jsonpath='{.status.containerStatuses[0].ready}'`
      local count=`expr $count + 1`
      if [ ${ready:=Error} != "true" ] ; then
        trace "pod readiness is ${ready:=Error}, iteration $count of $max_count_pod"
        sleep $wait_time
      fi
    done
    if [ ${ready:=Error} != "true" ] ; then
      fail "ERROR: pod $POD_NAME is not ready, exiting!"
    fi

    if [ "${IS_ADMIN_SERVER}" = "true" ]; then
      verify_wlst_access $DOM_KEY
    fi
}


function verify_domain_created {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi 

    local DOM_KEY="$1"
    local OP_KEY="$2"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"

    trace "verify domain $DOMAIN_UID in $NAMESPACE namespace"

    # Prepend "+" to detailed debugging to make it easy to filter out

    kubectl get all -n $NAMESPACE --show-all 2>&1 | sed 's/^/+/' 2>&1

    kubectl get domains  -n $NAMESPACE 2>&1 | sed 's/^/+/' 2>&1

    trace 'checking if the domain is created'
    local count=`kubectl get domain $DOMAIN_UID -n $NAMESPACE |grep "^$DOMAIN_UID " | wc -l `
    if [ ${count:=Error} != 1 ] ; then
      fail "ERROR: domain not found, exiting!"
    fi

    trace "verify the service and pod of admin server"
    verify_service_and_pod_created $DOM_KEY $OP_KEY 0

    local replicas=`get_cluster_replicas $DOM_KEY`

    trace "verify $replicas number of managed servers for creation"
    local i
    for i in $(seq 1 $replicas);
    do
      local MS_NAME="$DOMAIN_UID-${MS_BASE_NAME}$i"
      trace "verify service and pod of server $MS_NAME"
      verify_service_and_pod_created $DOM_KEY $OP_KEY $i
    done

    # Check if we got exepcted number of managed servers running
    local ms_name_common=${DOMAIN_UID}-${MS_BASE_NAME}
    local pod_count=`kubectl get pods -n $NAMESPACE |grep "^${ms_name_common}" | wc -l `
    if [ ${pod_count:=Error} != $replicas ] ; then
      fail "ERROR: expected $replicas number of managed servers running, but got $pod_count, exiting!"
    fi
}

function verify_service_and_pod_deleted {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainkey serverNum, set serverNum to 0 to indicate the admin server"
    fi

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"

    local SERVER_NUM="$2"

    if [ "$SERVER_NUM" = "0" ]; then 
      local POD_NAME="${DOMAIN_UID}-admin-server"
    else
      local POD_NAME="${DOMAIN_UID}-${MS_BASE_NAME}${SERVER_NUM}"
    fi

    local SERVICE_NAME="${POD_NAME}"

    local max_count_srv=50
    local max_count_pod=50
    local wait_time=10
    local count=1
    local srv_count=1

    trace "checking if service $SERVICE_NAME is deleted"
    while [ "${srv_count:=Error}" != "0" -a $count -lt $max_count_srv ] ; do
      local count=`expr $count + 1`
      local srv_count=`kubectl -n $NAMESPACE get services | grep "^$SERVICE_NAME " | wc -l`
      if [ "${srv_count:=Error}" != "0" ]; then
        trace "service $SERVICE_NAME still exists, iteration $count of $max_count_srv"
        sleep $wait_time
      fi
    done

    if [ "${srv_count:=Error}" != "0" ]; then
      fail "ERROR: the service $SERVICE_NAME is not deleted, exiting!"
    fi

    trace "checking if pod $POD_NAME is deleted"
    local pod_count=1
    local count=1

    while [ "${pod_count:=Error}" != "0" -a $count -lt $max_count_pod ] ; do
      local pod_count=`kubectl -n $NAMESPACE get pod $POD_NAME | grep "^$POD_NAME " | wc -l`
      local count=`expr $count + 1`
      if [ "${pod_count:=Error}" != "0" ] ; then
        trace "pod $POD_NAME still exists, iteration $count of $max_count_srv"
        sleep $wait_time
      fi
    done

    if [ "${pod_count:=Error}" != "0" ] ; then
      fail "ERROR: pod $POD_NAME is not deleted, exiting!"
    fi
}

function verify_domain_deleted {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey managedServerCount"
    fi 
    trace Begin. 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"

    local MS_NUM="$2"

    trace "verify domain $DOMAIN_UID in $NAMESPACE namespace is deleted"

    # Prepend "+" to detailed debugging to make it easy to filter out

    kubectl get all -n $NAMESPACE --show-all 2>&1 | sed 's/^/+/' 2>&1

    kubectl get domains  -n $NAMESPACE 2>&1 | sed 's/^/+/' 2>&1

    trace 'checking if the domain is deleted'
    local count=`kubectl get domain $DOMAIN_UID -n $NAMESPACE | egrep $DOMAIN_UID | wc -l `
    if [ ${count:=Error} != 0 ] ; then
      fail "ERROR: domain still exists, exiting!"
    fi

    trace "verify the service and pod of admin server is deleted"
    verify_service_and_pod_deleted $DOM_KEY 0

    trace "verify $MS_NUM number of managed servers for deletion"
    local i
    for i in $(seq 1 $MS_NUM);
    do
      trace "verify service and pod of managed server $i is deleted"
      verify_service_and_pod_deleted $DOM_KEY $i
    done
    trace Done. Verified.
}

function shutdown_domain {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    trace Begin. 
    local DOM_KEY="$1"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local replicas=`get_cluster_replicas $DOM_KEY`

    kubectl delete -f ${TMP_DIR}/domain-custom-resource.yaml
    verify_domain_deleted $DOM_KEY $replicas
    trace Done. 
}

function startup_domain {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi 

    local DOM_KEY="$1"
    local OP_KEY="$2"

    local TMP_DIR="`dom_get $1 TMP_DIR`"

    kubectl create -f ${TMP_DIR}/domain-custom-resource.yaml

    verify_domain_created $DOM_KEY $OP_KEY
}

function test_domain_lifecycle {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi 

    local DOM_KEY="$1"
    local OP_KEY="$2"

    shutdown_domain $DOM_KEY
    startup_domain $DOM_KEY $OP_KEY
    verify_domain_exists_via_oper_rest $DOM_KEY $OP_KEY
}

function shutdown_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    kubectl delete -f $TMP_DIR/weblogic-operator.yaml
    trace "Checking REST service is deleted"
    local servicenum=`kubectl get services -n $OPERATOR_NS | egrep weblogic-operator-service | wc -l`
    trace "servicenum=$servicenum"
    if [ "$servicenum" != "0" ]; then
        fail 'operator fail to be deleted'
    fi
}

function startup_operator {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local OPERATOR_NS="`op_get $OP_KEY NAMESPACE`"
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    kubectl create -f $TMP_DIR/weblogic-operator.yaml

    local namespace=$OPERATOR_NS
    trace Waiting for operator deployment to be ready...
    local AVAILABLE="0"
    local max=30
    local count=1
    while [ "$AVAILABLE" != "1" -a $count -lt $max ] ; do
        sleep 10
        local AVAILABLE=`kubectl get deploy weblogic-operator -n $namespace -o jsonpath='{.status.availableReplicas}'`
        trace "status is $AVAILABLE, iteration $count of $max"
        local count=`expr $count + 1`
    done

    if [ "$AVAILABLE" != "1" ]; then
        kubectl get deploy weblogic-operator -n ${namespace}
        kubectl describe deploy weblogic-operator -n ${namespace}
        kubectl describe pods -n ${namespace}
        fail "The WebLogic operator deployment is not available, after waiting 300 seconds"
    fi

    trace "Checking the operator pods"
    local REPLICA_SET=`kubectl describe deploy weblogic-operator -n ${namespace} | grep NewReplicaSet: | awk ' { print $2; }'`
    local POD_TEMPLATE=`kubectl describe rs ${REPLICA_SET} -n ${namespace} | grep ^Name: | awk ' { print $2; } '`
    local PODS=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | wc | awk ' { print $1; } '`
    local POD=`kubectl get pods -n ${namespace} | grep $POD_TEMPLATE | awk ' { print $1; } '`

    if [ "$PODS" != "1" ]; then
        fail "There should be one operator pod running"
    fi

    trace Checking the operator Pod status
    local POD_STATUS=`kubectl describe pod $POD -n ${namespace} | grep "^Status:" | awk ' { print $2; } '`
    if [ "$POD_STATUS" != "Running" ]; then
        fail "The operator pod status should be Running"
    fi

    trace "Checking REST service is running"
    local REST_SERVICE=`kubectl get services -n ${namespace} -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-service")]}'`
    if [ -z "$REST_SERVICE" ]; then
        fail 'operator rest service was not created'
    fi
}

function verify_no_domain_via_oper_rest {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: operatorKey"
    fi
    local OP_KEY=${1}
    local TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local OPER_CURL_RESPONSE_BODY="$TMP_DIR/operator.rest.response.body"
    call_operator_rest $OP_KEY "operator/latest/domains"
    # verify that curl returned an empty list of domains, e.g. { ..., "items": [], ... }
    local DOMAIN_COUNT=`cat ${OPER_CURL_RESPONSE_BODY} | processJson 'print(len(j["items"]))'`
    if [ "${DOMAIN_COUNT}" != "0" ]; then
        fail "expect no domain CR created but return $DOMAIN_COUNT domain(s)"
    fi 
}

function verify_domain_exists_via_oper_rest {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi
    local DOM_KEY=${1}
    local OP_KEY=${2}
    local DOMAIN_UID="`dom_get $DOM_KEY DOMAIN_UID`"
    local OPERATOR_TMP_DIR="`op_get $OP_KEY TMP_DIR`"

    local OPER_CURL_RESPONSE_BODY="$OPERATOR_TMP_DIR/operator.rest.response.body"
    call_operator_rest $OP_KEY "operator/latest/domains" 
    local DOMAIN_COUNT=`cat ${OPER_CURL_RESPONSE_BODY} | processJson 'print(len(j["items"]))'`
    if [ "${DOMAIN_COUNT}" != "1" ]; then
        fail "expect one domain CR created but return $DOMAIN_COUNT domain(s)"
    fi

    call_operator_rest $OP_KEY "operator/latest/domains/$DOMAIN_UID"
}

function test_operator_lifecycle {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi

    local DOM_KEY=${1}
    local OP_KEY=${2}

    trace 'begin'
    shutdown_operator $OP_KEY
    startup_operator $OP_KEY
    verify_domain_exists_via_oper_rest $DOM_KEY $OP_KEY
    verify_domain_created $DOM_KEY $OP_KEY
    trace 'end'
}

function test_cluster_scale {
    if [ "$#" != 2 ] ; then
      fail "requires 2 parameters: domainKey operatorKey"
    fi 

    local DOM_KEY="$1"
    local OP_KEY="$2"

    local TMP_DIR="`dom_get $1 TMP_DIR`"

    local cur_replicas="`get_cluster_replicas $DOM_KEY`"
    [ "$cur_replicas" = "2" ] || fail "Expected domain CRD to specify 2 replicas."

    trace "test cluster scale-up from 2 to 3"
    local domainCR="$TMP_DIR/domain-custom-resource.yaml"
    sed -i -e "0,/replicas:/s/replicas:.*/replicas: 3/"  $domainCR
    kubectl apply -f $domainCR

    verify_service_and_pod_created $DOM_KEY $OP_KEY 3
    verify_webapp_load_balancing $DOM_KEY 3

    trace "test cluster scale-down from 3 to 2"
    sed -i -e "0,/replicas:/s/replicas:.*/replicas: 2/"  $domainCR
    kubectl apply -f $domainCR

    verify_service_and_pod_deleted $DOM_KEY 3
    verify_webapp_load_balancing $DOM_KEY 2 
}

function test_create_domain_on_exist_dir {
    if [ "$#" != 1 ] ; then
      fail "requires 1 parameter: domainKey"
    fi 

    local DOM_KEY="$1"

    local NAMESPACE="`dom_get $1 NAMESPACE`"
    local DOMAIN_UID="`dom_get $1 DOMAIN_UID`"
    local WL_CLUSTER_NAME="`dom_get $1 WL_CLUSTER_NAME`"
    local MS_BASE_NAME="`dom_get $1 MS_BASE_NAME`"
    local TMP_DIR="`dom_get $1 TMP_DIR`"

    trace "check domain directory exists"
    local tmp_dir="$TMP_DIR"
    local persistence_path=`egrep 'persistencePath' ${tmp_dir}/create-domain-job-inputs.yaml | awk '{print $2}'`
    local domain_name=`egrep 'domainName' ${tmp_dir}/create-domain-job-inputs.yaml | awk '{print $2}'`
    local domain_dir=${persistence_path}"/domain/"${domain_name}
    if [ ! -d ${domain_dir} ] ; then
      fail "ERROR: the domain directory ${domain_dir} does not exist, exiting!"
    fi

    trace "run the script to create the domain"
    sh ${tmp_dir}/create-domain-job.sh -i ${tmp_dir}/create-domain-job-inputs.yaml
    local exit_code=$?
    if [ ${exit_code} -eq 1 ] ; then
      trace "[SUCCESS] create domain job failed, this is the expected behavior"
    else
      trace "[FAIL] unexpected result, create domain job exit code: "${exit_code}
      fail "test_create_domain_on_exist_dir failed!"
    fi
}

function verify_elk_integration {
    trace 'verify elk integration (nyi)'
}


function test_suite {

    trace "******************************************************************************************"
    trace "***                                                                                    ***"
    trace "***    This is the Oracle WebLogic Server Kubernetes Operator Acceptance Test Suite    ***"
    trace "***                                                                                    ***"
    trace "******************************************************************************************"
    
    # SECONDS is a special bash reserved variable that auto-increments every second
    SECONDS=0
    
    local SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
    export CUSTOM_YAML="$SCRIPTPATH/../kubernetes"
    export PROJECT_ROOT="$SCRIPTPATH/../../.."
    export RESULT_ROOT=${RESULT_ROOT:-/scratch/k8s_dir}
    export RESULT_DIR="$RESULT_ROOT/acceptance_test_tmp"
    export host=${K8S_HOST:-`hostname | awk -F. '{print $1}'`}

    if [ "$JENKINS" = "true" ]; then
    
      trace "Test Suite is running on Jenkins"

      export BRANCH_NAME=${BRANCH_NAME:?}
      export docker_pass=${docker_pass:?}
      export M2_HOME=${M2_HOME:?}
      export JVM_ARGS=${JVM_ARGS}
     
      trace "Current git branch=$BRANCH_NAME"
     
    elif [ "$WERCKER" = "true" ]; then
    	
      trace "Test Suite is running on Wercker"
    	
    else
    
      trace "Test Suite is running locally"
    
      export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
      [ ! "$?" = "0" ] && fail "Error: Could not determine branch.  Run script from within a git repo".
    
      # No need to check M2_HOME or docker_pass -- not used by local runs

      trace "Current git branch=$BRANCH_NAME"
    
    fi
    
    # Convert the git branch name into a docker image tag.
    #
    # TODO - are there any other valid branch name characters other than '/'
    # that are not valid in a docker image version?
    #
    # Docker tag restrictions:
    #   A tag name must be valid ASCII and may contain lowercase and uppercase letters,
    #   digits, underscores, periods and dashes. A tag name may not start with a period
    #   or a dash and may contain a maximum of 128 characters.
    #
    # Git branch name restrictions:
    #   A Git branch name can not:
    #     Have a path component that begins with "."
    #     Have a double dot "…"
    #     Have an ASCII control character, "~", "^", ":" or SP, anywhere
    #     End with a "/"
    #     End with ".lock"
    #     Contain a "\" (backslash)
    
    export IMAGE_TAG=${IMAGE_TAG:-`echo "test_${BRANCH_NAME}" | sed "s#/#_#g"`}
    export IMAGE_NAME=${IMAGE_NAME:-wlsldi-v2.docker.oraclecorp.com/weblogic-operator}
    export IMAGE_PULL_POLICY=${IMAGE_PULL_POLICY:-Never}
    
    set -x

    if [ "$JENKINS" = "true" ]; then
    
      trace 'Cleaning...'
      clean
    
      trace 'Setup...'
      setup
      local duration=$SECONDS
      trace "Setting up env spent $(($duration / 60)) minutes and $(($duration % 60)) seconds."
    
      trace 'Running mvn integration tests...'
      mvn_integration_test
    
      create_image_pull_secret
    
    elif [ "$WERCKER" = "true" ]; then

      trace 'Running mvn integration tests...'
      mvn_integration_test_wercker
    	
    else
    
      cd $PROJECT_ROOT || fail "Could not cd to $PROJECT_ROOT"
    
      trace 'Setup...'
      setup_local
    
      # If host pv directories exist, check that they are 777 and empty.

      local pvdir    
      for pvdir in $RESULT_DIR/persistentVolume-domain1 \
                   $RESULT_DIR/persistentVolume-domain2 \
                   $RESULT_DIR/persistentVolume-domain3 \
                   $RESULT_DIR/persistentVolume-domain4 \
                   $RESULT_DIR/elkPersistentVolume; do
        if [ -d $pvdir ]; then
          [ "777" = "`stat --format '%a' $pvdir`" ] || fail "Error: $pvdir found but permission != 777"
          [ "0" = "`ls $pvdir | wc -l`" ] && fail "Error:  $pvdir must not exist or be empty"
        fi
      done
    
      trace 'Running mvn integration tests...'
      mvn_integration_test_local
    
      kubectl create namespace weblogic-operator
    fi

    #          OP_KEY               NAMESPACE            TARGET_NAMESPACES  EXTERNAL_REST_HTTPSPORT
    op_define  weblogic-operator    weblogic-operator    "default,test"     31001
    op_define  weblogic-operator-2  weblogic-operator-2  test2              32001


    #          DOM_KEY  NAMESPACE DOMAIN_UID WL_CLUSTER_NAME MS_BASE_NAME   ADMIN_PORT ADMIN_WLST_PORT ADMIN_NODE_PORT MS_PORT LOAD_BALANCER_WEB_PORT LOAD_BALANCER_ADMIN_PORT
    dom_define domain1  default   domain1    cluster-1       managed-server 7001       30012           30701           8001    30305                  30315
    dom_define domain2  default   domain2    cluster-1       managed-server 7011       30031           30702           8021    30306                  30316
    dom_define domain3  test      domain3    cluster-1       managed-server 7021       30041           30703           8031    30307                  30317
    dom_define domain4  test2     domain4    cluster-1       managed-server 7041       30051           30704           8041    30308                  30318

    kubectl create namespace test
    kubectl create namespace test2
    
    trace 'Running integration tests...'
    test_first_operator weblogic-operator
    verify_elk_integration

    # create first domain in default namespace and verify it
    test_domain_creation    domain1 weblogic-operator
    
    test_domain_lifecycle   domain1 weblogic-operator
    test_operator_lifecycle domain1 weblogic-operator
    test_cluster_scale      domain1 weblogic-operator 
    
    # create another domain in the default namespace and verify it
    test_domain_creation domain2 weblogic-operator
    
    # shutdown domain2 before create domain3
    shutdown_domain domain2
    
    # create another domain in the test namespace and verify it
    test_domain_creation domain3 weblogic-operator
    
    # shutdown domain3
    shutdown_domain domain3
    
    # Create another operator in weblogic-operator-2 namespace, managing namesapce test2
    # the only remaining running domain should not be affacted
    deploy_operator weblogic-operator-2 
    verify_domain domain1 weblogic-operator
    
    # create another domain in the test2 namespace and verify it
    test_domain_creation domain4 weblogic-operator-2

    # Make sure scale up a domain managed by one operator does not impact other domains
    test_cluster_scale domain4 weblogic-operator-2
    verify_domain domain1 weblogic-operator
  
    # scale domain1 up and down, make sure that domain4 is not impacted
    test_domain_lifecycle domain1 weblogic-operator
    verify_domain domain4 weblogic-operator-2

    # test managed server 1 pod auto-restart
    test_wls_liveness_probe domain1
    
    # TODO temporarily commenting out test:
    #    this test failed in Jenkins run #895 during setup its setup with
    #    "ERROR: the domain directory ${domain_dir} does not exist, exiting!"
    # shutdown_domain domain1
    # test_create_domain_on_exist_dir domain1
    
    state_dump logs

    local duration=$SECONDS
    trace "Running integration tests spent $(($duration / 60)) minutes and $(($duration % 60)) seconds."
    
    
    set +x
    trace "******************************************************************************************"
    trace "***                                                                                    ***"
    trace "***    Oracle WebLogic Server Kubernetes Operator Acceptance Test Suite Completed !!   ***"
    trace "***                                                                                    ***"
    trace "******************************************************************************************"
} 


# entry point

if [ "${FOREGROUND:-false}" = "true" ] || [ "$JENKINS" = "true" ] || [ "$WERCKER" = "true" ]; then
    test_suite
else
    export TESTOUT=/tmp/test_suite.out
    trace See $TESTOUT for full trace.
    # Ultra-detailed debugging begins with a "+"
    test_suite 2>&1 | tee /tmp/test_suite.out | grep -v "^+"
    trace See $TESTOUT for full trace.
fi



