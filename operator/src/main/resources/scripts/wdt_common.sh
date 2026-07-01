#!/bin/sh
# Copyright (c) 2023, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# limit the file extensions in the model directories

checkModelDirectoryExtensions() {
  trace "Entering checkModelDirectoryExtensions"

  cd ${IMG_MODELS_HOME}
  counter=$(ls  -I  "*.yaml" -I "*.zip" -I "*.properties" | wc -l)
  if [ $counter -ne 0 ] ; then
    trace SEVERE "Model image home '${IMG_MODELS_HOME}' contains files with unsupported extensions." \
      "Expected extensions: '.yaml', '.properties', or '.zip'." \
      "Files with unsupported extensions: '$(ls -I "*.yaml" -I "*.zip" -I "*.properties")'"
    exitOrLoop
  fi
  if [ -d ${WDT_CONFIGMAP_ROOT} ] ; then
    cd ${WDT_CONFIGMAP_ROOT}
    counter=$(ls  -I  "*.yaml" -I "*.properties" | wc -l)
    if [ $counter -ne 0 ] ; then
      trace SEVERE "Model 'spec.configuration.model.configMap' contains files with unsupported extensions." \
        "Expected extensions: '.yaml' or '.properties'." \
        "Files with unsupported extensions: '$(ls -I "*.yaml" -I "*.properties")'"
      exitOrLoop
    fi
  fi

  trace "Exiting checkModelDirectoryExtensions"
}
# checkDirNotExistsOrEmpty
#  Test directory exists or empty

