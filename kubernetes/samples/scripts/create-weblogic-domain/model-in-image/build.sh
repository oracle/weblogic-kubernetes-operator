#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Usage: build.sh 
#
# This script runs most of the sample. It builds a model-in-image image, 
# deploys a domain resources that references the image, and waits for
# the domain's pods to start. It subsequently deploys a model configmap
# that adds a data source to the model configuration, deploys an updated
# domain resource that references the model configmap, and patches the
# running domain's 'restart version' to force it to regenerate its
# configuration and roll its WebLogic pods.
#
# Optionally set the following env var:
#
#    WORKDIR
#      Working directory for the sample with at least 10g of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    WDT_DOMAIN_TYPE
#      WLS (default), RestrictedJRF, JRF
#
#    Others
#      See 'custom-env.sh'.
#
# Prerequisites:
#
#    - The WebLogic operator is deployed and monitoring DOMAIN_NAMESPACE
#      (default sample1-domain-ns).
#
#    - If domain type is JRF, database deployed using 
#      sample 'kubernetes/samples/scripts/create-oracle-db-service' with
#      access as per the urls and credentials in 'create-secrets.sh'.
#
#    - Optional deployment of traefik, where traefik is
#      monitoring DOMAIN_NAMESPACE.
#
# TBD add Traefik ingress setup to official sample?
#        Does quick-start setup an ingress?
#     'require' Traefik in sample?
#     add check to see if Operator running and monitoring expected ns?
#     add check to see if DB running in expected ns at expected port and responding to SQL commands?
#     add check to see if Traefik

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

export INCLUDE_CONFIGMAP=false #tells stage-domain-resource.sh and
                               #create-secrets.sh that we're not deploying
                               #the sample's model configmap

#######################################################################
# Setup a working directory WORKDIR for running the sample. By default,
# WORKDIR is '/tmp/$USER/model-in-image-sample-work-dir'.

$SCRIPTDIR/stage-workdir.sh

#######################################################################
# Stage the latest WebLogic Deploy Tooling and WebLogic Image Tool
# to WORKDIR. If this is run behind a proxy, then environment variables
# http_proxy and https_proxy must be set.

$SCRIPTDIR/stage-tooling.sh

#######################################################################
# Stage sample model yaml, sample model properties, and sample app
# to the 'WORKDIR/model' dir.

$SCRIPTDIR/stage-model-image.sh

#######################################################################
# Build a model image. 
#   This pulls a base image if there isn't already a local base image,
#   and, by default, builds the model image using model files from 
#   'stage-model-model-image.sh' plus tooling that was downloaded
#   by './stage-tooling.sh'.

$SCRIPTDIR/build-model-image.sh

#######################################################################
# Stage a domain resource to 'WORKDIR/k8s-domain.yaml'.

$SCRIPTDIR/stage-domain-resource.sh

#######################################################################
# Deploy secrets needed at runtime.

$SCRIPTDIR/create-secrets.sh

#######################################################################
# Deploy domain resource but first delete any pods that are already
# running for the domain (-predelete).

$SCRIPTDIR/create-domain-resource.sh -predelete

#######################################################################
# Wait for pods to start.

$SCRIPTDIR/util-wl-pod-wait.sh -p 3

#######################################################################
# Add a datasource to the running domain via a model config map:
#
#  - Stage domain resource again (will uncomment references to the
#    config map and its secret).
#  - Stage model configmap from 'SCRIPTDIR/sample-configmap' to 
#    'WORKDIR/configmap'.
#  - Deploy configmap.
#  - Deploy secrets again (will include extra secret needed by configmap).
#  - Deploy updated domain resource.
#  - Patch domain resource restart version. 
#     This will force introspector to rerun and regenerate
#     WebLogic config with the model files from the configmap, and 
#     will also force a subsequent rolling restart.
#  - Wait for pods to roll and reach the new restart version.
#

export INCLUDE_CONFIGMAP=true #tells stage-domain-resource.sh and
                              #create-secrets.sh to account for configmap

$SCRIPTDIR/stage-domain-resource.sh
$SCRIPTDIR/stage-model-configmap.sh

$SCRIPTDIR/create-model-configmap.sh
$SCRIPTDIR/create-secrets.sh
$SCRIPTDIR/create-domain-resource.sh

$SCRIPTDIR/util-patch-restart-version.sh
$SCRIPTDIR/util-wl-pod-wait.sh -p 3
