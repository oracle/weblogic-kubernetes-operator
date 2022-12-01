#!/bin/bash
# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Bring up Oracle DB Instance in [default] NameSpace with a NodePort Service 

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

usage() {
  echo "usage: ${script} [-a <dbasecret>] [-p <nodeport>] [-i <image>] [-s <pullsecret>] [-n <namespace>] [-h]"
  echo "  -a DB Sys Account Password Secret Name (optional)"
  echo "      (default: oracle-db-secret, secret must include a key named 'password')"
  echo "      If this secret is not deployed, then the database will not successfully deploy."
  echo "  -p DB Service NodePort (optional)"
  echo "      (default: 30011, set to 'none' to deploy service without a NodePort)"
  echo "  -i Oracle DB Image (optional)"
  echo "      (default: container-registry.oracle.com/database/enterprise:12.2.0.1-slim)"
  echo "  -s DB Image Pull Secret (optional)"
  echo "      (default: docker-store) "
  echo "      If this secret is not deployed, then Kubernetes will try pull anonymously."
  echo "  -n Configurable Kubernetes NameSpace for Oracle DB Service (optional)"
  echo "      (default: default) "
  echo "  -h Help"
  exit $1
}

syssecret="oracle-db-secret"
nodeport=30011
dbimage="container-registry.oracle.com/database/enterprise:12.2.0.1-slim"
pullsecret="docker-store"
namespace="default"

while getopts ":a:p:i:s:n:h:" opt; do
  case $opt in
    a) syssecret="${OPTARG}"
    ;;
    p) nodeport="${OPTARG}"
    ;;
    i) dbimage="${OPTARG}"
    ;;
    s) pullsecret="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

echo "Checking Status for NameSpace [$namespace]"
domns=`${KUBERNETES_CLI:-kubectl} get ns ${namespace} | grep ${namespace} | awk '{print $1}'`
if [ -z "${domns}" ]; then
 echo "Adding NameSpace[$namespace] to Kubernetes Cluster"
 ${KUBERNETES_CLI:-kubectl} create namespace ${namespace}
 sleep 5
else
 echo "Skipping the NameSpace[$namespace] Creation ..."
fi

echo "NodePort[$nodeport] ImagePullSecret[$pullsecret] Image[${dbimage}] NameSpace[${namespace}] SysSecret[${syssecret}]"

#create unique db yaml file if does not exists
dbYaml=${scriptDir}/common/oracle.db.${namespace}.yaml
if [ ! -f "$dbYaml" ]; then
    echo "$dbYaml does not exist."
    cp ${scriptDir}/common/oracle.db.yaml ${dbYaml}
fi

# Modify ImagePullSecret and DatabaseImage based on input
sed -i -e '$d' ${dbYaml}
echo '           - name: docker-store' >> ${dbYaml}
sed -i -e "s?name: docker-store?name: ${pullsecret}?g" ${dbYaml}
sed -i -e "s?image:.*?image: ${dbimage}?g" ${dbYaml}
sed -i -e "s?namespace:.*?namespace: ${namespace}?g" ${dbYaml}

# Modify DBA sys password secret based on input
sed -i -e "s?oracle-db-secret?${syssecret}?g" ${dbYaml}

# Modify the NodePort based on input 
if [ "${nodeport}" = "none" ]; then
  sed -i -e "s? nodePort:? #nodePort:?g" ${dbYaml}
  sed -i -e "s? type:.*LoadBalancer? #type: LoadBalancer?g" ${dbYaml}
else
  sed -i -e "s?[#]*nodePort:.*?nodePort: ${nodeport}?g" ${dbYaml}
  sed -i -e "s?[#]*type:.*LoadBalancer?type: LoadBalancer?g" ${dbYaml} # default type is ClusterIP
fi

${KUBERNETES_CLI:-kubectl} delete service oracle-db -n ${namespace} --ignore-not-found

echo "Applying Kubernetes YAML file '${dbYaml}' to start database."
${KUBERNETES_CLI:-kubectl} apply -f ${dbYaml}

detectPod ${namespace}
dbpod=${retVal}

echo "Is going to check dbpod: ${dbpod} in the namespace: ${namespace} "
checkPod ${dbpod} ${namespace}

echo " checking pod state for pod ${dbpod} running in ${namespace}"
checkPodState ${dbpod} ${namespace} "1/1"
checkService oracle-db ${namespace}

${KUBERNETES_CLI:-kubectl} get po -n ${namespace}
${KUBERNETES_CLI:-kubectl} get service -n ${namespace}

${KUBERNETES_CLI:-kubectl} cp ${scriptDir}/common/checkDbState.sh -n ${namespace} ${dbpod}:/home/oracle/

${KUBERNETES_CLI:-kubectl} exec -it ${dbpod} -n ${namespace} -- /bin/bash /home/oracle/checkDbState.sh
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not create Oracle DB Service, check the pod log for pod ${dbpod} in namespace ${namespace}";
 echo "######################";
 exit -3;
fi

if [ ! "${nodeport}" = "none" ]; then
  echo "Oracle DB Service is RUNNING with NodePort [${nodeport}]"
else
  echo "Oracle DB Service is RUNNING and does not specify a public NodePort"
fi
echo "Oracle DB Service URL [oracle-db.${namespace}.svc.cluster.local:1521/devpdb.k8s]"
