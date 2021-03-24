#!/bin/bash
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Generate report of known issues in dependencies

set -e

mvn ${MAVEN_ARGS} -Dorg.slf4j.simpleLogger.defaultLogLevel=WARN org.owasp:dependency-check-maven:aggregate
