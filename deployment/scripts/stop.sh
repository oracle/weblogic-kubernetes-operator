#!/bin/bash
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "Setting stop signal"

DEPLOYMENT_DIR="/deployment"
SHUTDOWN_MARKER_FILE="${DEPLOYMENT_DIR}/marker.shutdown"

touch ${SHUTDOWN_MARKER_FILE}