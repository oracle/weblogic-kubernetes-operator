#!/bin/sh
# Copyright (c) 2021, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Init container script for the auxiliary image feature.
# See 'domain.spec.configuration.model.auxiliaryImages' for details.

# Notes:
# This script purposely tries to exit zero even on failure as
# the Operator monitors the container running this
# script for the Intropector job case, and we have
# seen issues with non-zero exiting scripts.
#
# The operator fails the introspector if it detects an
# ERROR/SEVERE, and succeeds if it detects
# 'executed successfully'.
#
# The main introspector and pod scripts will echo
# the contents of /${AUXILIARY_IMAGE_MOUNT_PATH}/auxiliary-image-logs/
# and fail if they are missing, or if any do not
# include 'executed successfully', or if the scripts
# cannot create (touch) files in /${AUXILIARY_IMAGE_MOUNT_PATH}.
# (See also utils.sh checkAuxiliaryImage function)

scriptDir="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

if [ "${debug}" == "true" ]; then set -x; fi;

# This script runs in the auxiliary image container, which is often using
# busybox or some other very sparse base image.  This script relies on
# the determining that the shell supports the "starts with" test so
# make sure that it works and exit if it does not.

SHELL_TEST=abcd
if [[ "${SHELL_TEST}" == "ab"* ]]; then
  SHELL_IS_GOOD=true
fi

if [ -z "${SHELL_IS_GOOD}" ]; then
  echo "[SEVERE] The shell in the auxiliary image is missing required functionality. Exiting."
  exit 1
fi

. ${scriptDir}/utils_base.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${scriptDir}/utils_base.sh" && exit 1
UNKNOWN_SHELL=true

checkEnv AUXILIARY_IMAGE_TARGET_PATH AUXILIARY_IMAGE_CONTAINER_NAME || exit 1

if [[ "$AUXILIARY_IMAGE_CONTAINER_NAME" == "operator-aux-container"* ]]; then
  sucFile="${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs/${AUXILIARY_IMAGE_CONTAINER_NAME}.suc"
  if [ ! -f $sucFile ]; then
    initAuxiliaryImage > /tmp/auxiliaryImage.out 2>&1
    retval=$?
    cat /tmp/auxiliaryImage.out

    mkdir -p ${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs
    cp /tmp/auxiliaryImage.out ${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs/${AUXILIARY_IMAGE_CONTAINER_NAME}.out
    rm -f "$sucFile"
    if [ $retval -eq 0 ]; then
      echo $retval > "$sucFile"
    fi
  else
    trace FINE "Auxiliary Image: Skipping initialization due to a previous successful initialization."
  fi
elif [[ "$AUXILIARY_IMAGE_CONTAINER_NAME" == "compat-"* ]] \
     || [[ "$AUXILIARY_IMAGE_CONTAINER_NAME" == "wls-shared-"* ]]; then
  sucFile="${AUXILIARY_IMAGE_TARGET_PATH}/${AUXILIARY_IMAGE_COMMAND_LOGS_DIR}/${AUXILIARY_IMAGE_CONTAINER_NAME}.suc"
  if [ ! -f $sucFile ]; then
    initCompatibilityModeInitContainersWithLegacyAuxImages > /tmp/compatibilityModeInitContainers.out 2>&1
    retval=$?
    cat /tmp/compatibilityModeInitContainers.out

    mkdir -p "${AUXILIARY_IMAGE_TARGET_PATH}/${AUXILIARY_IMAGE_COMMAND_LOGS_DIR}"
    cp /tmp/compatibilityModeInitContainers.out "${AUXILIARY_IMAGE_TARGET_PATH}/${AUXILIARY_IMAGE_COMMAND_LOGS_DIR}/${AUXILIARY_IMAGE_CONTAINER_NAME}.out"
    rm -f "$sucFile"
    if [ $retval -eq 0 ]; then
      echo $retval > "$sucFile"
    fi
  else
    trace FINE "Auxiliary Image: Skipping initialization due to a previous successful initialization."
  fi
else
  trace SEVERE "Invalid auxiliary image container name '$AUXILIARY_IMAGE_CONTAINER_NAME'. " \
               "The auxiliary image container name must start with either 'operator-aux-container' " \
               "or 'compat-operator-aux-container'. Exiting."
  exit 1
fi
exit
