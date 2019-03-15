#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.


# Usage:
# waitUntil cmd expected_out okMsg failMsg 
function waitUntil() {
  cmd=$1
  expected_out=$2
  okMsg=$3
  failMsg=$4

  MAX_WAIT=100
  count=0
  while [ $count -lt $MAX_WAIT ]; do
    if [ "$($cmd)" = "$expected_out" ]; then
      echo $okMsg
      return 0;
    fi
    echo "wait until $okMsg"
    ((count=count+1))
    sleep 5 
  done
  echo "Error: $failMsg"
  return 1
}

# usage: waitUntilNSTerm ns_name 
function waitUntilNSTerm() {
  expected_out=0
  okMsg="namespace $1 is termiated"
  failMsg="fail to termiate namespace $1"

  waitUntil "checkNSTermCmd $1" "$expected_out" "$okMsg" "$failMsg"
}

function checkNSTermCmd() {
  kubectl get ns $1  --ignore-not-found | grep $1 | wc -l
}
