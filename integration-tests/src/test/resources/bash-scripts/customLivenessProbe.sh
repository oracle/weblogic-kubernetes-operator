#!/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

FILE=/u01/tempFile.txt
if [  -f ${FILE} ]; then
  echo "FILE  exists, liveness probe will be triggered with exit code 1"
  exit 1
elif [ ! -f ${FILE} ]; then
  echo "FILE does not exists, liveness probe will not be triggered with exit code 0"
  exit 0
fi