# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Important: Functions defined in this file can work with unknown shells,
#            e.g. pure shell instead of bash, when UNKNOWN_SHELL has been set to true. 

#
# trace [-cloc caller-location] -n [log-level] [text]*
# trace [-cloc caller-location] -pipe [log-level] [text]*
# trace [-cloc caller-location] [log-level] [text]*
#
#   Generate logging in a format similar to WLST utils.py using the
#   same timestamp format as the Operator, and using the same 
#   log levels as the Operator. This logging is may be parsed by the
#   Operator when it reads in a job or pod log.
#
#   log-level can be one of SEVERE|ERROR|WARNING|INFO|CONFIG|FINE|FINER|FINEST
#     - Default is 'FINE'.
#     - NOTE: Use SEVERE, ERROR, WARNING, INFO sparingly since these log-levels
#             are visible by default in the Operator log and the Operator captures
#             some script logs and echos them to the Operator log.
#     - if it's ERROR it's converted to SEVERE
#     - if there's no log-level and the text starts with a
#       recognized keyword like 'Error:' then the log-level is inferred
#     - if there's no log-level and the text does not start with
#       a recognized keyword, the log-level is assumed to be 'FINE' (the default)
#
#   -n     Suppress new-line.
#
#   -pipe  Redirect stdout through a trace, see example below.
#
#   -cloc  Use the supplied value as the caller location
#
#   examples:
#     trace "Situation normal."
#     @[2018-09-28T18:10:52.417000Z][myscript.sh:91][FINE] Situation normal.
#
#     trace INFO "Situation normal."
#     @[2018-09-28T18:10:52.417000Z][myscript.sh:91][INFO] Situation normal.
#
#     trace "Info: Situation normal."
#     @[2018-09-28T18:10:52.417000Z][myscript.sh:91][INFO] Info: Situation normal.
#
#     ls 2>&1 | tracePipe FINE "ls output: "
#     @[2018-09-28T18:10:52.417000Z][myscript.sh:91][FINE] ls output: file1
#     @[2018-09-28T18:10:52.417000Z][myscript.sh:91][FINE] ls output: file2
#
#   Set TRACE_INCLUDE_FILE env var to false to suppress file name and line number.
#
export AUXILIARY_IMAGE_COMMAND_LOGS_DIR="${AUXILIARY_IMAGE_COMMAND_LOGS_DIR:-compatibilityModeInitContainerLogs}"

trace() {
  (
  set +x

  local logLoc=""
  if [ ${TRACE_INCLUDE_FILE:-true} = "true" ]; then
    if [ "$1" = "-cloc" ]; then
      logLoc="$2"
      shift
      shift
    else
      if [[ "$UNKNOWN_SHELL" = "true" ]]; then
        logLoc=`basename $0`:$LINENO
      else
        logLoc="$(basename ${BASH_SOURCE[1]}):${BASH_LINENO[0]}"
      fi
    fi
  else
    if [ "$1" = "-cloc" ]; then
      shift
      shift
    fi
  fi

  local logMode='-normal'
  case $1 in
    -pipe|-n) logMode=$1; shift; ;;
  esac

  # Support log-levels in operator, if unknown then assume FINE
  #  SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST
  local logLevel='FINE'

  #(Convert the var to upper case.)
  local level=`echo $1 | tr [a-z] [A-Z]`
  case ${level} in
    SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST)
      logLevel=$level
      shift
      ;;
    ERROR)
      logLevel='SEVERE'
      shift
      ;;
    WARNING*)
      logLevel='WARNING'
      ;;
    ERROR*|SEVERE*)
      logLevel='SEVERE'
      ;;
    INFO*)
      logLevel='INFO'
      ;;
    CONFIG*)
      logLevel='CONFIG'
      ;;
    FINEST*)
      logLevel='FINEST'
      ;;
    FINER*)
      logLevel='FINER'
      ;;
    FINE*)
      logLevel='FINE'
      ;;
  esac

  logPrefix() {
    echo "@[`timestamp`][$logLoc][$logLevel]"
  }

  case $logMode in 
    -pipe) 
          (
          # IFS='' causes read line to preserve leading spaces
          # -r cause read to treat backslashes as-is, e.g. '\n' --> '\n'
          IFS=''
          while read -r line; do
            echo "$(logPrefix)" "$@" "$line"
          done
          )
          ;;
    -n)
          echo -n "$(logPrefix)" "$@"
          ;;
    *)
          echo "$(logPrefix)" "$@"
          ;;
  esac
  )
}

#
# traceDirs before|after DOMAIN_HOME LOG_HOME DATA_HOME ...
#   Trace contents and owner of directory for the specified env vars...

