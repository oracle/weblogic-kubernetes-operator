#!/bin/bash
# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Configure RCU schema based on schemaPreifix and rcuDatabaseURL

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

usage() {
  echo "usage: ${script} -s <schemaPrefix> [-t <schemaType>] [-d <dburl>] [-n <namespace>] [-c <credentialsSecretName>] [-p <imagePullSecret>] [-i <image>] [-u <imagePullPolicy>] [-o <rcuOutputDir>] [-h]"
  echo "  -s RCU Schema Prefix (required)"
  echo "  -t RCU Schema Type (optional)"
  echo "      (supported values: fmw(default), soa, osb, soaosb, soaess, soaessosb) "
  echo "  -d RCU Oracle Database URL (optional) "
  echo "      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) "
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

rcuType="fmw"
dburl="oracle-db.default.svc.cluster.local:1521/devpdb.k8s"
namespace="default"
createPodArgs=""

while getopts ":s:t:d:n:c:p:i:u:o:h:" opt; do
  case $opt in
    s) schemaPrefix="${OPTARG}"
    ;;
    t) rcuType="${OPTARG}"
    ;;
    d) dburl="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    c|p|i|u|o) createPodArgs+=" -${opt} ${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z "${schemaPrefix}" ]; then
  echo "${script}: -s <schemaPrefix> must be specified."
  usage 1
fi

# this creates the rcu pod if it doesn't already exist
echo "[INFO] Calling '${scriptDir}/common/create-rcu-pod.sh -n $namespace $createPodArgs'"
${scriptDir}/common/create-rcu-pod.sh -n $namespace $createPodArgs || exit -4

${KUBERNETES_CLI:-kubectl} exec -n $namespace -i rcu -- /bin/bash /u01/oracle/createRepository.sh ${dburl} ${schemaPrefix} ${rcuType}

if [ $? != 0 ]; then
 echo "######################";
 echo "[ERROR] Could not create the RCU Repository";
 echo "######################";
 exit -3;
fi

echo "[INFO] RCU Schema created. For samples that use a 'domain.input.yaml' file, modify the file to use '$dburl' for its 'rcuDatabaseURL' and '${schemaPrefix}' for its 'rcuSchemaPrefix'."
