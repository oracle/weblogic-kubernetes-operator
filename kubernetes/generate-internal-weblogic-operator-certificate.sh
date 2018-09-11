#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

if [ "$#" -gt 1 ] ; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]} <operator namespace, optional, defaults to weblogic-operator>"
  exit 1
fi

namespace=$1
if [[ -z $namespace ]]; then
  namespace="weblogic-operator"
fi

this_dir=`cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd`

# Generate a self-signed cert for the internal operator REST port
host="internal-weblogic-operator-service"
sans="DNS:${host},DNS:${host}.${namespace},DNS:${host}.${namespace}.svc,DNS:${host}.${namespace}.svc.cluster.local"

${this_dir}/internal/generate-operator-certificate.sh internal $sans
