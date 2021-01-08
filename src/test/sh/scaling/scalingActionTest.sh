#!/usr/bin/env bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

TEST_OPERATOR_ROOT=/tmp/test/weblogic-operator
setUp() {
  DISALLOW=
  PWD=/no/where/special
  DOMAIN_HOME=/domain/home

  INTROSPECTOR_MAP=${TEST_OPERATOR_ROOT}/introspector
  rm -fR ${TEST_OPERATOR_ROOT}
  mkdir -p ${TEST_OPERATOR_ROOT}/introspector
  echo "<ignored>" > $INTROSPECTOR_MAP/domainzip.secure
  echo "<ignored>" > $INTROSPECTOR_MAP/primordial_domainzip.secure
}

oneTimeTearDown() {

  # Cleanup cmds-$$.py
  [ -e cmds-$$.py ] && rm cmds-$$.py

}

test_get_domain_api_version() {
  CURL_FILE="apis1.txt"

  output=$(get_domain_api_version)

  assertEquals "should have api version v8" 'v8' "${output}"  
}

test_get_domain_api_version_without_weblogic_group() {
  CURL_FILE="apis2.txt"

  output=$(get_domain_api_version)

  assertEquals "should have empty api version" '' "${output}"  
}

######################### Mocks for the tests ###############

cat() {
  case "$*" in
    "/var/run/secrets/kubernetes.io/serviceaccount/token")
      echo "sometoken"
      ;;
  *)
    command cat $*
  esac
}

curl() {
  cat ${testdir}/${CURL_FILE}
}

testdir="${0%/*}"

# shellcheck source=scripts/scaling/scalingAction.sh
. ${SCRIPTPATH}/scalingAction.sh --no_op

echo "Run tests"

# shellcheck source=target/classes/shunit/shunit2
. ${SHUNIT2_PATH}
