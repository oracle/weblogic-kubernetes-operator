#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Usage: run-main.sh -wait
#
# This script runs most of the sample and performs the following steps:
#
#   - Creates a working directory if there isn't already one created.
#   - Downloads the WebLogic Deploy Tooling and WebLogic Image Tool.
#   - Stages and builds a model-in-image image.
#   - Deploys secrets needed by the domain.
#   - Deletes existing domain resource if it's already deployed
#     and waits for the existing domain's pods to exit
#   - Stages and creates Traefik ingresses.
#   - Stages and deploys a domain resource that references the image.
#   - If '-wait' is passed, then it waits for the domain's pods to start.
#
# Prerequisites:
#
#    - Namespace DOMAIN_NAMESPACE exists (default 'sample-domain1-ns').
#
#    - The WebLogic operator is deployed and monitoring DOMAIN_NAMESPACE.
#
#    - If domain type is JRF, a database is deployed using 
#      sample 'kubernetes/samples/scripts/create-oracle-db-service' with
#      access as per the urls and credentials in 'create-secrets.sh'.
#
#    - Optional deployment of Traefik, where Traefik is
#      monitoring DOMAIN_NAMESPACE.
#
# Optionally set the following env vars:
#
#    WORKDIR
#      Working directory for the sample with at least 10GB of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#
#    WDT_DOMAIN_TYPE
#      WLS (default), RestrictedJRF, JRF
#
#    INCLUDE_MODEL_CONFIGMAP
#      Set to 'true' to include the sample's model configmap:
#         - stage its files
#         - deploy the configmap
#         - ensure domain resource references the configmap
#         - ensure deployed secrets includes its secret
#      Default is 'false'.  
#      (To add configmap later dynamically, see 'run-update.sh'.)
#
#    Others
#      See 'custom-env.sh'.
#      (In particular DOMAIN_UID and DOMAIN_NAMESPACE.)
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

#######################################################################
# Setup a working directory WORKDIR for running the sample. By default,
# WORKDIR is '/tmp/$USER/model-in-image-sample-work-dir'.

if [ ! -d $WORKDIR ] || [ "$(ls $WORKDIR)" = "" ]; then
  #
  # Copy over the sample to WORKDIR
  #

  mkdir -p $WORKDIR
  cp -r $MIISAMPLEDIR/* $WORKDIR
  cp $SCRIPTDIR/env-custom.sh $WORKDIR
fi

#######################################################################
# Stage the latest WebLogic Deploy Tooling and WebLogic Image Tool
# to WORKDIR. If this is run behind a proxy, then environment variables
# http_proxy and https_proxy must be set.

$SCRIPTDIR/stage-tooling.sh

#######################################################################
# Build a model image. 
#   This pulls a base image if there isn't already a local base image,
#   and, by default, builds the model image using model files from 
#   'stage-model-image.sh' plus tooling that was downloaded
#   by 'stage-tooling.sh'.

$SCRIPTDIR/build-model-image.sh

#######################################################################
# Optionally stage model configmap from 'SCRIPTDIR/sample-model-configmap'
# to 'WORKDIR/model-configmap'. Then deploy it.

if [ "${INCLUDE_MODEL_CONFIGMAP:-false}" = "true" ]; then
  $SCRIPTDIR/stage-model-configmap.sh
  $SCRIPTDIR/create-model-configmap.sh
fi

#######################################################################
# Stage a domain resource to 'WORKDIR/mii-DOMAIN_UID.yaml'.

$SCRIPTDIR/stage-domain-resource.sh

#######################################################################
# Deploy secrets needed at runtime.

$SCRIPTDIR/create-secrets.sh

#######################################################################
# Stage and create the ingresses for routing external network traffic
# from the Traefik load balancer to the admin server and managed servers.

$SCRIPTDIR/stage-and-create-ingresses.sh

#######################################################################
# Deploy domain resource but first delete any pods that are already
# running for the domain (-predelete).

$SCRIPTDIR/create-domain-resource.sh -predelete

#######################################################################
# Optionally wait for pods to start.

if [ "${1:-}" = "-wait" ]; then
  echo "@@"
  echo "@@ ######################################################################"
  echo "@@"

  $WORKDIR/utils/wl-pod-wait.sh -p 3 -d $DOMAIN_UID -n $DOMAIN_NAMESPACE
  
  echo "@@"
  echo "@@ Info: Voila! Script '$(basename $0)' completed successfully! All pods ready."
  echo "@@"
else
  echo "@@"
  echo "@@ Info: Voila! Script '$(basename $0)' completed successfully!"
  echo "@@"
fi
