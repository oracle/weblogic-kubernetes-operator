#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This is the script that the create operator unit tests call to create an operator.
# Since unit tests run in a stripped down environment that doesn't include keytool, openssl,
# kubectl or kubernetes, we use this script to 'mock' generating certificates.
#
# Instead of using keytool and openssl to generate real certificates and keys,
# just write out some predicable text.
#
# Because of the mock behavior, the certificates and keys in the generated yaml files
# are not valid.  But that's fine for the unit tests since these yaml files are never
# used to actually run the operator.

if [ ! $# -eq 1 ]; then
  echo "Syntax: ${BASH_SOURCE[0]} <subject alternative names, e.g. DNS:localhost,DNS:mymachine,DNS:mymachine.us.oracle.com,IP:127.0.0.1>"
  exit 1
fi

script_dir="$( cd "$(dirname "${BASH_SOURCE[0]}")" > /dev/null 2>&1 ; pwd -P)"


CERT_DIR="${script_dir}/weblogic-operator-cert"
OP_PREFIX="weblogic-operator"
OP_CERT_PEM="${CERT_DIR}/${OP_PREFIX}.cert.pem"
OP_KEY_PEM="${CERT_DIR}/${OP_PREFIX}.key.pem"
SANS=$1

# we want to be able to unit test that, if this script fails, the overal script exits with an error exit code
# normally the subject alternative names are validated when the certificate is created
# simulate this by exiting with an error if SANS starts with "invalid"
if [[ ${SANS} == invalid* ]]; then
  echo "[ERROR] invalid subject alternative names: ${SANS}"
  exit 1
fi

rm -rf ${CERT_DIR}
mkdir ${CERT_DIR}

echo "unit test mock cert pem for sans:${SANS}" > ${OP_CERT_PEM}
echo "unit test mock key pem for sans:${SANS}" > ${OP_KEY_PEM}
