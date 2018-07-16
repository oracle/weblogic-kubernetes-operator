#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

set -e

if [ $# != 3 ]; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]} <prefix (internal/external)> <operator-certificate-pem-file-pathname> <operator-private-key-pem-file-pathname>"
  exit 1
fi

prefix=$1
cert_file=$2
key_file=$3

if [ ! -f ${cert_file} ]; then
  1>&2 echo The file ${cert_file} was not found
  exit 1
fi

if [ ! -f ${key_file} ]; then
  1>&2 echo The file ${key_file} was not found
  exit 1
fi

function cleanup {
  if [[ $succeeded != "true" ]]; then
    1>&1 echo "Failed to set the weblogic operator certificate and private key."
    exit 1
  fi
}

trap "cleanup" EXIT

cert_data=`base64 -i ${cert_file} | tr -d '\n'`
key_data=`base64 -i ${key_file} | tr -d '\n'`

echo "${prefix}OperatorCert: ${cert_data}"
echo "${prefix}OperatorKey: ${key_data}"

succeeded=true
