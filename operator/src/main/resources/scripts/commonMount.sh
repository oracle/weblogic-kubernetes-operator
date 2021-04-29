#!/bin/sh

# Init container script for the common mount feature. 
# See 'domain.spec.serverPod.commonMount' for details.

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

scriptDir="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

if [ "${debug}" == "true" ]; then set -x; fi;

source ${scriptDir}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${scriptDir}/utils.sh" && exit 1

function copyFiles() {

  if [ -z "${COMMON_MOUNT_COMMAND}" ]; then
    trace ERROR "Common Mount: The 'serverPod.commonMount.container.mountCommand' is empty for the container image='$CONTAINER_IMAGE'. Exiting"
    return
  fi

  trace FINE "Common Mount: About to execute command '$COMMON_MOUNT_COMMAND' in container image='$CONTAINER_IMAGE'. COMMON_MOUNT_PATH is '$COMMON_MOUNT_PATH' and COMMON_TARGET_PATH is '${COMMON_TARGET_PATH}'."
  traceDirs $COMMON_MOUNT_PATH

  if [ ! -d ${COMMON_MOUNT_PATH} ] ||  [ -z "$(ls -A ${COMMON_MOUNT_PATH})" ]; then
    trace ERROR "Common Mount: Dir '${COMMON_MOUNT_PATH}' doesn't exist or is empty. Exiting."
    return
  fi

  trace FINE "Common Mount: About to execute COMMON_MOUNT_COMMAND='$COMMON_MOUNT_COMMAND' ."
  results=$(eval $COMMON_MOUNT_COMMAND 2>&1)
  if [ $? -ne 0 ]; then
    trace ERROR "Common Mount: Command '$COMMON_MOUNT_COMMAND' execution failed in container image='$CONTAINER_IMAGE' with COMMON_MOUNT_PATH=$COMMON_MOUNT_PATH. Error -> '$results' ."
  else
    trace FINE "Common Mount: Command '$COMMON_MOUNT_COMMAND' executed successfully. Output -> '$results'."
  fi
}

copyFiles > /tmp/commonMount.out 2>&1
cat /tmp/commonMount.out
mkdir -p ${COMMON_TARGET_PATH}/commonMountLogs
cp /tmp/commonMount.out ${COMMON_TARGET_PATH}/commonMountLogs/${CONTAINER_NUMBER}.out
exit
