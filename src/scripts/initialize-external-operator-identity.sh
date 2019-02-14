#!/usr/bin/env bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# do not turn on 'set -x' since it can print sensitive info, like secrets and private keys, to the operator log
#set -x

if [ "$#" != 0 ] ; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]}"
  exit 1
fi

EXTERNAL_CERT="externalOperatorCert"
EXTERNAL_KEY="externalOperatorKey"
OPERATOR_DIR="/operator"
EXTERNAL_IDENTITY_DIR="${OPERATOR_DIR}/external-identity"
NAMESPACE=`cat /var/run/secrets/kubernetes.io/serviceaccount/namespace`
OPERATOR_CONFIG_DIR=${OPERATOR_DIR}/config
OPERATOR_SECRETS_DIR=${OPERATOR_DIR}/secrets

# the operator runtime expects the external operator cert and private key to be in these files:
EXTERNAL_CERT_PEM="${EXTERNAL_IDENTITY_DIR}/${EXTERNAL_CERT}"
EXTERNAL_KEY_PEM="${EXTERNAL_IDENTITY_DIR}/${EXTERNAL_KEY}"
EXTERNAL_CERT_SECRET="${OPERATOR_CONFIG_DIR}/externalRestIdentitySecret"

# the legacy helm install mount the ceritificate and private key in the following locations:
LEGACY_CERT_BASE64_PEM=${OPERATOR_CONFIG_DIR}/${EXTERNAL_CERT}
LEGACY_KEY_PEM=${OPERATOR_SECRETS_DIR}/${EXTERNAL_KEY}

CACERT='/var/run/secrets/kubernetes.io/serviceaccount/ca.crt'
TOKEN=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`
KUBERNETES_MASTER="https://kubernetes.default.svc"

function cleanup {
  if [[ $SUCCEEDED != "true" ]]; then
    exit 1
  fi
}

function getExternalIdentity {
  SECRET_NAME=`cat ${EXTERNAL_CERT_SECRET}`
  
  curl -s \
    --cacert $CACERT \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -X GET \
    $KUBERNETES_MASTER/api/v1/namespaces/$NAMESPACE/secrets/$SECRET_NAME | \
    python -c "import sys, json; print json.load(sys.stdin)['data']['tls.crt']" \
    >> ${EXTERNAL_CERT_PEM}

  curl -s \
    --cacert $CACERT \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -X GET \
    $KUBERNETES_MASTER/api/v1/namespaces/$NAMESPACE/secrets/$SECRET_NAME | \
    python -c "import sys, json; print json.load(sys.stdin)['data']['tls.key']" | base64 --decode \
    >> ${EXTERNAL_KEY_PEM}
}

function getLegacyExternalIdentity {
  cp ${LEGACY_CERT_BASE64_PEM} ${EXTERNAL_CERT_PEM}
  cp ${LEGACY_KEY_PEM} ${EXTERNAL_KEY_PEM}
}

set -e

trap "cleanup" EXIT

mkdir ${EXTERNAL_IDENTITY_DIR}

if [ -f "${EXTERNAL_CERT_SECRET}" ]; then
  # The operator's external ssl certificate was defined within a kubernetes tls secret
  getExternalIdentity
else
  if [ -f "${LEGACY_CERT_BASE64_PEM}" ]; then
    # The operator's external ssl certificate was created by helm using the values.yaml.
    getLegacyExternalIdentity
  fi
fi

SUCCEEDED=true
