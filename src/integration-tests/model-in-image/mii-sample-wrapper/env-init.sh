# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This file sets the defaults for this sample's scripts, loads 
# WORKDIR/custom-env.sh (which may contain customized values
# set by the user), and does other actions that are common
# to all of the sample's scripts.

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

if [ -f $WORKDIR/env-custom.sh ]; then
  source $WORKDIR/env-custom.sh
fi

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$SCRIPTDIR/../../../.." > /dev/null 2>&1 ; pwd -P )"
MIISAMPLEDIR="$( cd "$SRCDIR/kubernetes/samples/scripts/create-weblogic-domain/model-in-image" > /dev/null 2>&1 ; pwd -P )"

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

if    [ ! "$WDT_DOMAIN_TYPE" = "WLS" ] \
   && [ ! "$WDT_DOMAIN_TYPE" = "RestrictedJRF" ] \
   && [ ! "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'."
  exit 1
fi

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}
CUSTOM_DOMAIN_NAME=${CUSTOM_DOMAIN_NAME:-domain1}

MODEL_CONFIGMAP_DIR=${MODEL_CONFIGMAP_DIR:-model-configmaps/datasource}

DOMAIN_RESOURCE_TEMPLATE="${DOMAIN_RESOURCE_TEMPLATE:-mii-domain.yaml.template-$WDT_DOMAIN_TYPE}"
DOMAIN_RESOURCE_FILENAME="${DOMAIN_RESOURCE_FILENAME:-domain-resources/mii-${DOMAIN_UID}.yaml}"

DB_NAMESPACE=${DB_NAMESPACE:-default}

DOWNLOAD_WIT=${DOWNLOAD_WIT:-when-missing}
DOWNLOAD_WDT=${DOWNLOAD_WDT:-when-missing}
WDT_INSTALLER_URL=${WDT_INSTALLER_URL:-https://github.com/oracle/weblogic-deploy-tooling/releases/latest}
WIT_INSTALLER_URL=${WIT_INSTALLER_URL:-https://github.com/oracle/weblogic-image-tool/releases/latest}

if [ "$WDT_DOMAIN_TYPE" = "WLS" ]; then
  defaultBaseImage="container-registry.oracle.com/middleware/weblogic"
else
  defaultBaseImage="container-registry.oracle.com/middleware/fmw-infrastructure"
fi

BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-$defaultBaseImage}"
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.4}
BASE_IMAGE="${BASE_IMAGE_NAME}:${BASE_IMAGE_TAG}"

MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-${WDT_DOMAIN_TYPE}-v1}
MODEL_IMAGE="${MODEL_IMAGE_NAME}:${MODEL_IMAGE_TAG}"
MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-always}
MODEL_DIR=${MODEL_DIR:-model-images/$(basename $MODEL_IMAGE_NAME)__${MODEL_IMAGE_TAG}}

ARCHIVE_SOURCEDIR=${ARCHIVE_SOURCEDIR:-archives/archive-v1}

INCLUDE_MODEL_CONFIGMAP=${INCLUDE_MODEL_CONFIGMAP:-false}

echo "@@"
echo "@@ ######################################################################"
[ $# -eq 0 ] && echo "@@ Info: Running '$(basename "$0")'"
[ $# -ne 0 ] && echo "@@ Info: Running '$(basename "$0") $@'"
echo "@@ Info: WORKDIR='$WORKDIR'."
echo "@@ Info: SCRIPTDIR='$SCRIPTDIR'."
echo "@@ Info: MIISAMPLEDIR='$MIISAMPLEDIR'."
echo "@@ Info: DOMAIN_UID='$DOMAIN_UID'."
echo "@@ Info: DOMAIN_NAMESPACE='$DOMAIN_NAMESPACE'."

