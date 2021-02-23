#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Configure RCU schema based on schemaPreifix and rcuDatabaseURL

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh

function usage {
  echo "usage: ${script} -s <schemaPrefix> -t <schemaType> -d <dburl> -i <image> -u <imagePullPolicy> -p <docker-store> -n <namespace> -q <sysPassword> -r <schemaPassword>  -o <rcuOutputDir>  [-h]"
  echo "  -s RCU Schema Prefix (required)"
  echo "  -t RCU Schema Type (optional)"
  echo "      (supported values: fmw(default), soa, osb, soaosb, soaess, soaessosb) "
  echo "  -d RCU Oracle Database URL (optional) "
  echo "      (default: oracle-db.default.svc.cluster.local:1521/devpdb.k8s) "
  echo "  -p FMW Infrastructure ImagePullSecret (optional) "
  echo "      (default: none) "
  echo "  -i FMW Infrastructure Image (optional) "
  echo "      (default: container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4) "
  echo "  -u FMW Infrastructure ImagePullPolicy (optional) "
  echo "      (default: IfNotPresent) "
  echo "  -n Namespace for RCU pod (optional)"
  echo "      (default: default)"
  echo "  -q password for database SYSDBA user. (optional)"
  echo "      (default: Oradoc_db1)"
  echo "  -r password for all schema owner (regular user). (optional)"
  echo "      (default: Oradoc_db1)"
  echo "  -o Output directory for the generated YAML file. (optional)"
  echo "      (default: rcuoutput)"
  echo "  -h Help"
  exit $1
}

while getopts ":h:s:d:p:i:t:n:q:r:o:u:" opt; do
  case $opt in
    s) schemaPrefix="${OPTARG}"
    ;;
    t) rcuType="${OPTARG}"
    ;;
    d) dburl="${OPTARG}"
    ;;
    p) pullsecret="${OPTARG}"
    ;;
    i) fmwimage="${OPTARG}"
    ;;
    n) namespace="${OPTARG}"
    ;;
    q) sysPassword="${OPTARG}"
    ;;
    r) schemaPassword="${OPTARG}"
    ;;
    o) rcuOutputDir="${OPTARG}"
    ;;
    u) imagePullPolicy="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${schemaPrefix} ]; then
  echo "${script}: -s <schemaPrefix> must be specified."
  usage 1
fi

if [ -z ${dburl} ]; then
  dburl="oracle-db.default.svc.cluster.local:1521/devpdb.k8s"
fi

if [ -z ${rcuType} ]; then
  rcuType="fmw"
fi

if [ -z ${pullsecret} ]; then
  pullsecret="none"
  pullsecretPrefix="#"
fi

if [ -z ${fmwimage} ]; then
 fmwimage="container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4"
fi

if [ -z ${imagePullPolicy} ]; then
 imagePullPolicy="IfNotPresent"
fi

if [ -z ${namespace} ]; then
 namespace="default"
fi

if [ -z ${sysPassword} ]; then
 sysPassword="Oradoc_db1"
fi

if [ -z ${schemaPassword} ]; then
 schemaPassword="Oradoc_db1"
fi

if [ -z ${rcuOutputDir} ]; then
 rcuOutputDir="rcuoutput"
fi

echo "ImagePullSecret[$pullsecret] Image[${fmwimage}] dburl[${dburl}] rcuType[${rcuType}]"

mkdir -p ${rcuOutputDir}
rcuYaml=${rcuOutputDir}/rcu.yaml
rm -f ${rcuYaml}
rcuYamlTemp=${scriptDir}/common/template/rcu.yaml.template
cp $rcuYamlTemp $rcuYaml

#kubectl run rcu --generator=run-pod/v1 --image ${jrf_image} -- sleep infinity
# Modify the ImagePullSecret based on input
sed -i -e "s:%NAMESPACE%:${namespace}:g" $rcuYaml
sed -i -e "s:%WEBLOGIC_IMAGE_PULL_POLICY%:${imagePullPolicy}:g" $rcuYaml
sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${pullsecret}:g" $rcuYaml
sed -i -e "s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${pullsecretPrefix}:g" $rcuYaml
sed -i -e "s?image:.*?image: ${fmwimage}?g" $rcuYaml
kubectl apply -f $rcuYaml

# Make sure the rcu deployment Pod is RUNNING
checkPod rcu $namespace
checkPodState rcu $namespace "1/1"
sleep 5
kubectl get po/rcu -n $namespace 

# Generate the default password files for rcu command
echo "$sysPassword" > pwd.txt
echo "$schemaPassword" >> pwd.txt

kubectl exec -n $namespace -i rcu -- bash -c 'cat > /u01/oracle/createRepository.sh' < ${scriptDir}/common/createRepository.sh 
kubectl exec -n $namespace -i rcu -- bash -c 'cat > /u01/oracle/pwd.txt' < pwd.txt 
rm -rf createRepository.sh pwd.txt

kubectl exec -n $namespace -i rcu /bin/bash /u01/oracle/createRepository.sh ${dburl} ${schemaPrefix} ${rcuType} ${sysPassword}
if [ $? != 0  ]; then
 echo "######################";
 echo "[ERROR] Could not create the RCU Repository";
 echo "######################";
 exit -3;
fi

echo "[INFO] Modify the domain.input.yaml to use [$dburl] as rcuDatabaseURL and [${schemaPrefix}] as rcuSchemaPrefix "
