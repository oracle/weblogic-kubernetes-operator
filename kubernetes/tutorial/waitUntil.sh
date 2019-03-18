#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.


DEFAULT_MAX_WAIT=20

# Usage:
# waitUntil cmd expected_out okMsg failMsg <waitMaxCount>
function waitUntil() {
  cmd=$1
  expected_out=$2
  okMsg=$3
  failMsg=$4
  if [ $# = 5 ]; then
    max_wait=$5
  else
    max_wait=$DEFAULT_MAX_WAIT
  fi

  count=0
  echo "wait until $okMsg"
  while [ $count -lt $max_wait ]; do
    if [ "$($cmd)" = "$expected_out" ]; then
      echo $okMsg
      return 0
    fi
    #echo "wait until $okMsg"
    ((count=count+1))
    sleep 3 
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

# Usage: waitUntilHttpReady name hostname url
function waitUntilHttpReady() {
  expected_out=200
  okMsg="http to $1 is ready"
  failMsg="fail to access http to $1 "

  waitUntil "checkHttpCmd $2 $3" "$expected_out" "$okMsg" "$failMsg"
}

# Usage: checkHTTPCmd hostname url
function checkHttpCmd() {
  curl -s -o /dev/null -w "%{http_code}"  -H "host: $1" $2
}

# Usage: waitUntilHttpsReady name hostname url
function waitUntilHttpsReady() {
  expected_out=200
  okMsg="https to $1 is ready"
  failMsg="fail to access https to $1 "

  waitUntil "checkHttpsCmd $2 $3" "$expected_out" "$okMsg" "$failMsg"
}

# Usage: checkHTTPSCmd hostname url
function checkHttpsCmd() {
  curl -k -s -o /dev/null -w "%{http_code}"  -H "host: $1" $2
}

function test() {
  expected_out=0
  okMsg="namespace $1 is termiated"
  failMsg="fail to termiate namespace $1"

  waitUntil "checkNSTermCmd $1" "$expected_out" "$okMsg" "$failMsg"  1
  waitUntil "checkNSTermCmd $1" "$expected_out" "$okMsg" "$failMsg" 2
  waitUntil "checkNSTermCmd $1" "$expected_out" "$okMsg" "$failMsg" 
}

