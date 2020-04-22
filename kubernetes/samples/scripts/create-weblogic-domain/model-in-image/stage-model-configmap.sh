#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Usage: 'stage_model_configmap.sh'
#
#   If 'WORKDIR/model-configmap' doesn't already exist, this script
#   stages wdt model files from './sample-model-configmap' 
#   to 'WORKDIR/model-configmap'.
#
#   The staging is for a Kubernetes configmap that is optionally
#   referenced by the  domain resource 'configuration.model.configMap' field.
# 
#   The './create-model-configmap.sh' script uses
#   'WORKDIR/model-configmap' by default.
#
# Optionally set these environment variables:
#
#   WORKDIR
#     Working directory for the sample with at least 10GB of space.
#     Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
# CUSTOMIZATION NOTE:
#
#   If you want to specify your own model files for a config map, then
#   you don't need to run this script. See MODEL_CONFIGMAP_DIR in 
#   'create-model-configmap.sh'
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

echo "@@"
echo "@@ Info: Staging model configmap files from SCRIPTDIR/sample-model-configmap to WORKDIR/model-configmap"
echo "@@"

if [ -e $WORKDIR/model-configmap ]; then
    echo "@@"
    echo "@@ -----------------------------------------------------------------------"
    echo "@@ Info: NOTE! Skipping config map staging since directory                "
    echo "@@             'WORKDIR/model-configmap' already exists.                "
    echo "@@             To force staging, delete this directory first.             "
    echo "@@ -----------------------------------------------------------------------"
    echo "@@"
    exit 0
fi

mkdir -p $WORKDIR/model-configmap
cp $SCRIPTDIR/sample-model-configmap/* $WORKDIR/model-configmap
