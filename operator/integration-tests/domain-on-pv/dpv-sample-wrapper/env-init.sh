# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This file sets the defaults for the sample wrapper scripts, 
# and does other actions that are common to all of the sample's scripts.

WORKDIR=${WORKDIR:-/tmp/$USER/domain-on-pv-sample-work-dir}

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$SCRIPTDIR/../../../.." > /dev/null 2>&1 ; pwd -P )"
DPVSAMPLEDIR="$( cd "$SRCDIR/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv" > /dev/null 2>&1 ; pwd -P )"

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}
IMAGE_TYPE=${IMAGE_TYPE:-$WDT_DOMAIN_TYPE}

if    [ ! "$WDT_DOMAIN_TYPE" = "WLS" ] \
   && [ ! "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', or 'JRF'"
  exit 1
fi

if    [ ! "$IMAGE_TYPE" = "WLS" ] \
   && [ ! "$IMAGE_TYPE" = "JRF" ]; then 
  echo "@@ Error: Invalid image type IMAGE_TYPE '$IMAGE_TYPE': expected 'WLS', and 'JRF'."
  exit 1
fi

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
IMAGE_PULL_POLICY=${IMAGE_PULL_POLICY:-IfNotPresent}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}
CUSTOM_DOMAIN_NAME=${CUSTOM_DOMAIN_NAME:-domain1}

DOMAIN_RESOURCE_TEMPLATE="${DOMAIN_RESOURCE_TEMPLATE:-dpv-domain.yaml.template-$IMAGE_TYPE}"
DOMAIN_RESOURCE_FILENAME="${DOMAIN_RESOURCE_FILENAME:-domain-resources/dpv-${DOMAIN_UID}.yaml}"

DB_NAMESPACE=${DB_NAMESPACE:-default}

DOWNLOAD_WIT=${DOWNLOAD_WIT:-when-missing}
DOWNLOAD_WDT=${DOWNLOAD_WDT:-when-missing}
WDT_INSTALLER_URL=${WDT_INSTALLER_URL:-https://github.com/oracle/weblogic-deploy-tooling/releases/latest}
WIT_INSTALLER_URL=${WIT_INSTALLER_URL:-https://github.com/oracle/weblogic-image-tool/releases/latest}

if [[ "$WDT_DOMAIN_TYPE" = "WLS" ]]; then
  defaultBaseImage="container-registry.oracle.com/middleware/weblogic"
else
  defaultBaseImage="container-registry.oracle.com/middleware/fmw-infrastructure"
fi

BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-$defaultBaseImage}"
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.4}
BASE_IMAGE="${BASE_IMAGE_NAME}:${BASE_IMAGE_TAG}"

DOMAIN_CREATION_IMAGE_NAME=${DOMAIN_CREATION_IMAGE_NAME:-wdt-domain-image}
DOMAIN_CREATION_IMAGE_TAG=${DOMAIN_CREATION_IMAGE_TAG:-${IMAGE_TYPE}-v1}
DOMAIN_CREATION_IMAGE="${DOMAIN_CREATION_IMAGE_NAME}:${DOMAIN_CREATION_IMAGE_TAG}"
DOMAIN_CREATION_IMAGE_BUILD=${DOMAIN_CREATION_IMAGE_BUILD:-always}
MODEL_DIR=${MODEL_DIR:-wdt-artifacts/wdt-model-files/${DOMAIN_CREATION_IMAGE_TAG}}
AUXILIARY_IMAGE_PATH=${AUXILIARY_IMAGE_PATH:-/auxiliary}
DOMAIN_CREATION_IMAGE_DOCKER_FILE_SOURCEDIR=${DOMAIN_CREATION_IMAGE_DOCKER_FILE_SOURCEDIR:-dci-docker-file}

IMAGE_PULL_SECRET_NAME=${IMAGE_PULL_SECRET_NAME:-""}
DOMAIN_IMAGE_PULL_SECRET_NAME=${DOMAIN_IMAGE_PULL_SECRET_NAME:-""}

ARCHIVE_SOURCEDIR=${ARCHIVE_SOURCEDIR:-wdt-artifacts/archives/archive-v1}

INCLUDE_DOMAIN_CREATION_CONFIGMAP=${INCLUDE_DOMAIN_CREATION_CONFIGMAP:-false}
CORRECTED_DATASOURCE_SECRET=${CORRECTED_DATASOURCE_SECRET:-false}

INTROSPECTOR_DEADLINE_SECONDS=${INTROSPECTOR_DEADLINE_SECONDS:-300}

echo "@@"
echo "@@ ######################################################################"
[ $# -eq 0 ] && echo "@@ Info: Running '$(basename "$0")'"
[ $# -ne 0 ] && echo "@@ Info: Running '$(basename "$0") $@'"
echo "@@ Info: WORKDIR='$WORKDIR'."
echo "@@ Info: SCRIPTDIR='$SCRIPTDIR'."
echo "@@ Info: DPVSAMPLEDIR='$DPVSAMPLEDIR'."
echo "@@ Info: DOMAIN_UID='$DOMAIN_UID'."
echo "@@ Info: DOMAIN_NAMESPACE='$DOMAIN_NAMESPACE'."

