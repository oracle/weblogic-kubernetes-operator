# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This file defines env vars and env defaults specific to the test.
#
# For env vars related directly to the sample see
# '$SCRIPTDIR/mii-sample-helpers/custom-env.sh'.
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

# ::: Traefik name and namespace
#  Defaults are 'traefik-operator' and '${TRAEFIK_NAME}-ns'
# export TRAEFIK_NAME=
# export TRAEFIK_NAMESPACE=
