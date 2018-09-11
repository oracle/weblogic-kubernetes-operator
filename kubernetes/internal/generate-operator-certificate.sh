#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

set -e

if [ $# != 2 ] ; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]} <prefix (internal/external)> <subject alternative names>"
  exit 1
fi

prefix=$1
sans=$2

this_dir=`cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd`
cert_dir="$this_dir/weblogic-operator-cert"

function cleanup {
  rm -rf $cert_dir
  if [[ $succeeded != "true" ]]; then
    exit 1
  fi
}

trap "cleanup" EXIT

# Generate a self-signed cert for the operator REST port
1>&2 ${this_dir}/generate-weblogic-operator-cert.sh $sans

${this_dir}/set-operator-certificate.sh ${prefix} ${cert_dir}/weblogic-operator.cert.pem ${cert_dir}/weblogic-operator.key.pem

succeeded=true
