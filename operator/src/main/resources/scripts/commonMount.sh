#!/bin/sh

# Init container script for the common mount feature. 
# See 'domain.spec.serverPod.commonMount' for details.

scriptDir="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

if [ "${debug}" == "true" ]; then set -x; fi;

source ${scriptDir}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${scriptDir}/utils.sh" && exit 1

if [ -z ${COMMON_MOUNT_COMMAND} ]; then
  trace ERROR "Common Mount: The 'serverPod.commonMount.container.mountCommand' is empty for the container image='$CONTAINER_IMAGE'. Exiting"
  exit
fi

trace INFO "Common Mount: About to execute command '$COMMON_MOUNT_COMMAND' in container image='$CONTAINER_IMAGE'. COMMON_MOUNT_PATH is '$COMMON_MOUNT_PATH' and COMMON_TARGET_PATH is '${COMMON_TARGET_PATH}'."
traceDirs $COMMON_MOUNT_PATH

if [ ! -d ${COMMON_MOUNT_PATH} ] ||  [ -z "$(ls -A ${COMMON_MOUNT_PATH})" ]; then 
  trace ERROR "Common Mount: Dir '${COMMON_MOUNT_PATH}' doesn't exist or is empty. Exiting."
  exit
fi

trace FINE "Common Mount: About to execute COMMON_MOUNT_COMMAND='$COMMON_MOUNT_COMMAND' ."
results=$(eval $COMMON_MOUNT_COMMAND 2>&1)
if [ $? -ne 0 ]; then
  trace ERROR "Common Mount: Command '$COMMON_MOUNT_COMMAND' execution failed. Error -> '$results' ."
else
  trace FINE "Common Mount: Command '$COMMON_MOUNT_COMMAND' executed successfully. Output -> '$results'."
fi