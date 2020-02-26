#!/bin/bash -x
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
function cleanup {
	${PROJECT_ROOT}/src/integration-tests/bash/cleanup.sh
}

export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."

cleanup || true