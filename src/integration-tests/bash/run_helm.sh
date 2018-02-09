#!/bin/bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.

function must_be_root {

    id=`id -u`
    if [ $id -ne 0 ] ; then
      echo "ERROR you must run this script with sudo: id = $id"
      exit 1
    fi

}

function clean {
   echo y |  /usr/local/packages/aime/ias/run_as_root ${SCRIPTPATH}/clean_docker_k8s.sh
}

function setup {
    export real_user=wls
    echo y |  /usr/local/packages/aime/ias/run_as_root "sh ${SCRIPTPATH}/install_docker_k8s.sh ${K8S_VERSION}"
    set +x
    . ~/.dockerk8senv
    set -x
    id

    echo "Pull and tag the images we need"

    docker login -u teamsldi_us@oracle.com -p $docker_pass  wlsldi-v2.docker.oraclecorp.com
    docker images

    docker pull wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest store/oracle/weblogic:12.2.1.3

    docker pull wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest
    docker tag wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest store/oracle/serverjre:8

    docker images

  
    #'host' is needed by deploy_operator and verify_wlst_access, was set in install, but now must be set here
    export host=`hostname | awk -F. '{print $1}'`

    echo "Helm installation starts" 
    wget -q -O  /tmp/helm-v2.7.2-linux-amd64.tar.gz https://kubernetes-helm.storage.googleapis.com/helm-v2.7.2-linux-amd64.tar.gz
    mkdir /tmp/helm
    tar xzf /tmp/helm-v2.7.2-linux-amd64.tar.gz -C /tmp/helm
    chmod +x /tmp/helm/linux-amd64/helm
    /usr/local/packages/aime/ias/run_as_root "cp /tmp/helm/linux-amd64/helm /usr/bin/"
    rm -rf /tmp/helm
    helm init
    echo "Helm is configured."
}

function create_image_pull_secret {

    echo "Creating Secret"
    kubectl create secret docker-registry wlsldi-secret  \
    --docker-server=wlsldi-v2.docker.oraclecorp.com \
    --docker-username=teamsldi_us@oracle.com \
    --docker-password=$docker_pass \
    --docker-email=teamsldi_us@oracle.com

    echo "Checking Secret"
    SECRET=`kubectl get secret wlsldi-secret | grep wlsldi | wc | awk ' { print $1; }'`
    if [ "$SECRET" != "1" ]; then
        fail 'secret wlsldi-secret was not created successfully'
    fi

}

function create_helm_chart {
    echo "Helm chart package creation"
    mkdir /tmp/helm-charts
    mkdir /tmp/internal
    cp $PROJECT_ROOT/kubernetes/helm-charts/create-helm-certificates.sh /tmp/helm-charts/create-helm-certificates.sh
    cp $PROJECT_ROOT/kubernetes/internal/generate-weblogic-operator-cert.sh /tmp/internal/generate-weblogic-operator-cert.sh
    /tmp/helm-charts/create-helm-certificates.sh weblogic-operator DNS:`hostname` $PROJECT_ROOT/kubernetes/helm-charts/weblogic-operator/values-template.yaml $PROJECT_ROOT/kubernetes/helm-charts/weblogic-operator/values.yaml
}

function deploy_helm_chart {
    echo "Creating target namespace domain1"
    kubectl create namespace domain1
    echo "Helm package install"
    cd $PROJECT_ROOT/kubernetes/helm-charts
    helm install weblogic-operator --name operator --namespace weblogic-operator --set targetNamespaces=domain1
    sleep 30
    kubectl get customresourcedefinitions
    echo "Creating secret for domain1"
    kubectl -n domain1 create secret generic domain1-weblogic-credentials --from-literal=username=weblogic --from-literal=password=welcome1
    helm install weblogic-domain --name domain --namespace domain1
    confirm-operator-rest
}

function deploy_webapp {

    echo 'deploy the web app'

}

function update_load_balancer_rules {

    echo 'update load balancer rules'

}

function confirm_load_balancing {

    echo 'confirm the load balancer is working'

}

function extra_weblogic_checks {

    echo 'Pani will contribute some extra checks here'

}

