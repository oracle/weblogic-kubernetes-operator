# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This file defines env vars and env defaults specific to the test.
#
# For env vars related directly to the sample see
# '$SCRIPTDIR/mii-sample-wrappers/README'.
# (Especially DOMAIN_NAMESPACE, DOMAIN_NAME, and DOMAIN_UID.)
#

SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"
MIIWRAPPERDIR="${TESTDIR}/mii-sample-wrapper"
MIISAMPLEDIR="${SRCDIR}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image"
DBSAMPLEDIR="${SRCDIR}/kubernetes/samples/scripts/create-oracle-db-service"

# ::: Directory for sample's temp files
#  should have 10GB? of space
#  default is '/tmp/$USER/model-in-image-sample-work-dir'
export WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

# ::: Operator settings
#  Defaults are 'sample-weblogic-operator', '${OPER_NAME}-ns', '${OPER_NAME}-sa', 'weblogic-kubernetes-operator', and 'test'
# export OPER_NAME=
# export OPER_NAMESPACE=
# export OPER_SA=
# export OPER_IMAGE_NAME=
# export OPER_IMAGE_TAG=

# ::: DB Settings (needed if WDT_DOMAIN_TYPE is JRF)
export DB_NAMESPACE=${DB_NAMESPACE:-default}
export DB_NODE_PORT=${DB_NODE_PORT:-30011}
export DB_IMAGE_NAME=${DB_IMAGE_NAME:-container-registry.oracle.com/database/enterprise}
export DB_IMAGE_TAG=${DB_IMAGE_TAG:-12.2.0.1-slim}
export DB_IMAGE_PULL_SECRET=${DB_IMAGE_PULL_SECRET:-docker-secret}

# ::: Traefik settings/defaults, set NODEPORT values to 0 to have
#     K8S dynamically choose the values for Traefik
export TRAEFIK_NAME=${TRAEFIK_NAME:-traefik-operator}
export TRAEFIK_NAMESPACE=${TRAEFIK_NAMESPACE:-traefik-operator-ns}
export TRAEFIK_HTTP_NODEPORT=${TRAEFIK_HTTP_NODEPORT:-30305}
export TRAEFIK_HTTPS_NODEPORT=${TRAEFIK_HTTPS_NODEPORT:-30433}

export DOMAIN_UID1="${DOMAIN_UID1:-sample-domain1}"
export DOMAIN_UID2="${DOMAIN_UID2:-sample-domain2}"
export DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}

# default max time to let introspector run for JRF runs
export INTROSPECTOR_DEADLINE_SECONDS=${INTROSPECTOR_DEADLINE_SECONDS:-600}

# default max amount of time to wait for all of a domain's pods to start
# NOTE: this _includes_ waiting for the introspector job and so should
#       be higher than INTROSPECTOR_DEADLINE_SECONDS
export POD_WAIT_TIMEOUT_SECS=${POD_WAIT_TIMEOUT_SECS:-1000}