traceDirs() {
  trace "id = '`id`'"
  local keyword="$1"
  shift
  local indir
  local val_indir
  for indir in $*; do
    eval "val_indir=\"\$${indir}\""
    [ -z "${val_indir}" ] && continue
    trace "Directory trace for $indir=${val_indir} ($keyword)"
    local cnt=0
    local odir=""
    local cdir="${val_indir}/*"
    while [ ${cnt} -lt 30 ] && [ ! "$cdir" = "$odir" ]; do
      echo "  ls -ld $cdir:"
      ls -ld $cdir 2>&1 | sed 's/^/    /'
      odir="$cdir"
      cdir="`dirname "$cdir"`"
      cnt=$((cnt + 1))
    done
  done
}

# timestamp
#   purpose:  echo timestamp in the form yyyy-mm-ddThh:mm:ss.nnnnnnZ
#   example:  2018-10-01T14:00:00.000001Z
timestamp() {
  local timestamp="`date --utc '+%Y-%m-%dT%H:%M:%S.%NZ' 2>&1`"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S.000000Z' 2>&1`"
  fi
  echo "${timestamp}"
}

# 
# checkEnv [-q] envvar1 envvar2 ...
#
#   purpose: Check and trace the values of the provided env vars.
#            If any env vars don't exist or are empty, return non-zero
#            and trace an '[SEVERE]'.
#            (Pass '-q' to suppress FINE tracing.)
#
#   sample:  checkEnv HOST NOTSET1 USER NOTSET2
#            @[2018-10-05T22:48:04.368000Z][FINE] HOST='esscupcakes'
#            @[2018-10-05T22:48:04.393000Z][FINE] USER='friendly'
#            @[2018-10-05T22:48:04.415000Z][SEVERE] The following env vars are missing or empty:  NOTSET1 NOTSET2
#
checkEnv() {
  local do_fine="true"
  if [ "$1" = "-q" ]; then 
    do_fine="false"
    shift
  fi
  
  local not_found=""
  local val_1
  while [ ! -z "${1}" ]; do 
    eval "val_1=\"\$${1}\""
    if [ -z "${val_1}" ]; then
      not_found="$not_found ${1}"
    else
      [ "$do_fine" = "true" ] && trace FINE "${1}='${val_1}'"
    fi
    shift
  done
  if [ ! -z "${not_found}" ]; then
    trace SEVERE "The following env vars are missing or empty: ${not_found}"
    return 1
  fi
  return 0
}

# Create a folder and test access to it
#   Arg $1 - path of folder to create
#   Arg $2 - optional wording to append to the FINE and SEVERE traces
createFolder() {
  local targetDir="${1}"
  local folderDescription="${2:-}"
  local mkdirCommand="mkdir -m 750 -p $targetDir"

  trace FINE "Creating folder '${targetDir}' using command '${mkdirCommand}'. ${folderDescription}"

  local mkdirOutput="$($mkdirCommand 2>&1)"
  [ ! -z "$mkdirOutput" ] && echo "$mkdirOutput"

  if [ ! -d "$targetDir" ]; then
    trace SEVERE "Unable to create folder '${targetDir}' using command '${mkdirCommand}', error='${mkdirOutput}'. ${folderDescription}"
    return 1
  fi

  local touchFile="${targetDir}/testaccess.tmp"
  local touchCommand="touch $touchFile"

  rm -f "${touchFile}"
  local touchOutput="$($touchCommand 2>&1)"
  [ ! -z "$touchOutput" ] && echo "$touchOutput"

  if [ ! -f "$touchFile" ] ; then
    trace SEVERE "Cannot write a file to directory '${targetDir}' using command '${touchCommand}', error='${touchOutput}'. ${folderDescription}"
    return 1
  fi

  rm -f "${touchFile}"
  return 0
}

