#!/bin/bash
# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Launch an "rcu" pod for use by the schema create and drop scripts

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../../common/utility.sh

usage() {
  echo "usage: ${script} [-n <namespace>] [-c <credentialsSecretName>] [-p <docker-store>] [-i <image>] [-u <imagePullPolicy>] [-o <rcuOutputDir>] [-h]"
  echo "  -n Namespace for RCU pod (optional)"
  echo "      (default: default)"
  echo "  -c Name of credentials secret (optional)."
  echo "       (default: oracle-rcu-secret)"
  echo "       Must contain SYSDBA username at key 'sys_username',"
  echo "       SYSDBA password at key 'sys_password',"
  echo "       and RCU schema owner password at key 'password'."
  echo "  -p FMW Infrastructure ImagePullSecret (optional) "
  echo "      (default: none) "
  echo "  -i FMW Infrastructure Image (optional) "
  echo "      (default: container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4) "
  echo "  -u FMW Infrastructure ImagePullPolicy (optional) "
  echo "      (default: IfNotPresent) "
  echo "  -o Output directory for the generated YAML file. (optional)"
  echo "      (default: rcuoutput)"
  echo "  -h Help"
  echo ""
  echo "NOTE: The c, p, i, u, and o arguments are ignored if an rcu pod is already running in the namespace."
  echo ""
  exit $1
}

namespace="default"
credSecret="oracle-rcu-secret"
fmwimage="container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4"
imagePullPolicy="IfNotPresent"
rcuOutputDir="rcuoutput"

while getopts ":h:n:c:p:i:u:o:" opt; do
  case $opt in
    n) namespace="${OPTARG}"
    ;;
    c) credSecret="${OPTARG}"
    ;;
    p) pullsecret="${OPTARG}"
    ;;
    i) fmwimage="${OPTARG}"
    ;;
    u) imagePullPolicy="${OPTARG}"
    ;;
    o) rcuOutputDir="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z "${pullsecret}" ]; then
  pullsecret="none"
  pullsecretPrefix="#"
fi

rcupod=`${KUBERNETES_CLI:-kubectl} get po -n ${namespace} | grep "^rcu " | cut -f1 -d " " `
if [ "$rcupod" = "rcu" ]; then
  echo "[INFO] Pod already exists. Skipping creation."
else
  echo "ImagePullSecret[$pullsecret] Image[${fmwimage}] dburl[${dburl}] rcuType[${rcuType}] credSecret[${credSecret}]"

  mkdir -p ${rcuOutputDir}
  rcuYaml=${rcuOutputDir}/rcu.yaml
  rm -f ${rcuYaml}
  rcuYamlTemp=${scriptDir}/template/rcu.yaml.template
  cp $rcuYamlTemp $rcuYaml

  sed -i -e "s:%NAMESPACE%:${namespace}:g" $rcuYaml
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_POLICY%:${imagePullPolicy}:g" $rcuYaml
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${pullsecret}:g" $rcuYaml
  sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${pullsecretPrefix}:g" $rcuYaml
  sed -i -e "s:%ORACLE_RCU_SECRET_NAME%:${credSecret}:g" $rcuYaml
  sed -i -e "s?image:.*?image: ${fmwimage}?g" $rcuYaml

  ${KUBERNETES_CLI:-kubectl} delete po rcu -n ${namespace} --ignore-not-found
  ${KUBERNETES_CLI:-kubectl} apply -f $rcuYaml
fi

checkPod rcu $namespace # exits non zero non error

checkPodState rcu $namespace "1/1" # exits non zero on error

sleep 5

echo "[INFO] Copying 'dropRepository.sh' and 'createRepository.sh' into the '/u01/oracle' directory in pod 'rcu'."

${KUBERNETES_CLI:-kubectl} exec -n $namespace -i rcu -- bash -c 'cat > /u01/oracle/dropRepository.sh' < ${scriptDir}/dropRepository.sh || exit -5
${KUBERNETES_CLI:-kubectl} exec -n $namespace -i rcu -- bash -c 'cat > /u01/oracle/createRepository.sh' < ${scriptDir}/createRepository.sh || exit -6

${KUBERNETES_CLI:-kubectl} get po/rcu -n $namespace 

echo "[INFO] Pod 'rcu' is running in namespace '$namespace'"

