#!/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

FILE=/u01/tempFile.txt
if [ -f ${FILE} ]; then
  echo "Find the file"
  cat ${FILE}
  exit 1
elif [ ! -f ${FILE} ]; then
  echo "FILE does not exist"
  exit 0
fi
