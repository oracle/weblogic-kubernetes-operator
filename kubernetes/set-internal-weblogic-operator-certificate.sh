#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

if [ ! $# -eq 2 ]; then
  1>&2 echo "Syntax: ${BASH_SOURCE[0]} <operator-internal-certificate-pem-file-pathname> <operator-internal-private-key-pem-file-pathname>"
  exit 1
fi

this_dir=`cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd`
${this_dir}/internal/set-operator-certificate 'internal' $1 $2
