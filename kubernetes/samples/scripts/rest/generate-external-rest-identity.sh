#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# When the customer enables the operator's external REST api (by setting
# externalRestEnabled to true when installing the operator helm chart), the customer needs
# to provide the certificate and private key for api's SSL identity too (by setting
# externalOperatorCert and externalOperatorKey to the base64 encoded PEM of the cert and
# key when installing the operator helm chart).
#
# This sample script generates a self-signed certificate and private key that can be used
# for the operator's external REST api when experimenting with the operator.  They should
# not be used in a production environment.
#
# The sytax of the script is:
#   kubernetes/samples/scripts/generate-external-rest-identity.sh <subject alternative names>
#
# <subject alternative names> lists the subject alternative names to put into the generated
# self-signed certificate for the external WebLogic Operator REST https interface,
# for example:
#   DNS:myhost,DNS:localhost,IP:127.0.0.1
#
# The script prints out the base64 encoded pem of the generated certificate and private key
# in the same format that the operator helm chart's values.yaml requires.
#
# Example usage:
#   generate-external-rest-identity.sh IP:127.0.0.1 > my_values.yaml
#   echo "externalRestEnabled: true" >> my_values.yaml
#   ...
#   helm install kubernetes/charts/weblogic-operator --name my_operator --namespace my_operator-ns --values my_values.yaml --wait

if [ "$#" != 1 ] ; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]} <subject alternative names>"
  exit 1
fi

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

trap "cleanup" EXIT

#set -x

SANS=$1
DAYS_VALID="3650"
TEMP_PW="temp_password"
OP_PREFIX="weblogic-operator"
OP_ALIAS="${OP_PREFIX}-alias"
OP_JKS="${TEMP_DIR}/${OP_PREFIX}.jks"
OP_PKCS12="${TEMP_DIR}/${OP_PREFIX}.p12"
OP_CSR="${TEMP_DIR}/${OP_PREFIX}.csr"
OP_CERT_PEM="${TEMP_DIR}/${OP_PREFIX}.cert.pem"
OP_KEY_PEM="${TEMP_DIR}/${OP_PREFIX}.key.pem"
KEYTOOL=/usr/java/jdk1.8.0_141/bin/keytool

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

# base64 encode the cert and private key pem
CERT_DATA=`base64 -i ${OP_CERT_PEM} | tr -d '\n'`
KEY_DATA=`base64 -i ${OP_KEY_PEM} | tr -d '\n'`

# print out the cert and pem in the form that can be added to
# the operator helm chart's values.yaml
echo "externalOperatorCert: ${CERT_DATA}"
echo "externalOperatorKey: ${KEY_DATA}"

SUCCEEDED=true
