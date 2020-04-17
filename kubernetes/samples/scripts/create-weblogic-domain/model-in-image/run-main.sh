#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Usage: run-main.sh 
#
# This script runs most of the sample. It creates a working directory
# if there isn't already one created, downloads the WebLogic Deploy
# Tooling and WebLogic Image Tool, stages and builds a model-in-image image, 
# deploys secrets needed by the domain, stages and deploys a domain
# resource that references the image, and finally waits for the
# domain's pods to start. 
#
# Prerequisites:
#    - Namespace DOMAIN_NAMESPACE exists (default 'sample-domain1-ns').
#
#    - The WebLogic operator is deployed and monitoring DOMAIN_NAMESPACE.
#
#    - If domain type is JRF, a database is deployed using 
#      sample 'kubernetes/samples/scripts/create-oracle-db-service' with
#      access as per the urls and credentials in 'create-secrets.sh'.
#
#    - Optional deployment of traefik, where traefik is
#      monitoring DOMAIN_NAMESPACE.
#
# Optionally set the following env vars:
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
# TBD add Traefik ingress setup to official sample?
#        Does quick-start setup an ingress?
#     'require' Traefik in sample?
#     add check to see if Operator running and monitoring expected ns?
#     add check to see if DB running in expected ns at expected port and responding to SQL commands?
#     add check to see if Traefik is running?

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

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

echo @@
echo @@ Info: Viola! Script '$(basename $0)' completed successfully! All pods ready.
echo @@
