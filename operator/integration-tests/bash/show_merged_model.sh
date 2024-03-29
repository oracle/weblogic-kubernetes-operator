#!/bin/bash
# Copyright (c) 2019, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#  This script show the model in image merged model of the running domain in clear text
#
DOMAIN_NAMESPACE=sample-domain1-ns
DOMAIN_UID=sample-domain1
PASSWORD=my_runtime_password
IMAGE=model-in-image:JRF-v1
IMAGEPULLPOLICY=IfNotPresent
WDT_INSTALLER=$HOME/Downloads/weblogic-deploy.zip
IMAGEPULLSECRETSTAG=''
IMGPS=''
IMGPSN=''
KUBERNETES_CLI=${KUBERNETES_CLI:-kubectl}

usage_exit() {
cat << EOF

  Usage: $(basename $0) ...options...

    Show the encrypted merged model json file for model in image

  Parameters:

    -i imagename           - Image, default is "$IMAGE".
    -l imagepullpolicy     - Image pull policy, default is "$IMAGEPULLPOLICY".
    -n namespace           - Namespace, default is "$NAMESPACE".
    -p password            - Password, default is "$PASSWORD".
    -d domain uid          - Domain UID, default is "${DOMAIN_UID}"
    -w wdt installer       - WDT Installer location, default is "${WDT_INSTALLER} - $HOME/Downloads/weblogic-deploy.zip"

  Sample usage:
  
    $(basename $0) -i model-in-image:v1 -n sample-domain1-ns -p weblogic -d domain1

EOF
 
  exit 1
}

while getopts i:n:p:l:s:w:d:h OPT
do
  case $OPT in
  i) IMAGE=$OPTARG
     ;;
  l) IMAGEPULLPOLICY=$OPTARG
     ;;
  s) IMAGEPULLSECRETS=$OPTARG

     IMGPS="imagePullSecrets:"
     IMGPSN="  -name ${IMAGEPULLSECRETS}"
     ;;
  p) PASSWORD=$OPTARG
     ;;
  n) DOMAIN_NAMESPACE=$OPTARG
     ;;
  d) DOMAIN_UID=$OPTARG
     ;;
  w) WDT_INSTALLER=$OPTARG
     ;;
  h) usage_exit
     ;;
  *) usage_exit
     ;;
  esac
done
shift $(($OPTIND - 1))
[ ! -z "$1" ] && usage_exit


cat > decrypt_model.yaml <<EOF
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: decryptmodel
  name: decryptmodel
  namespace: ${DOMAIN_NAMESPACE}
spec:
  containers:
  - args:
    - sleep
    - infinity
    image: ${IMAGE}
    imagePullPolicy: ${IMAGEPULLPOLICY}
    ${IMGPS}
    ${IMGPSN} 
    name: decryptmodel
EOF
${KUBERNETES_CLI} apply -f decrypt_model.yaml
echo "wait for pod available"
while [ 1 -eq 1 ] ; do 
    n=$(${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get pod decryptmodel  | grep Running | wc -l)
    echo $n
    if [ $n -eq 1 ] ; then
        break
    fi
    sleep 1
done
${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get configmap ${DOMAIN_UID}-weblogic-domain-introspect-cm -o jsonpath='{.data.merged_model\.json}' > encrypted_model.json
${KUBERNETES_CLI} cp encrypted_model.json ${DOMAIN_NAMESPACE}/decryptmodel:/tmp
${KUBERNETES_CLI} cp decrypt_model.sh ${DOMAIN_NAMESPACE}/decryptmodel:/tmp
${KUBERNETES_CLI} cp model-encryption-util.py ${DOMAIN_NAMESPACE}/decryptmodel:/tmp
if [ -f ${WDT_INSTALLER} ] ; then
  ${KUBERNETES_CLI} cp ${WDT_INSTALLER} ${DOMAIN_NAMESPACE}/decryptmodel:/tmp
  INSTALLER=$(basename ${WDT_INSTALLER})
  ${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} exec decryptmodel -- bash -c "cd /tmp && unzip ${INSTALLER}"
fi
${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} exec decryptmodel -- bash -c "/tmp/decrypt_model.sh decrypt /tmp/encrypted_model.json ${PASSWORD} /tmp/decrypted_model.json &&  base64 -d /tmp/decrypted_model.json | gunzip "
${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} delete -f decrypt_model.yaml

