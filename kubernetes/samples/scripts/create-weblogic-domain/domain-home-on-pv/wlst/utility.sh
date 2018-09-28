#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Report an error and fail the job
# $1 - text of error
function fail {
  echo ERROR: $1
  exit 1
}

#
# Create a folder
# $1 - path of folder to create
function createFolder {
  mkdir -m 777 -p $1
  if [ ! -d $1 ]; then
    fail "Unable to create folder $1"
  fi
}

#
# Check a file exists
# $1 - path of file to check
function checkFileExists {
  if [ ! -f $1 ]; then
    fail "The file $1 does not exist"
  fi
}

