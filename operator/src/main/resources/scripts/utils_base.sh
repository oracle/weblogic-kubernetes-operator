# Copyright (c) 2021, Oracle and/or its affiliates.
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
function trace() {
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

  function logPrefix() {
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

function traceDirs() {
  trace "id = '`id`'"
  local keyword="$1"
  shift
  local indir
  local val_indir
  for indir in $*; do
    eval "val_indir=\"\${indir}\""
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
function timestamp() {
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
function checkEnv() {
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

#
# initCommonMount
#   purpose: Execute the COMMON_MOUNT_COMMAND specified as part of the common mount init container.
#            If the specified COMMON_MOUNT_COMMAND is empty, it logs an error message and returns.
#            If the COMMON_MOUNT_PATH directory doesn't exist or is empty, it logs error and returns.
#            If the command execution fails, it logs errror message with failure details. Otherwise it
#            logs a success message with details.
#            See also 'commonMount.sh'.
#            See also checkCommonMount in 'utils.sh'.
#
function initCommonMount() {

  if [ -z "${COMMON_MOUNT_COMMAND}" ]; then
    trace SEVERE "Common Mount: The 'serverPod.commonMounts.command' is empty for the " \
                "container image='$COMMON_MOUNT_CONTAINER_IMAGE'. Exiting."
    return
  fi

  trace FINE "Common Mount: About to execute command '$COMMON_MOUNT_COMMAND' in container image='$COMMON_MOUNT_CONTAINER_IMAGE'. " \
             "COMMON_MOUNT_PATH is '$COMMON_MOUNT_PATH' and COMMON_MOUNT_TARGET_PATH is '${COMMON_MOUNT_TARGET_PATH}'."
  traceDirs before $COMMON_MOUNT_PATH

  if [ ! -d ${COMMON_MOUNT_PATH} ] ||  [ -z "$(ls -A ${COMMON_MOUNT_PATH})" ]; then
    trace SEVERE "Common Mount: Dir '${COMMON_MOUNT_PATH}' doesn't exist or is empty. Exiting."
    return
  fi

  trace FINE "Common Mount: About to execute COMMON_MOUNT_COMMAND='$COMMON_MOUNT_COMMAND' ."
  results=$(eval $COMMON_MOUNT_COMMAND 2>&1)
  if [ $? -ne 0 ]; then
    trace SEVERE "Common Mount: Command '$COMMON_MOUNT_COMMAND' execution failed in container image='$COMMON_MOUNT_CONTAINER_IMAGE' " \
                "with COMMON_MOUNT_PATH=$COMMON_MOUNT_PATH. Error -> '$results' ."
  else
    trace FINE "Common Mount: Command '$COMMON_MOUNT_COMMAND' executed successfully. Output -> '$results'."
  fi
}
