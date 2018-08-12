#!/usr/bin/env bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

if [ "$#" != 1 ] ; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]} <subject alternative names>"
  exit 1
fi

sans=$1
if [[ -z $namespace ]]; then
  namespace="weblogic-operator"
fi

this_dir=`cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd`

# Generate a self-signed cert for the external operator REST port
${this_dir}/internal/generate-operator-certificate.sh external $sans
