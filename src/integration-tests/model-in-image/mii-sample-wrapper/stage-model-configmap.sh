#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Usage: 'stage_model_configmap.sh'
#
#   If 'WORKDIR/model-configmaps/uid-DOMAIN_UID' doesn't already exist,
#   this script stages wdt model files from 'WORKDIR/model-configmaps/datasource'
#   to this directory.
#
#   The staging is for a Kubernetes configmap that is optionally
#   referenced by the  domain resource 'configuration.model.configMap' field.
# 
#   The './create-model-configmap.sh' script uses this location
#   by default.
#
# Optionally set these environment variables:
#
#   WORKDIR
#     Working directory for the sample with at least 10GB of space.
#     Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#   DOMAIN_UID
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
echo "@@ Info: Staging model configmap files from WORKDIR/sample-model-configmap to WORKDIR/model-configmap"
echo "@@"

target_dir=$WORKDIR/model-configmaps/uid-$DOMAIN_UID

if [ -e $target_dir ]; then
    echo "@@"
    echo "@@ -----------------------------------------------------------------------"
    echo "@@ Info: NOTE! Skipping config map staging since directory                "
    echo "@@             'WORKDIR/model-configmaps/uid-DOMAIN_UID' already exists.  "
    echo "@@             To force staging, delete this directory first.             "
    echo "@@ -----------------------------------------------------------------------"
    echo "@@"
    exit 0
fi

mkdir -p $target_dir
cp $WORKDIR/model-configmaps/datasource/* $target_dir
