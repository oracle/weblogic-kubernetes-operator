#!/bin/sh
# Copyright (c) 2023, Oracle and/or its affiliates.

scriptDir="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
if [ "${debug}" == "true" ]; then set -x; fi;

. ${scriptDir}/utils_base.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${scriptDir}/utils_base.sh" && exit 1
checkEnv DOMAIN_HOME AUXILIARY_IMAGE_TARGET_PATH || exit 1
default_ugid=${DOMAIN_HOME_ON_PV_DEFAULT_UGID:-1000:0}
mkdir -m 750 -p "${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs/" || exit 1
chown -R ${default_ugid} "${AUXILIARY_IMAGE_TARGET_PATH}"
output_file="${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs/initializeDomainHomeOnPV.out"

failure_exit() {
  chown ${default_ugid} $output_file
  cat $output_file
  exit 1
}

create_success_file_and_exit() {
  echo "0" > "${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs/initializeDomainHomeOnPV.suc"
  chown ${default_ugid} "${AUXILIARY_IMAGE_TARGET_PATH}/auxiliaryImageLogs/initializeDomainHomeOnPV.suc"
  chown ${default_ugid} $output_file 2>&1
  cat $output_file
  exit
}

create_directory_path_and_set_permission() {
  DIR_NAME="$1"
  trace "Creating directory and set path for  ${DIR_NAME}" >> "$output_file"
  if [ ! -z "${DIR_NAME}" ] ; then
    IFS='/'
    # domain home is tokenized by '/' now
    read -a dir_array <<< "${DIR_NAME}"
    IFS=$OLDIFS

    number_of_tokens=${#dir_array[@]}
    trace "Number of tokens in domain home $number_of_tokens"  >> "$output_file"

    # walk through the list of tokens backwards and reconstruct the path from beginning to find the
    # base path since the share root can be in any of the mount path

    for (( i=$number_of_tokens ; i>=1; i--))
    do
       temp_dir=${dir_array[@]:0:i}
       # parameter substitution turns spece to slash
       test_dir="/${temp_dir// //}"
       trace "Testing base mount path at $test_dir" >> "$output_file"
       if [ -d $test_dir ] ; then
           trace "Found base mount path at $test_dir" >> "$output_file"
           SHARE_ROOT=$test_dir

           trace "Creating directory path and setting the permission from shared root $SHARE_ROOT" >> "$output_file"
           if ! errmsg=$(mkdir -p "$1" 2>&1)
             then
               trace SEVERE "Could not create directory $1 specified in 'domain.spec.domainHome'.  Error: ${errmsg}" >> "$output_file"
               failure_exit
           fi
           next_level_dir=${dir_array[@]:0:i+1}
           next_level_dir="/${next_level_dir// //}"
           trace "Changing ownership of ${next_level_dir}" >> "$output_file"
           if ! errmsg=$(chown -R ${default_ugid} "${next_level_dir}" 2>&1)
             then
               trace SEVERE "Failed to change directory permission at ${next_level_dir} Error: $errmsg" >> "$output_file"
               failure_exit
           fi

           trace "Creating directory path completed" >> "$output_file"
           return 0
       fi
    done

    return 1
  fi
}

adjust_domain_home_parent_dir_ownership() {
  DOMAIN_HOME_DIR="$1"
  trace "Adjusting ownership of the parent of domain home ${DOMAIN_HOME_DIR}" >> "$output_file"
  IFS='/'
  # domain home is tokenized by '/' now
  read -a dir_array <<< "${DOMAIN_HOME_DIR}"
  IFS=$OLDIFS

  number_of_tokens=${#dir_array[@]}
  trace "Number of tokens in domain home $number_of_tokens"  >> "$output_file"
  parent_dir=${dir_array[@]:0:${number_of_tokens}-1}
  parent_dir="/${parent_dir// //}"
  trace "Changing ownership of ${parent_dir}" >> "$output_file"
  if ! errmsg=$(chown -R ${default_ugid} "${parent_dir}" 2>&1)
    then
      trace SEVERE "Failed to adjust directory ownership at ${parent_dir} Error: $errmsg" >> "$output_file"
      failure_exit
  fi

  trace "Adjusting directory ownership completed" >> "$output_file"
}

ORIGIFS=$IFS
trace "DOMAIN HOME is $DOMAIN_HOME" > "$output_file"
trace "Running the script as "`id` >> "$output_file"

if [ -f "$DOMAIN_HOME" ]; then
  if [ -f "$DOMAIN_HOME/config/config.xml" ] ; then
    trace INFO "Domain Home '$DOMAIN_HOME' already exists, no operation. Exiting with 0 return code" >> "$output_file"
    create_success_file_and_exit
  else
    trace SEVERE "Domain Home '$DOMAIN_HOME' is not empty and does not contain any WebLogic Domain. Please specify an empty directory in n 'domain.spec.domainHome'." >> "$output_file"
    failure_exit
  fi
fi

trace "Creating path for domain home '$DOMAIN_HOME'" >> "$output_file"

create_directory_path_and_set_permission "${DOMAIN_HOME}"
if [ $? -ne 0 ] ; then
  trace SEVERE "Error: Unable to initialize domain home directory: 'domain.spec.domainHome' $DOMAIN_HOME is not under mountPath in any of the 'domain.spec.serverPod.volumeMounts'" >> "$output_file"
  failure_exit
fi

adjust_domain_home_parent_dir_ownership "${DOMAIN_HOME}"
if [ $? -ne 0 ] ; then
  trace SEVERE "Error: Unable to adjust ownership of parent directory of domain home directory: 'domain.spec.domainHome' $DOMAIN_HOME" >> "$output_file"
  failure_exit
fi

if [ ! -z $LOG_HOME ] ; then
  trace "Creating path for log home $LOG_HOME" >> "$output_file"
  create_directory_path_and_set_permission "${LOG_HOME}"
  if [ $? -ne 0 ] ; then
    trace SEVERE "Error: Unable to initialize log home directory: 'domain.spec.logHome' $LOG_HOME is not under mountPath in any of the 'domain.spec.serverPod.volumeMounts'" >> "$output_file"
    failure_exit
  fi
fi

create_success_file_and_exit

