#!/bin/sh
# Copyright (c) 2023, Oracle and/or its affiliates.
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


# Function to rotate WDT script log file and copy the file to WDT output dir.
# parameter:
#   1 - Name of the log file to rotate and copy to WDT output directory.
wdtRotateAndCopyLogFile() {
  local logFileName=$1

  logFileRotate "${WDT_OUTPUT_DIR}/${logFileName}" "${WDT_LOG_FILE_MAX:-11}"

  cp ${WDT_ROOT}/logs/${logFileName} ${WDT_OUTPUT_DIR}/
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
