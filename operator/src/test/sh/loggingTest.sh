#!/usr/bin/env bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

TEST_ROOT=/tmp/test/operator-logging
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# shellcheck source=operator/scripts/logging.sh
. "${SCRIPT_DIR}/../../../scripts/logging.sh"

setUp() {
  rm -f /tmp/logging.properties
  rm -rf "${TEST_ROOT}"
  mkdir -p "${TEST_ROOT}"
  unset JAVA_LOGGING_LEVEL
  unset JAVA_LOGGING_MAXSIZE
  unset JAVA_LOGGING_COUNT
  unset OPERATOR_LOGDIR
  unset LOGGING
}

tearDown() {
  rm -f /tmp/logging.properties
  rm -rf "${TEST_ROOT}"
}

getProperty() {
  grep "^$1=" /tmp/logging.properties | sed "s|^$1=||"
}

testConfigureLogging_writesConfigToTmpAndLogFilesToOperatorLogDir() {
  JAVA_LOGGING_LEVEL=FINER
  JAVA_LOGGING_MAXSIZE=12345
  JAVA_LOGGING_COUNT=4
  OPERATOR_LOGDIR="${TEST_ROOT}/pv/logs"

  configure_logging

  assertEquals "-Djava.util.logging.config.file=/tmp/logging.properties" "${LOGGING}"
  assertTrue "logging.properties should be generated" "[ -f /tmp/logging.properties ]"
  assertEquals "" "$(getProperty handlers)"
  assertEquals "FINER" "$(getProperty Operator.level)"
  assertEquals "false" "$(getProperty Operator.useParentHandlers)"
  assertEquals "java.util.logging.ConsoleHandler,java.util.logging.FileHandler" "$(getProperty Operator.handlers)"
  assertEquals "FINER" "$(getProperty java.util.logging.ConsoleHandler.level)"
  assertEquals "FINER" "$(getProperty java.util.logging.FileHandler.level)"
  assertEquals "${OPERATOR_LOGDIR}/operator%g.log" "$(getProperty java.util.logging.FileHandler.pattern)"
  assertEquals "12345" "$(getProperty java.util.logging.FileHandler.limit)"
  assertEquals "4" "$(getProperty java.util.logging.FileHandler.count)"
  assertTrue "operator log directory should be created" "[ -d ${OPERATOR_LOGDIR} ]"
}

testConfigureLogging_withInvalidValues_usesFallbacks() {
  JAVA_LOGGING_LEVEL=DEBUG
  JAVA_LOGGING_MAXSIZE=bad
  JAVA_LOGGING_COUNT=bad
  OPERATOR_LOGDIR=../bad

  configure_logging

  assertEquals "WARNING" "$(getProperty Operator.level)"
  assertEquals "WARNING" "$(getProperty java.util.logging.ConsoleHandler.level)"
  assertEquals "" "$(getProperty handlers)"
  assertEquals "java.util.logging.ConsoleHandler" "$(getProperty Operator.handlers)"
  assertEquals "" "$(getProperty java.util.logging.FileHandler.pattern)"
}

# shellcheck source=target/classes/shunit/shunit2
. "${SHUNIT2_PATH}"