#
# initAuxiliaryImage
#   purpose: Copy the WDT installation and model files from the source locations. The WDT install source location can be specified using 
#            'spec.configuration.model.auxiliaryImages[].sourceWDTInstallHome' and the WDT model files source location can be specified
#            using 'spec.configuration.model.auxiliaryImages[].sourceModelHome'.
#            If the WDT install source location location is not specified then copy from the default location '/auxiliary/weblogic-deploy'.
#            If the WDT model files location is not specified then copy from the default location '/auxiliary/models'. If the source location is 
#            set to 'None' then skip copying the files. If the source location is at default value and there are no files at the source location then 
#            skip copying the files. If the copy command or validation fails, log error message with details. Otherwise log a success message
#            with details.
#            See also 'auxImage.sh'.
#            See also checkAuxiliaryImage in 'utils.sh'.
#
initAuxiliaryImage() {
  local copyWdtInstallFiles=true
  local copyModelFiles=true
  local cpOut=""

  local sourceWdtInstallHome=$(echo $AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME |  tr '[a-z]' '[A-Z]')
  if [ "${sourceWdtInstallHome}" != "NONE" ]; then
    trace FINE "Auxiliary Image: About to copy WDT installation files from '${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}' " \
               "in container image='$AUXILIARY_IMAGE_CONTAINER_IMAGE'. "
    if [ "${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}" != "/auxiliary/weblogic-deploy" ]; then
      # Source WDT install home is non-default, validate that the directory exists and is non-empty.
      checkSourceWDTInstallDirExistsAndNotEmpty "${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}" || return 1
    elif [ ! -d "${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}" ]; then
      # Source WDT install home is at the default and directory doesn't exist. Ignore.
      trace FINE "Auxiliary Image: The directory '${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}' doesn't exist. Skip copying WDT install files."
      copyWdtInstallFiles=false
    elif [ -z "$(ls -A ${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME})" ]; then
      # Source WDT install home is at the default and no files found at the location. Ignore.
      trace FINE "Auxiliary Image: The directory '${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}' is empty. Skip copying WDT install files."
      copyWdtInstallFiles=false
    fi
    if [ "${copyWdtInstallFiles}" == "true" ]; then
      createFolder "${AUXILIARY_IMAGE_TARGET_PATH}/weblogic-deploy" "This is the target directory for WDT installation files." || return 1
      if [ ! -z "$(ls -A ${AUXILIARY_IMAGE_TARGET_PATH}/weblogic-deploy)" ] ; then
        trace SEVERE "The target directory for WDT installation files '${AUXILIARY_IMAGE_TARGET_PATH}/weblogic-deploy' is not empty. " \
        "This is usually because multiple auxiliary images are specified, and more than one specified a WDT install, " \
        "which is not allowed; if this is the problem, then you can correct the problem by setting the " \
        "'domain.spec.configuration.model.auxiliaryImages.sourceWDTInstallHome' to 'None' on images that you " \
        "don't want to have an install copy. Exiting."
        return 1
      fi
      cpOut=$(cp -R ${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}/* \
                                          ${AUXILIARY_IMAGE_TARGET_PATH}/weblogic-deploy \
                                          2>&1)
      if [ $? -ne 0 ]; then
       trace SEVERE "Failed to copy WDT installation files from the '${AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME}' directory."
       echo $cpOut
       return 1
      fi
    fi
  fi

  local sourceWdtModelHome=$(echo $AUXILIARY_IMAGE_SOURCE_MODEL_HOME |  tr '[a-z]' '[A-Z]')
  if [ "${sourceWdtModelHome}" != "NONE" ]; then
    trace FINE "Auxiliary Image: About to copy WDT model files from '${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}' " \
               "in container image='$AUXILIARY_IMAGE_CONTAINER_IMAGE'. "
    if [ "${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}" != "/auxiliary/models" ]; then
      # Source model home is non-default, validate that the directory exists and is non-empty.
      checkSourceWDTModelHomeDirExistsAndNotEmpty "${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}" || return 1
    elif [ ! -d "${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}" ]; then
      # Source WDT model home is at the default and directory doesn't exist. Ignore.
      trace FINE "Auxiliary Image: The directory '${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}' doesn't exist. Skip copying WDT model files."
      copyModelFiles=false
    elif [ -z "$(ls -A ${AUXILIARY_IMAGE_SOURCE_MODEL_HOME})" ]; then
      # Source WDT model home is at the default and no files found at the location. Ignore.
      trace FINE "Auxiliary Image: The directory '${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}' is empty. Skip copying WDT model files."
      copyModelFiles=false
    fi
    if [ "${copyModelFiles}" == "true" ]; then
      createFolder "${AUXILIARY_IMAGE_TARGET_PATH}/models" "This is the target directory for WDT model files." || return 1
      cpOut=$(cp -R ${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}/* \
                                          ${AUXILIARY_IMAGE_TARGET_PATH}/models \
                                          2>&1)
      if [ $? -ne 0 ]; then
        trace SEVERE "Failed to copy WDT model files from the '${AUXILIARY_IMAGE_SOURCE_MODEL_HOME}' directory."
        echo $cpOut
        return 1
      fi
    fi
  fi

  trace FINE "Auxiliary Image: Commands executed successfully."
}


checkSourceWDTInstallDirExistsAndNotEmpty() {
  local sourceWdtInstallHomeDir=$1

  if [ ! -d "${sourceWdtInstallHomeDir}" ] ; then
    trace SEVERE "Source WDT install home directory '$sourceWdtInstallHomeDir' specified in " \
      "'spec.configuration.model.auxiliaryImages[].sourceWDTInstallHome' for image '$AUXILIARY_IMAGE_CONTAINER_IMAGE' does not exist. " \
      "Make sure the 'sourceWDTInstallHome' is correctly specified and the WDT installation files are available in this directory " \
      "or set 'sourceWDTInstallHome' to 'None' for this image."
    return 1
  else
    if [ -z "$(ls -A $sourceWdtInstallHomeDir)" ] ; then
      trace SEVERE "Source WDT install home directory '$sourceWdtInstallHomeDir' specified in " \
        "'spec.configuration.model.auxiliaryImages[].sourceWDTInstallHome' for image '$AUXILIARY_IMAGE_CONTAINER_IMAGE' is empty. Make sure " \
        "the 'sourceWDTInstallHome' is correctly specified and the WDT installation files are available in this directory " \
        "or set 'sourceWDTInstallHome' to 'None' for this image."
      return 1
    fi
  fi
}

#
# initCompatibilityModeInitContainersWithLegacyAuxImages
#   purpose: Execute the AUXILIARY_IMAGE_COMMAND specified as part of the compatibility mode init container
#            with legacy auxiliary image. If the specified AUXILIARY_IMAGE_COMMAND is empty, it logs an error
#            message and returns. If the command execution fails, it logs error message with failure details.
#            Otherwise it logs a success message with details.
#            See also 'auxImage.sh'.
#            See also checkCompatibilityModeInitContainersWithLegacyAuxImages in 'utils.sh'.
#
initCompatibilityModeInitContainersWithLegacyAuxImages() {

  if [ -z "${AUXILIARY_IMAGE_COMMAND}" ]; then
    trace SEVERE "Compatibility Auxiliary Image: The 'serverPod.auxiliaryImages.command' is empty for the " \
                "container image='$AUXILIARY_IMAGE_CONTAINER_IMAGE'. Exiting."
    return 1
  fi

  trace FINE "Compatibility Auxiliary Image: About to execute command '$AUXILIARY_IMAGE_COMMAND' in container image='$AUXILIARY_IMAGE_CONTAINER_IMAGE'. " \
             "AUXILIARY_IMAGE_PATH is '$AUXILIARY_IMAGE_PATH' and AUXILIARY_IMAGE_TARGET_PATH is '${AUXILIARY_IMAGE_TARGET_PATH}'."
  traceDirs before AUXILIARY_IMAGE_PATH

  trace FINE "Compatibility Auxiliary Image: About to execute AUXILIARY_IMAGE_COMMAND='$AUXILIARY_IMAGE_COMMAND' ."
  results=$(eval $AUXILIARY_IMAGE_COMMAND 2>&1)
  if [ $? -ne 0 ]; then
    trace SEVERE "Compatibility Auxiliary Image: Command '$AUXILIARY_IMAGE_COMMAND' execution failed in container image='$AUXILIARY_IMAGE_CONTAINER_IMAGE' " \
                "with AUXILIARY_IMAGE_PATH=$AUXILIARY_IMAGE_PATH. Error -> '$results' ."
    return 1
  else
    trace FINE "Compatibility Auxiliary Image: Command '$AUXILIARY_IMAGE_COMMAND' executed successfully. Output -> '$results'."
  fi
}

checkSourceWDTModelHomeDirExistsAndNotEmpty() {
  local sourceWdtModelHomeDir=$1

  if [ ! -d "${sourceWdtModelHomeDir}" ] ; then
    trace SEVERE "Source WDT model home directory '$sourceWdtModelHomeDir' specified in " \
       "'spec.configuration.model.auxiliaryImages[].sourceModelHome' for image '$AUXILIARY_IMAGE_CONTAINER_IMAGE' does not exist. " \
       "Make sure the 'sourceModelHome' is correctly specified and the WDT model files are available in this directory " \
       "or set 'sourceModelHome' to 'None' for this image."
    return 1
  else
    if [ -z "$(ls -A $sourceWdtModelHomeDir)" ] ; then
      trace SEVERE "Source WDT installation directory '$sourceWdtModelHomeDir' specified in " \
        "'spec.configuration.model.auxiliaryImages[].sourceModelHome' for image '$AUXILIARY_IMAGE_CONTAINER_IMAGE' is empty. " \
        "Make sure the 'sourceModelHome' is correctly specified and the WDT model files are available in this directory " \
        "or set 'sourceModelHome' to 'None' for this image."
      return 1
    fi
  fi
}