function confirm-operator-rest {

    echo "Checking REST service is running"
    REST_SERVICE=`kubectl get services -n weblogic-operator -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-service")]}'`
    if [ ! -z $REST_SERVICE ]; then
        fail 'operator rest service was not created'
    fi

    echo "Calling some operator REST APIs"
    REST_IP=`kubectl get services -n weblogic-operator -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-service")].spec.clusterIP}'`
    REST_PORT=`kubectl get services -n weblogic-operator -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-service")].spec.ports[?(@.name == "rest-https")].nodePort}'`
    REST_ADDR="https://${REST_IP}:${REST_PORT}"
    SECRET=`kubectl get serviceaccount weblogic-operator -n weblogic-operator -o jsonpath='{.secrets[0].name}'`
    TOKEN=`kubectl get secret ${SECRET} -n weblogic-operator -o jsonpath='{.data.token}'`
    CURL="curl -k -H \"Authorization: Bearer ${TOKEN}\" -H Accept:application/json -X GET ${REST_ADDR}/operator"
    echo "TODO e.g. ${CURL}"

}

# eventually use this to check the json output from an Operator REST curl command, e.g.
#NAME=`${CURL} | processJson 'print j["items"][0]["name"])'`
#
#function processJson {
#  python -c "
#import sys, json
#j=json.load(sys.stdin)
#$1
#"
#}

function fail {

    echo "[ERROR] $1"
    exit 1

}

function mvn_integration_test {

    echo "generating job to run mvn -P integration-tests clean install "

    job_name=integration-test-$RANDOM
    job_yml=/tmp/$job_name.yml
    job_workspace=/tmp/$job_name/workspace
    uid=`id -u`
    gid=`id -g`

    JOBDEF=`cat <<EOF > $job_yml
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
    echo "copying source code and mvn into workspace folder to be shared with pod"

    mkdir -p  $job_workspace
    rsync -a $PROJECT_ROOT $job_workspace/weblogic-operator
    rsync -a $M2_HOME/ $job_workspace/apache-maven


    cat <<EOF > $job_workspace/run_test.sh
#!/bin/sh
export M2_HOME=/workspace/apache-maven
export M2=\$M2_HOME/bin
export PATH=\$M2:$PATH
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

    echo "job created to run mvn -P integration-tests clean install , check $job_workspace/mvn.out"

    status="0"
    max=20
    count=1
    while [ ${status:=0} != "1" -a $count -lt $max ] ; do
      sleep 30
      status=`kubectl get job $job_name | egrep $job_name | awk '{print $3}'`
      echo "kubectl status is ${status:=Error}, iteration $count of $max"
      count=`expr $count + 1`
    done
    status=`kubectl get job $job_name | egrep $job_name | awk '{print $3}'`
    if [ ${status:=0} != "1" ] ; then
      echo "ERROR: kubectl get job reports status=${status:=0} after running, exiting!"
      exit 1
    fi

    fail_out=`grep FAIL $job_workspace/mvn.out`
    success_out=`grep SUCCESS $job_workspace/mvn.out`

    [ "$fail_out" != "" ] && "echo ERROR: found FAIL in output $job_workspace/mvn.out" && exit 1
    [ "$success_out" = "" ] && "echo ERROR: didn't find SUCCESS in output $job_workspace/mvn.out" && exit 1

    kubectl delete job $job_name

    # save the mvn.out somewhere before rm?
    rm -rf $job_workspace
    rm $job_yml
}


# entry point

echo "******************************************************************************************"
echo "***                                                                                    ***"
echo "***    This is the Oracle WebLogic Server Kubernetes Operator Acceptance Test Suite    ***"
echo "***                                                                                    ***"
echo "******************************************************************************************"

set -x
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
CUSTOM_YAML="$SCRIPTPATH/../kubernetes"
PROJECT_ROOT="$SCRIPTPATH/../../.."

echo 'Cleaning...'
clean

echo 'Setup...'
setup

echo 'Running integration tests...'
mvn_integration_test
create_image_pull_secret
create_helm_chart
deploy_helm_chart
deploy_webapp
update_load_balancer_rules
confirm_load_balancing
extra_weblogic_checks

set +x
echo "******************************************************************************************"
echo "***                                                                                    ***"
echo "***    Oracle WebLogic Server Kubernetes Operator Acceptance Test Suite Completed !!   ***"
echo "***                                                                                    ***"
echo "******************************************************************************************"