checkDirNotExistsOrEmpty() {
  trace "Entering checkDirNotExistsOrEmpty"

  if [ $# -eq 1 ] ; then
    if [ ! -d $1 ] ; then
      trace SEVERE "Directory '$1' does not exist"
      exitOrLoop
    else
      if [ -z "$(ls -A $1)" ] ; then
        trace SEVERE "Directory '$1' is empty"
        exitOrLoop
      fi
    fi
  fi

  trace "Exiting checkDirNotExistsOrEmpty"
}

# sort_files  sort the files according to the names and naming conventions and write the result to stdout
#    $1  directory
#    $2  extension
#

sort_files() {
  shopt -s nullglob
  root_dir=$1
  ext=$2
  declare -A sequence_array
  for file in ${root_dir}/*${ext} ;
    do
      actual_filename=$(basename $file)
      base_filename=$(basename ${file%.*})
      sequence="${base_filename##*.}"
      sequence_array[${actual_filename}]=${sequence}
    done
  for k in "${!sequence_array[@]}" ;
    do
      # MUST use echo , caller depends on stdout
      echo $k ' - ' ${sequence_array["$k"]}
    done |
  sort -n -k3  | cut -d' ' -f 1
  shopt -u nullglob
}

# Returns the directory where WDT writes command log files.
wdtGetLogRoot() {
  echo "${WLSDEPLOY_LOG_DIRECTORY:-${WDT_ROOT}/logs}"
}

# Returns true when log home is available for WDT command log files.
wdtIsLogHomeAvailable() {
  [ -n "${LOG_HOME}" ] && [ -d "${LOG_HOME}" ]
}

# Function to rotate WDT script log file and copy the file to log home.
# The copied log path is set in WDT_COMMAND_LOG_HOME_PATH.
# parameter:
#   1 - Name of the log file to rotate and copy to log home.
wdtCopyLogFileToLogHome() {
  local logFileName=$1
  local wdtLogRoot
  WDT_COMMAND_LOG_HOME_PATH=""

  if ! wdtIsLogHomeAvailable ; then
    return 1
  fi

  WDT_COMMAND_LOG_HOME_PATH="${LOG_HOME}/${logFileName}"
  # If the special variable WLSDEPLOY_LOG_DIRECTORY is set to log home,
  # then the command log file is already in log home.
  if [ "${WLSDEPLOY_LOG_DIRECTORY}" = "${LOG_HOME}" ] ; then
    [ -f "${WDT_COMMAND_LOG_HOME_PATH}" ]
    return $?
  fi

  wdtLogRoot="$(wdtGetLogRoot)"
  if [ ! -f "${wdtLogRoot}/${logFileName}" ] ; then
    return 1
  fi

  logFileRotate "${WDT_COMMAND_LOG_HOME_PATH}" "${WDT_LOG_FILE_MAX:-11}"
  cp "${wdtLogRoot}/${logFileName}" "${LOG_HOME}"/
}

# Function to rotate WDT script log file and copy the file to log home
# parameter:
#   1 - Name of the log file to rotate and copy to log home.
wdtRotateAndCopyLogFile() {
  wdtCopyLogFileToLogHome "$1"
  return 0
}

# Function to report a WDT command log after a failed WDT command.
# If log home is enabled, it copies the full command log to log home and prints only a pointer.
# Otherwise, it prints a single gzip+base64 line when the encoded log is under the size guard.
# parameters:
#   1 - WDT command name, such as createDomain.
#   2 - Name of the WDT command log file.
wdtReportCommandLogOnFailure() {
  local commandName=$1
  local logFileName=$2

  if wdtIsLogHomeAvailable ; then
    if wdtCopyLogFileToLogHome "${logFileName}" && [ -n "${WDT_COMMAND_LOG_HOME_PATH}" ] && [ -f "${WDT_COMMAND_LOG_HOME_PATH}" ] ; then
      trace INFO "WDT_COMMAND_LOG_AVAILABLE command=${commandName} file=${logFileName} path=${WDT_COMMAND_LOG_HOME_PATH}"
    fi
    return 0
  fi

  wdtReportEncodedCommandLog "${commandName}" "${logFileName}"
}

# Function to report a WDT command log in the pod log as one gzip+base64 line.
# parameters:
#   1 - WDT command name, such as createDomain.
#   2 - Name of the WDT command log file.
wdtReportEncodedCommandLog() {
  local commandName=$1
  local logFileName=$2
  local wdtLogRoot
  local logFilePath
  local encodedLog
  local encodedSize

  wdtLogRoot="$(wdtGetLogRoot)"
  logFilePath="${wdtLogRoot}/${logFileName}"
  if [ ! -f "${logFilePath}" ] ; then
    return 0
  fi

  encodedLog="$(mktemp /tmp/wdt_command_log.XXXXXX)"
  if ! gzip -c "${logFilePath}" | base64 | tr -d '\n' > "${encodedLog}" ; then
    rm -f "${encodedLog}"
    return 0
  fi

  encodedSize="$(wc -c < "${encodedLog}" | tr -d ' ')"
  if [ "${encodedSize}" -le "${WDT_COMMAND_LOG_ENCODED_MAX_BYTES:-1048576}" ] ; then
    trace INFO "WDT_COMMAND_LOG command=${commandName} file=${logFileName} encoding=gzip+base64 data=$(cat "${encodedLog}")"
  else
    trace INFO "WDT_COMMAND_LOG_OMITTED command=${commandName} file=${logFileName} reason=encodedSizeExceeded recommendation=\"Enable domain.spec.logHomeEnabled to preserve the full WDT command log.\""
  fi

  rm -f "${encodedLog}"
}

# These out files are generated by WDT post creation hooks
#
wdtRotateAndCopyOutFile() {

  if [ -d "${LOG_HOME}" ] && [ "${WLSDEPLOY_LOG_DIRECTORY}" != "${LOG_HOME}" ] ; then
    WDT_LOG_ROOT="${WLSDEPLOY_LOG_DIRECTORY:-$(echo $WDT_ROOT/logs)}"
    for file in "${WDT_LOG_ROOT}"/*.out ; do
      logFileRotate "${LOG_HOME}/${file}" "${WDT_LOG_FILE_MAX:-11}"
      cp "${WDT_LOG_ROOT}/${file}" "${LOG_HOME}"/
    done
  fi

}


#
# Generic error handler
#
error_handler() {
    if [ $1 -ne 0 ]; then
        # Use FINE instead of SEVERE, avoid showing in domain status
        trace FINE  "Script Error: There was an error at line: ${2} command: ${@:3:20}"
        stop_trap
        exitOrLoop
    fi
}

start_trap() {
    set -eE
    trap 'error_handler $? $BASH_LINENO $BASH_COMMAND ' ERR EXIT SIGHUP SIGINT SIGTERM SIGQUIT
}

stop_trap() {
    trap -  ERR EXIT SIGHUP SIGINT SIGTERM SIGQUIT
    set +eE
}
