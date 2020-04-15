#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script stages wdt model files from './sample-configmap' 
# to 'WORKDIR/configmap' for future inclusion in a Kubernetes
# configmap that is referenced by the  domain resource
# 'configuration.model.configMap' field.
#
# Optionally set these environment variables:
#
#    WORKDIR
#      Working directory for the sample with at least 10g of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
# CUSTOMIZATION NOTE:
#
#   If you want to specify your own model files for a config map, then
#   you don't need to run this script. See CONFIGMAP_DIR in 
#   'create-model-configmap.sh'
#
#   - Set the CONFIGMAP_DIR env var to indicate the location of your
#     wdt config map files prior to creating your wdt config
#     map (see ./run_domain.sh), and prior to defining your domain
#     resource (see ./stage-domain-resource.sh).
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

echo "@@"
echo "@@ Info: Staging configmap files from SCRIPTDIR/sample-configmap to WORKDIR/configmap"
echo "@@"

mkdir -p $WORKDIR/configmap
rm -f $WORKDIR/configmap/*
cp $SCRIPTDIR/sample-configmap/* $WORKDIR/configmap
