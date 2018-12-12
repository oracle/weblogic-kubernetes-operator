#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# do not turn on 'set -x' since it can print sensitive info, like secrets and private keys, to the oeprator log
#set -x

if [ "$#" != 0 ] ; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]}"
  exit 1
fi

OPERATOR_DIR="/operator"
CERT_PROPERTY="internalOperatorCert"
KEY_PROPERTY="internalOperatorKey"
INTERNAL_IDENTITY_DIR="${OPERATOR_DIR}/internal-identity"
NAMESPACE=`cat /var/run/secrets/kubernetes.io/serviceaccount/namespace`

# the operator runtime expects the internal operator cert and private key to be in these files:
INTERNAL_CERT_BASE64_PEM="${INTERNAL_IDENTITY_DIR}/${CERT_PROPERTY}"
INTERNAL_KEY_PEM="${INTERNAL_IDENTITY_DIR}/${KEY_PROPERTY}"

function cleanup {
  if [[ $SUCCEEDED != "true" ]]; then
    exit 1
  fi
}

function generateInternalIdentity {
  TEMP_DIR="${INTERNAL_IDENTITY_DIR}/temp"
  mkdir ${TEMP_DIR}

  # note : host must match the name of the node port for the operator internal REST api
  host="internal-weblogic-operator-svc"
  SANS="DNS:${host},DNS:${host}.${NAMESPACE},DNS:${host}.${NAMESPACE}.svc,DNS:${host}.${NAMESPACE}.svc.cluster.local"
  DAYS_VALID="3650"
  TEMP_PW="temp_password"
  OP_PREFIX="weblogic-operator"
  OP_ALIAS="${OP_PREFIX}-alias"
  OP_JKS="${TEMP_DIR}/${OP_PREFIX}.jks"
  OP_PKCS12="${TEMP_DIR}/${OP_PREFIX}.p12"
  OP_CSR="${TEMP_DIR}/${OP_PREFIX}.csr"
  OP_CERT_PEM="${TEMP_DIR}/${OP_PREFIX}.cert.pem"
  OP_KEY_PEM="${TEMP_DIR}/${OP_PREFIX}.key.pem"
  KEYTOOL=${JAVA_HOME}/bin/keytool

  # generate a keypair for the operator's internal service, putting it in a keystore
  $KEYTOOL \
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
    -ext SAN="${SANS}"

  # extract the cert to a pem file
  $KEYTOOL \
    -exportcert \
    -keystore ${OP_JKS} \
    -storepass ${TEMP_PW} \
    -alias ${OP_ALIAS} \
    -rfc \
    > ${OP_CERT_PEM}

  # convert the keystore to a pkcs12 file
  $KEYTOOL \
    -importkeystore \
    -srckeystore ${OP_JKS} \
    -srcstorepass ${TEMP_PW} \
    -destkeystore ${OP_PKCS12} \
    -srcstorepass ${TEMP_PW} \
    -deststorepass ${TEMP_PW} \
    -deststoretype PKCS12

  # extract the private key from the pkcs12 file to a pem file
  openssl \
    pkcs12 \
    -in ${OP_PKCS12} \
    -passin pass:${TEMP_PW} \
    -nodes \
    -nocerts \
    -out ${OP_KEY_PEM}

  # copy the certificate and key to the locations the operator runtime expects
  base64 -i ${OP_CERT_PEM} | tr -d '\n' > ${INTERNAL_CERT_BASE64_PEM}
  cp ${OP_KEY_PEM} ${INTERNAL_KEY_PEM}
}

function recordInternalIdentity {
  CACERT='/var/run/secrets/kubernetes.io/serviceaccount/ca.crt'
  TOKEN=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`
  KUBERNETES_MASTER="https://kubernetes.default.svc"

  # the request body prints out the atz token
  # don't specify -v so that the token is not printed to the operator log

  # the response body from PATCH prints out the config map / secret
  # send stdout to /dev/null to supress this so that we don't print the cert or private key to the operator log

  # put the new certificate in the operator's config map so that it will be available
  # the next time the operator is started
  NEW_CERT=`cat ${INTERNAL_CERT_BASE64_PEM}`
  PATCH_DOCUMENT="{ \"data\": { \"${CERT_PROPERTY}\" : \"${NEW_CERT}\" } }"
  curl \
    --cacert $CACERT \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/merge-patch+json" \
    -d "${PATCH_DOCUMENT}" \
    -X PATCH \
    $KUBERNETES_MASTER/api/v1/namespaces/$NAMESPACE/configmaps/weblogic-operator-cm \
    > /dev/null

  # put the new private key in the operator's secret so that it will be available
  # the next time the operator is started
  NEW_KEY=`base64 -i ${INTERNAL_KEY_PEM} | tr -d '\n'`
  PATCH_DOCUMENT="{ \"data\": { \"${KEY_PROPERTY}\" : \"${NEW_KEY}\" } }"
  curl \
    --cacert $CACERT \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/merge-patch+json" \
    -d "${PATCH_DOCUMENT}" \
    -X PATCH \
    $KUBERNETES_MASTER/api/v1/namespaces/$NAMESPACE/secrets/weblogic-operator-secrets \
    > /dev/null
}

function createInternalIdentity {
  generateInternalIdentity
  recordInternalIdentity
}

function reuseInternalIdentity {
  # copy the certificate and key from the operator's config map and secret
  # to the locations the operator runtime expects
  cp ${OPERATOR_DIR}/config/${CERT_PROPERTY} ${INTERNAL_CERT_BASE64_PEM}
  cp ${OPERATOR_DIR}/secrets/${KEY_PROPERTY} ${INTERNAL_KEY_PEM}
}

set -e

trap "cleanup" EXIT

mkdir ${INTERNAL_IDENTITY_DIR}

if [ -f "${OPERATOR_DIR}/config/${CERT_PROPERTY}" ]; then
  # The operator's internal ssl identity has already been created.
  reuseInternalIdentity
else
  # The operator's internal ssl identity hasn't been created yet.
  createInternalIdentity
fi

SUCCEEDED=true

