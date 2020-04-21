# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This file sets the defaults for this sample's scripts, loads 
# WORKDIR/custom-env.sh (which may contain customized values
# set by the user), and does other actions that are common
# to all of the sample's scripts.
#
# Assumption:
#   WORKDIR already exists.  See 'stage-workdir.sh'.
#

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

source $WORKDIR/env-custom.sh

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

if    [ ! "$WDT_DOMAIN_TYPE" = "WLS" ] \
   && [ ! "$WDT_DOMAIN_TYPE" = "RestrictedJRF" ] \
   && [ ! "$WDT_DOMAIN_TYPE" = "JRF" ]; then
  echo "@@ Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'."
  exit 1
fi

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}
CUSTOM_DOMAIN_NAME=${CUSTOM_DOMAIN_NAME:-domain1}

MODEL_CONFIGMAP_DIR=${MODEL_CONFIGMAP_DIR:-$WORKDIR/model-configmap}

DOMAIN_RESOURCE_TEMPLATE="${DOMAIN_RESOURCE_TEMPLATE:-$SCRIPTDIR/sample-domain-resource/k8s-domain.yaml.template-$WDT_DOMAIN_TYPE}"

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
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
MODEL_IMAGE="${MODEL_IMAGE_NAME}:${MODEL_IMAGE_TAG}"
MODEL_IMAGE_BUILD=${MODEL_IMAGE_BUILD:-when-changed}
MODEL_DIR=${MODEL_DIR:-$WORKDIR/model}

INCLUDE_MODEL_CONFIGMAP=${INCLUDE_MODEL_CONFIGMAP:-false}

echo "@@"
echo "@@ ######################################################################"
echo "@@ Info: Running '$(basename "$0")'."
echo "@@ Info: WORKDIR='$WORKDIR'."
echo "@@ Info: SCRIPTDIR='$SCRIPTDIR'."

#
# fail if WDT domain type changed since last invoke
#

mkdir -p ${WORKDIR}
model_type_file=${WORKDIR}/.model_type.txt
if [ -e ${model_type_file} ]; then
  last_wdt_domain_type=$(cat ${model_type_file})
  if [ ! "$WDT_DOMAIN_TYPE" = "${last_wdt_domain_type}" ]; then
    echo "@@ Error: This sample does not support changing the WDT_DOMAIN_TYPE once its set. If you need to change the WDT type, then start over with a fresh WORKDIR directory. Current WDT type: '$WDT_DOMAIN_TYPE'. Last WDT type: '${last_wdt_domain_type}'."
    exit 1
  fi
else
  echo $WDT_DOMAIN_TYPE > ${WORKDIR}/.model_type.txt
fi

