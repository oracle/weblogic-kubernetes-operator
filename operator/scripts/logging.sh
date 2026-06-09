#!/bin/bash
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

logging_value_or_default() {
  local value="$1"
  local default_value="$2"
  local max_value="$3"

  if [[ "$value" =~ ^[0-9]+$ ]] && (( value > 0 )); then
    if (( value > max_value )); then
      echo "$max_value"
    else
      echo "$value"
    fi
  else
    echo "$default_value"
  fi
}

configure_logging() {
  LOGGING_CONFIG="/tmp/logging.properties"
  LOGGING_LEVEL="${JAVA_LOGGING_LEVEL:-INFO}"

  case "$LOGGING_LEVEL" in
    OFF|SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST|ALL)
      ;;
    *)
      echo "WARNING: Invalid JAVA_LOGGING_LEVEL='${LOGGING_LEVEL}', using WARNING"
      LOGGING_LEVEL="WARNING"
      ;;
  esac

  LOGGING_HANDLERS="java.util.logging.ConsoleHandler"
  LOGGING_FILE_CONFIG=""
  LOGGING_DIR="${OPERATOR_LOGDIR:-/logs}"

  case "$LOGGING_DIR" in
    ""|..|../*|*/../*)
      echo "WARNING: Invalid OPERATOR_LOGDIR='${LOGGING_DIR}', file logging disabled"
      ;;
    *)
      mkdir -m 777 -p "$LOGGING_DIR"
      LOGGING_MAX_SIZE=$(logging_value_or_default "${JAVA_LOGGING_MAXSIZE:-20000000}" 20000000 100000000)
      LOGGING_FILE_COUNT=$(logging_value_or_default "${JAVA_LOGGING_COUNT:-10}" 10 100)
      LOGGING_HANDLERS="${LOGGING_HANDLERS},java.util.logging.FileHandler"
      LOGGING_FILE_CONFIG="java.util.logging.FileHandler.level=${LOGGING_LEVEL}
java.util.logging.FileHandler.formatter=oracle.kubernetes.operator.logging.OperatorLoggingFormatter
java.util.logging.FileHandler.pattern=${LOGGING_DIR}/operator%g.log
java.util.logging.FileHandler.limit=${LOGGING_MAX_SIZE}
java.util.logging.FileHandler.count=${LOGGING_FILE_COUNT}"
      ;;
  esac

  {
    echo ".level=WARNING"
    echo "handlers="
    echo "Operator.level=${LOGGING_LEVEL}"
    echo "Operator.useParentHandlers=false"
    echo "Operator.handlers=${LOGGING_HANDLERS}"
    echo "java.util.logging.ConsoleHandler.level=${LOGGING_LEVEL}"
    echo "java.util.logging.ConsoleHandler.formatter=oracle.kubernetes.operator.logging.OperatorLoggingFormatter"
    if [[ -n "$LOGGING_FILE_CONFIG" ]]; then
      echo "$LOGGING_FILE_CONFIG"
    fi
  } > "$LOGGING_CONFIG"

  LOGGING="-Djava.util.logging.config.file=${LOGGING_CONFIG}"
}
