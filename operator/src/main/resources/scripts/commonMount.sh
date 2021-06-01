#!/bin/sh

# Init container script for the common mount feature. 
# See 'domain.spec.serverPod.commonMounts' for details.

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
# the contents of /${COMMON_MOUNT_PATH}/common-mount-logs/
# and fail if they are missing, or if any do not
# include 'executed successfully', or if the scripts
# cannot create (touch) files in /${COMMON_MOUNT_PATH}.
# (See also utils.sh checkCommonMount function)

scriptDir="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

if [ "${debug}" == "true" ]; then set -x; fi;

source ${scriptDir}/utils_base.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${scriptDir}/utils_base.sh" && exit 1
UNKNOWN_SHELL=true

checkEnv COMMON_MOUNT_TARGET_PATH COMMON_MOUNT_CONTAINER_NAME || exit 1

initCommonMount > /tmp/commonMount.out 2>&1
cat /tmp/commonMount.out
mkdir -p ${COMMON_MOUNT_TARGET_PATH}/commonMountLogs
cp /tmp/commonMount.out ${COMMON_MOUNT_TARGET_PATH}/commonMountLogs/${COMMON_MOUNT_CONTAINER_NAME}.out
exit
