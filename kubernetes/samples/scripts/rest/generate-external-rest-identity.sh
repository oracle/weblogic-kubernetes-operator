#!/usr/bin/env bash
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# When the customer enables the operator's external REST api (by setting
# externalRestEnabled to true when installing the operator helm chart), the customer needs
# to provide the certificate and private key for api's SSL identity too (by creating a 
# tls secret before the installation of the operator helm chart).
#
# This sample script generates a self-signed certificate and private key that can be used
# for the operator's external REST api when experimenting with the operator.  They should
# not be used in a production environment.
#
# The sytax of the script is:
#
#   kubernetes/samples/scripts/rest/generate-external-rest-identity.sh -a <SANs> -n <namespace>
#
# Where <SANs> lists the subject alternative names to put into the generated self-signed 
# certificate for the external WebLogic Operator REST https interface, for example:
#
#   DNS:myhost,DNS:localhost,IP:127.0.0.1
#
# You should include the addresses of all masters and load balancers in this list.  The certificate
# cannot be conveniently changed after installation of the operator.
#
# The script creates the secret in the weblogic-operator namespace with the self-signed 
# certificate and private key
#
# Example usage:
#   generate-external-rest-identity.sh -a IP:127.0.0.1 -n weblogic-operator > my_values.yaml
#   echo "externalRestEnabled: true" >> my_values.yaml
#   ...
#   helm install my_operator kubernetes/charts/weblogic-operator --namespace my_operator-ns --values my_values.yaml --wait
usage(){
cat <<EOF
Usage: $0 [options] -a <subject alternative names> -n <namespace>
Options:
-a  SANS           Required, the SANs for the certificate
-n  NAMESPACE      Required, the namespace where the secret will be created.
-s  SECRET_NAME    Optional, the name of the kubernetes secret. Default is: weblogic-operator-external-rest-identity.
-h, --help         Display this help text.
EOF
exit 1
}

if [ ! -x "$(command -v keytool)" ]; then
  echo "Can't find keytool.  Please add it to the path."
  exit 1
fi

if [ ! -x "$(command -v openssl)" ]; then
  echo "Can't find openssl.  Please add it to the path."
  exit 1
fi

if [ ! -x "$(command -v base64)" ]; then
  echo "Can't find base64.  Please add it to the path."
  exit 1
fi

TEMP_DIR=`mktemp -d`
if [ $? -ne 0 ]; then
  echo "$0: Can't create temp directory."
  exit 1
fi

if [ -z $TEMP_DIR ]; then
  echo "Can't create temp directory."
  exit 1
fi

function cleanup {
  rm -r $TEMP_DIR
  if [[ $SUCCEEDED != "true" ]]; then
    exit 1
  fi
}

set -e
#set -x

trap "cleanup" EXIT

SECRET_NAME="weblogic-operator-external-rest-identity"

while [ $# -gt 0 ]
  do 
    key="$1"
    case $key in
      -a)
      shift # past argument
      if [ $# -eq 0 ] || [ ${1:0:1} == "-" ]; then echo "SANs is required and is missing"; usage; fi
      SANS=$1
      shift # past value
      ;;
      -n)
      shift # past argument
      if [ $# -eq 0 ] || [ ${1:0:1} == "-" ]; then echo "Namespace is required and is missing"; usage; fi
      NAMESPACE=$1
      shift # past value
      ;;
      -s)
      shift # past argument
      if [ $# -eq 0 ] || [ ${1:0:1} == "-" ]; then echo "Invalid secret name $1"; usage; fi
      SECRET_NAME=$1
      shift # past value
      ;;
      -h)
      shift # past argument
      ;;
      *)
      SANS=$1
      shift # past argument
      ;;  
    esac    
done

if [ -z "$SANS" ]
then
  1>&2
  echo "SANs is required and is missing"
  usage
fi

if [ -z "$NAMESPACE" ]
then
  1>&2
  echo "Namespace is required and is missing"
  usage
fi

DAYS_VALID="3650"
TEMP_PW="temp_password"
OP_PREFIX="weblogic-operator"
OP_ALIAS="${OP_PREFIX}-alias"
OP_JKS="${TEMP_DIR}/${OP_PREFIX}.jks"
OP_PKCS12="${TEMP_DIR}/${OP_PREFIX}.p12"
OP_CSR="${TEMP_DIR}/${OP_PREFIX}.csr"
OP_CERT_PEM="${TEMP_DIR}/${OP_PREFIX}.cert.pem"
OP_KEY_PEM="${TEMP_DIR}/${OP_PREFIX}.key.pem"

# generate a keypair for the operator's REST service, putting it in a keystore
keytool \
  -genkey \
  -keystore ${OP_JKS} \
  -alias ${OP_ALIAS} \
  -storepass ${TEMP_PW} \
  -keypass ${TEMP_PW} \
  -keysize 2048 \
  -keyalg RSA \
  -validity ${DAYS_VALID} \
  -dname "CN=weblogic-operator" \
  -ext KU=digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment,keyAgreement \
  -ext SAN="${SANS}" \
2> /dev/null

# extract the cert to a pem file
keytool \
  -exportcert \
  -keystore ${OP_JKS} \
  -storepass ${TEMP_PW} \
  -alias ${OP_ALIAS} \
  -rfc \
> ${OP_CERT_PEM} 2> /dev/null

# convert the keystore to a pkcs12 file
keytool \
  -importkeystore \
  -srckeystore ${OP_JKS} \
  -srcstorepass ${TEMP_PW} \
  -destkeystore ${OP_PKCS12} \
  -srcstorepass ${TEMP_PW} \
  -deststorepass ${TEMP_PW} \
  -deststoretype PKCS12 \
2> /dev/null

# extract the private key from the pkcs12 file to a pem file
openssl \
  pkcs12 \
  -in ${OP_PKCS12} \
  -passin pass:${TEMP_PW} \
  -nodes \
  -nocerts \
  -out ${OP_KEY_PEM} \
2> /dev/null

set +e
# Check if namespace exist
kubectl get namespace $NAMESPACE >/dev/null 2>/dev/null
if [ $? -eq 1 ]; then
  echo "Namespace $NAMESPACE does not exist"
  exit 1
fi
kubectl get secret $SECRET_NAME -n $NAMESPACE >/dev/null 2>/dev/null
if [ $? -eq 1 ]; then
  kubectl create secret tls "$SECRET_NAME" --cert=${OP_CERT_PEM} --key=${OP_KEY_PEM} -n $NAMESPACE >/dev/null
fi
echo "externalRestIdentitySecret: $SECRET_NAME"

SUCCEEDED=true
