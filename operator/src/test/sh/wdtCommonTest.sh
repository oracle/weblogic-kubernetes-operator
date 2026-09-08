#!/usr/bin/env bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

TEST_ROOT=/tmp/test/wdt-common
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPTPATH="$(cd "${SCRIPT_DIR}/../../main/resources/scripts" && pwd)"

# shellcheck source=operator/src/main/resources/scripts/utils.sh
. "${SCRIPTPATH}/utils.sh"

# shellcheck source=operator/src/main/resources/scripts/wdt_common.sh
. "${SCRIPTPATH}/wdt_common.sh"

setUp() {
  rm -rf "${TEST_ROOT}"
  mkdir -p "${TEST_ROOT}/wdt/logs"
  WDT_ROOT="${TEST_ROOT}/wdt"
  WDT_CREATE_DOMAIN_LOG=createDomain.log
  unset LOG_HOME
  unset WLSDEPLOY_LOG_DIRECTORY
  unset WDT_COMMAND_LOG_ENCODED_MAX_BYTES
  unset WDT_COMMAND_LOG_HOME_PATH
  echo "WDT command log line 1" > "${WDT_ROOT}/logs/${WDT_CREATE_DOMAIN_LOG}"
  echo "WDT command log line 2" >> "${WDT_ROOT}/logs/${WDT_CREATE_DOMAIN_LOG}"
}

tearDown() {
  rm -rf "${TEST_ROOT}"
}

assertOutputContains() {
  local output="$1"
  local expected="$2"
  echo "${output}" | grep -q "${expected}"
  assertEquals "expected output to contain '${expected}'" 0 $?
}

assertOutputDoesNotContain() {
  local output="$1"
  local expected="$2"
  echo "${output}" | grep -q "${expected}"
  assertNotEquals "expected output not to contain '${expected}'" 0 $?
}

testWdtReportCommandLogOnFailure_whenLogHomeAvailable_copiesLogAndPrintsPointer() {
  LOG_HOME="${TEST_ROOT}/log-home"
  mkdir -p "${LOG_HOME}"

  actual="$(wdtReportCommandLogOnFailure createDomain "${WDT_CREATE_DOMAIN_LOG}")"

  assertEquals "WDT command log line 1
WDT command log line 2" "$(cat "${LOG_HOME}/${WDT_CREATE_DOMAIN_LOG}")"
  assertOutputContains "${actual}" "WDT_COMMAND_LOG_AVAILABLE command=createDomain"
  assertOutputContains "${actual}" "path=${LOG_HOME}/${WDT_CREATE_DOMAIN_LOG}"
  assertOutputDoesNotContain "${actual}" "encoding=gzip+base64"
}

testWdtReportCommandLogOnFailure_whenLogHomeUnavailable_printsOneEncodedLine() {
  WDT_COMMAND_LOG_ENCODED_MAX_BYTES=1048576

  actual="$(wdtReportCommandLogOnFailure createDomain "${WDT_CREATE_DOMAIN_LOG}")"
  payload="$(echo "${actual}" | sed -n 's/.* data=//p')"

  assertOutputContains "${actual}" "WDT_COMMAND_LOG command=createDomain"
  assertOutputContains "${actual}" "encoding=gzip+base64"
  assertEquals "WDT command log line 1
WDT command log line 2" "$(echo "${payload}" | base64 -d | gunzip)"
}

testWdtReportCommandLogOnFailure_whenEncodedLogExceedsSizeLimit_printsRecommendation() {
  WDT_COMMAND_LOG_ENCODED_MAX_BYTES=1

  actual="$(wdtReportCommandLogOnFailure createDomain "${WDT_CREATE_DOMAIN_LOG}")"

  assertOutputContains "${actual}" "WDT_COMMAND_LOG_OMITTED command=createDomain"
  assertOutputContains "${actual}" "reason=encodedSizeExceeded"
  assertOutputContains "${actual}" "recommendation=\"Enable domain.spec.logHomeEnabled to preserve the full WDT command log.\""
  assertOutputDoesNotContain "${actual}" "data="
}

# shellcheck source=target/classes/shunit/shunit2
. "${SHUNIT2_PATH}"
