# Copyright (c) 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -o pipefail

#
# Purpose:
#   Defines various shared utility functions, including a trace function.
#
# Load this file via the following pattern:
#   SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
#   source ${SCRIPTPATH}/utils.sh
#   [ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1
#

# exportInstallHomes
#   purpose:  export MW_HOME, WL_HOME, ORACLE_HOME
#             with defaults as needed 
function exportInstallHomes() {
  export ORACLE_HOME=${ORACLE_HOME:-${MW_HOME}}

  if [ -z ${ORACLE_HOME} ]; then
    if [ -z ${WL_HOME} ]; then
      export ORACLE_HOME='/u01/oracle'
    else
      export ORACLE_HOME="`dirname ${WL_HOME}`"
    fi
  fi

  export WL_HOME=${WL_HOME:-${ORACLE_HOME}/wlserver}
  export MW_HOME=${MW_HOME:-${ORACLE_HOME}}
}

# timestamp
#   purpose:  echo timestamp in the form yyyymmddThh:mm:ss.mmm ZZZ
#   example:  20181001T14:00:00.001 UTC
function timestamp() {
  local timestamp="`date --utc '+%Y-%m-%dT%H:%M:%S %N %s %Z' 2>&1`"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S 000000 %s %Z' 2>&1`"
  fi
  local ymdhms="`echo $timestamp | awk '{ print $1 }'`"
  # convert nano to milli
  local milli="`echo $timestamp | awk '{ print $2 }' | sed 's/\(^...\).*/\1/'`"
  local secs_since_epoch="`echo $timestamp | awk '{ print $3 }'`"
  local millis_since_opoch="${secs_since_epoch}${milli}"
  local timezone="`echo $timestamp | awk '{ print $4 }'`"
  echo "${ymdhms}.${milli} ${timezone}"
}

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
#     @[2018-09-28T18:10:52.417 UTC][myscript.sh:91][FINE] Situation normal.
#
#     trace INFO "Situation normal."
#     @[2018-09-28T18:10:52.417 UTC][myscript.sh:91][INFO] Situation normal.
#
#     trace "Info: Situation normal."
#     @[2018-09-28T18:10:52.417 UTC][myscript.sh:91][INFO] Info: Situation normal.
#
#     ls 2>&1 | tracePipe FINE "ls output: "
#     @[2018-09-28T18:10:52.417 UTC][myscript.sh:91][FINE] ls output: file1
#     @[2018-09-28T18:10:52.417 UTC][myscript.sh:91][FINE] ls output: file2
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
      logLoc="`basename $0`:${BASH_LINENO[0]}"
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
  #  (The '^^' trick below converts the var to upper case.)
  local logLevel='FINE'
  case ${1^^} in
    SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST)
      logLevel=$1
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

  logPrefix="@[`timestamp`][$logLoc][$logLevel]"

  case $logMode in 
    -pipe) 
          (
          # IFS='' causes read line to preserve leading spaces
          # -r cause read to treat backslashes as-is, e.g. '\n' --> '\n'
          IFS=''
          while read -r line; do
            echo "$logPrefix" "$@" "$line"
          done
          )
          ;;
    -n)
          echo -n "$logPrefix" "$@"
          ;;
    *)
          echo "$logPrefix" "$@"
          ;;
  esac
  )
}

#
# tracen
#   purpose: same as "trace -n"
#
function tracen() {
  (
  set +x
  trace -cloc "`basename $0`:${BASH_LINENO[0]}" -n "$@"
  )
}

#
# tracePipe
#   purpose:  same as "trace -pipe"
#
function tracePipe() {
  (
  set +x
  trace -cloc "`basename $0`:${BASH_LINENO[0]}" -pipe "$@"
  )
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
#            @[2018-10-05T22:48:04.368 UTC][FINE] HOST='esscupcakes'
#            @[2018-10-05T22:48:04.393 UTC][FINE] USER='friendly'
#            @[2018-10-05T22:48:04.415 UTC][SEVERE] The following env vars are missing or empty:  NOTSET1 NOTSET2
#
function checkEnv() {
  local do_fine="true"
  if [ "$1" = "-q" ]; then 
    do_fine="false"
    shift
  fi
  
  local not_found=""
  while [ ! -z "${1}" ]; do 
    if [ -z "${!1}" ]; then
      not_found="$not_found ${1}"
    else
      [ "$do_fine" = "true" ] && trace FINE "${1}='${!1}'"
    fi
    shift
  done
  if [ ! -z "${not_found}" ]; then
    trace SEVERE "The following env vars are missing or empty: ${not_found}"
    return 1
  fi
  return 0
}

# traceEnv:
#   purpose: trace a curated set of env vars
#   warning: we purposely avoid dumping all env vars
#            (K8S provides env vars with potentially sensitive network information)
#
function traceEnv() {
  local env_var
  trace FINE "Env vars ${*}:"
  for env_var in \
    DOMAIN_UID \
    NAMESPACE \
    SERVER_NAME \
    SERVICE_NAME \
    ADMIN_NAME \
    AS_SERVICE_NAME \
    ADMIN_PORT \
    ADMIN_PORT_SECURE \
    USER_MEM_ARGS \
    JAVA_OPTIONS \
    FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR \
    STARTUP_MODE \
    DOMAIN_HOME \
    LOG_HOME \
    SERVER_OUT_IN_POD_LOG \
    DATA_HOME \
    KEEP_DEFAULT_DATA_HOME \
    EXPERIMENTAL_LINK_SERVER_DEFAULT_DATA_DIR \
    JAVA_HOME \
    ORACLE_HOME \
    WL_HOME \
    MW_HOME \
    NODEMGR_HOME \
    INTROSPECT_HOME \
    PATH
  do
    echo "    ${env_var}='${!env_var}'"
  done
}

#
# traceDirs before|after
#   Trace contents and owner of DOMAIN_HOME/LOG_HOME/DATA_HOME directories
#
function traceDirs() {
  trace "id = '`id`'"
  local indir
  for indir in DOMAIN_HOME LOG_HOME DATA_HOME; do
    [ -z "${!indir}" ] && continue
    trace "Directory trace for $indir=${!indir} ($1)"
    local cnt=0
    local odir=""
    local cdir="${!indir}/*"
    while [ ${cnt} -lt 30 ] && [ ! "$cdir" = "$odir" ]; do
      echo "  ls -ld $cdir:"
      ls -ld $cdir 2>&1 | sed 's/^/    /'
      odir="$cdir"
      cdir="`dirname "$cdir"`"
      cnt=$((cnt + 1))
    done
  done
}


#
# exportEffectiveDomainHome
#   if DOMAIN_HOME='${ORACLE_HOME}/user_projects/domains':
#     1) look for a config.xml in DOMAIN_HOME/config and
#        and in DOMAIN_HOME/*/config
#     2) Export DOMAIN_HOME to reflect the actual location 
#     3) Trace an Error and return non-zero if not found or more than 1 found
#
function exportEffectiveDomainHome() {
  local count=0
  local cur_domain_home=""
  local eff_domain_home=""
  local found_configs=""

  exportInstallHomes

  if [ ! "${DOMAIN_HOME?}" = "${ORACLE_HOME}/user_projects/domains" ]; then
    # nothing to do
    return 0
  fi

  local tfile=$(mktemp /tmp/homes.`basename $0`.XXXXXXXXX)
  rm -f $tfile
  ls -d ${DOMAIN_HOME} ${DOMAIN_HOME}/* > $tfile
  exec 22<> $tfile

  while read -u 22 cur_domain_home; do

    config_path="${cur_domain_home}/config/config.xml"

    if [ ! -f "${config_path}" ]; then
      continue
    fi

    count=$((count + 1))

    if [ $count -eq 1 ]; then
      eff_domain_home="${cur_domain_home}"
      found_configs="'${config_path}'"
    else
      found_configs="${found_configs}, '${config_path}'"
    fi

  done

  rm -f $tfile

  if [ $count -eq 1 ]; then
    export DOMAIN_HOME="${eff_domain_home}"
    return 0
  fi

  if [ $count -eq 0 ]; then
    trace SEVERE "No config.xml found at DOMAIN_HOME/config/config.xml or DOMAIN_HOME/*/config/config.xml, DOMAIN_HOME='$DOMAIN_HOME'. Check your 'domainHome' setting in your WebLogic Operator Domain resource, and your pv/pvc mount location (if any)."
    return 1
  fi
    
  # if we get this far, count is > 1 
  trace SEVERE "More than one config.xml found at DOMAIN_HOME/config/config.xml and DOMAIN_HOME/*/config/config.xml, DOMAIN_HOME='$DOMAIN_HOME': ${found_configs}. Configure your 'domainHome' setting in your WebLogic Operator Domain resource to reference a single WebLogic domain."
  return 1
}

# generateDomainSecretMD5
#  puts MD5 of domain secret in file $1
#  returns 1 on failure
#  called by introspector job
# 
generateDomainSecretMD5File()
{
  local dsf="$DOMAIN_HOME/security/SerializedSystemIni.dat"
  local md5f="$1"

  trace "Placing MD5 checksum of current domain secret 'DOMAIN_HOME/security/SerializedSystemIni.dat' in file '$1'."

  if [ ! -f "$dsf" ]; then
    trace SEVERE "Missing domain secret. Expected to find the domain secret in file 'DOMAIN_HOME/security/SerializedSystemIni.dat' where DOMAIN_HOME='$DOMAIN_HOME'. The operator requires an initialized DOMAIN_HOME to ensure that all jobs and pods in the domain share the same domain secret. You can use WLST or WDT to setup a DOMAIN_HOME."
    return 1
  fi

  md5sum "$dsf" > $1 || return 1
}

# checkDomainSecretMD5
#  verifies MD5 of domain secret matches MD5 found by introspector job
#  called by WL server pods
#
checkDomainSecretMD5()
{
  local orig_md5_file="/weblogic-operator/introspector/DomainSecret.md5"

  trace "Comparing domain secret MD5 generated by introspector '$orig_md5_file' with MD5 of current domain secret."

  if [ ! -f "$orig_md5_file" ]; then
    trace "Skipping domain secret MD5 check. File '$orig_md5_file' is missing. This means we're upgrading a live domain from an older version of the operator (which won't generate this file)."
    return 0
  fi

  local cur_md5_file="$(mktemp /tmp/CurrentDomainSecret.md5.`basename $0`.XXXXXXXXX)"

  generateDomainSecretMD5File "$cur_md5_file" || return 1

  diff -B "$cur_md5_file" "$orig_md5_file" > ${cur_md5_file}.diff 2>&1

  if [ ! "$?" = "0" ]; then
    trace SEVERE "Domain secret mismatch. The domain secret in 'DOMAIN_HOME/security/SerializedSystemIni.dat' where DOMAIN_HOME='$DOMAIN_HOME' does not match the domain secret found by the introspector job. WebLogic requires that all WebLogic servers in the same domain share the same domain secret. See 'Domain Secret Mismatch' in the operator FAQ (https://oracle.github.io/weblogic-kubernetes-operator/faq/domain-secret-mismatch/). MD5 checksum diff:"

    # The operator will copy over the following to its log at the same time it copies the above SEVERE:

    cat ${cur_md5_file}.diff

    # sleep to give administrators time to capture the pod log before the pod exits
    sleep 60

    return 1
  fi

  rm -f "${cur_md5_file}"
  rm -f "${cur_md5_file}.diff"

  trace "Domain secret MD5 matches."
}

# versionCmp
#   Compares two wl versions $1 $2 up to the length of $2
#     Expects form N.N.N.N
#     Uses '0' for an N in $1 if $1 is shorter than $2
#   echo "1"  if v1 >  v2
#   echo "-1" if v1 <  v2
#   echo "0"  if v1 == v2
versionCmp()
{
  IFS='.' read -r -a v1_arr <<< "`echo $1`"
  IFS='.' read -r -a v2_arr <<< "`echo $2`"

  for i in "${!v2_arr[@]}"
  do
    [ ${v1_arr[i]:-0} -gt ${v2_arr[i]:-0} ] && echo "1" && return
    [ ${v1_arr[i]:-0} -lt ${v2_arr[i]:-0} ] && echo "-1" && return
  done
  echo "0"
}

# versionGE
#   return success if WL v1 >= v2
versionGE()
{
  [ `versionCmp "$1" "$2"` -ge 0 ] && return 0
  return 1
}

# versionEQ
#   return success if v1 == v2
versionEQ()
{
  [ `versionCmp "$1" "$2"` -eq 0 ] && return 0
  return 1
}

# hasWebLogicPatches
#   check for the given patch numbers in the install inventory, 
#   and return 1 if not found
#   - if we can't find the install inventory then we 
#     assume the patch is there...
#   - we parse the install inventory as this is far faster than
#     using opatch or weblogic.version
hasWebLogicPatches()
{
  local reg_file=$ORACLE_HOME/inventory/registry.xml
  [ ! -f $reg_file ] && return 0
  for pnum in "$@"; do
    grep --silent "patch-id=\"$1\"" $reg_file || return 1
  done
}

# getWebLogicVersion
#   parse wl version from install inventory
#   - if we can't get a version number then we return
#     a high dummy version number that's sufficient
#     to pass version checks "9999.9999.9999.9999"
#   - we parse the install inventory as this is far faster than
#     using opatch or weblogic.version
getWebLogicVersion()
{
  local reg_file=$ORACLE_HOME/inventory/registry.xml

  [ ! -f $reg_file ] && echo "9999.9999.9999.9999" && return

  # The following grep captures both "WebLogic Server" and "WebLogic Server for FMW"
  local wlver="`grep 'name="WebLogic Server.*version=' $reg_file \
               | sed 's/.*version="\([0-9.]*\)".*/\1/g'`"

  echo ${wlver:-"9999.9999.9999.9999"}
}


# checkWebLogicVersion
#   check if the WL version is supported by the Operator
#   - skip check if SKIP_WL_VERSION_CHECK = "true"
#   - log an error if WL version < 12.2.1.3
#   - log an error if WL version == 12.2.1.3 && patch 29135930 is missing
#     - you can override the required 12.2.1.3 patches by exporting
#       global WL12213REQUIREDPATCHES to an empty string or to other
#       patch number(s)
#   - return 1 if logged an error
#   - return 0 otherwise
checkWebLogicVersion()
{
  [ "$SKIP_WL_VERSION_CHECK" = "true" ] && return 0
  local cur_wl_ver="`getWebLogicVersion`"
  local exp_wl_ver="12.2.1.3" 
  local exp_wl_12213_patches="${WL12213REQUIREDPATCHES:-"29135930"}"
  if versionEQ "$cur_wl_ver" "12.2.1.3" ; then
    if ! hasWebLogicPatches $exp_wl_12213_patches ; then
      trace SEVERE "The Operator requires that WebLogic version '12.2.1.3' have patch '$exp_wl_12213_patches'. To bypass this check, set env var SKIP_WL_VERSION_CHECK to 'true'."
      return 1
    fi
  fi
  if versionEQ "$cur_wl_ver" "9999.9999.9999.9999" ; then
    trace INFO "Could not determine WebLogic version. Assuming version is fine. (The Operator requires WebLogic version '${exp_wl_ver}' or higher, and also requires patches '$exp_wl_12213_patches' for version '12.2.1.3'.)."
    return 0
  fi
  if versionGE "$cur_wl_ver" "${exp_wl_ver}" ; then
    trace INFO "WebLogic version='$cur_wl_ver'. Version check passed. (The Operator requires WebLogic version '${exp_wl_ver}' or higher)."
  else
    trace SEVERE "WebLogic version='$cur_wl_ver' and the Operator requires WebLogic version '${exp_wl_ver}' or higher. To bypass this check, set env var SKIP_WL_VERSION_CHECK to 'true'."
    return 1
  fi
  return 0
}


#
# getAdminUrl
#   purpose: Get the admin URL used to connect to the admin server internally, e.g. when starting a managed server
#   sample:
#     ADMIN_URL=$(getAdminServerUrl)
#
function getAdminServerUrl() {
  local admin_protocol="http"
  if [ "${ISTIO_ENABLED}" = "true" ]; then
    admin_protocol="t3"
  fi 

  if [ "${ADMIN_SERVER_PORT_SECURE}" = "true" ]; then
    if [ "${ISTIO_ENABLED}" = "true" ]; then
      admin_protocol="t3s"
    else
      admin_protocol="https"
    fi
  fi
  echo ${admin_protocol}://${AS_SERVICE_NAME}:${ADMIN_PORT}
}

function waitForShutdownMarker() {
  #
  # Wait forever.   Kubernetes will monitor this pod via liveness and readyness probes.
  #
  trace "Wait indefinitely so that the Kubernetes pod does not exit and try to restart"
  while true; do
    if [ -e ${SHUTDOWN_MARKER_FILE} ] ; then
      exit 0
    fi
    sleep 3
  done
}

#
# Define helper fn for failure debugging
#   If the livenessProbeSuccessOverride file is available, do not exit from startServer.sh.
#   This will cause the pod to stay up instead of restart.
#   (The liveness probe checks the same file.)
#

function exitOrLoop {
  if [ -f /weblogic-operator/debug/livenessProbeSuccessOverride ]
  then
    waitForShutdownMarker
  else
    exit 1
  fi
}

#
# Define helper fn to create a folder
#

function createFolder {
  mkdir -m 750 -p $1
  if [ ! -d $1 ]; then
    trace SEVERE "Unable to create folder $1"
    exitOrLoop
  fi
}

# Returns the count of the number of files in the specified directory
function countFilesInDir() {
  dir=${1}
  cnt=`find ${dir} -type f | wc -l`
  [ $? -ne 0 ] && trace SEVERE "failed determining number of files in '${dir}'" && exitOrLoop
  trace "file count in directory '${dir}': ${cnt}"
  return ${cnt}
}

# Creates symbolic link from source directory to target directory
function createSymbolicLink() {
  targetDir=${1}
  sourceDir=${2}
  /bin/ln -sFf ${targetDir} ${sourceDir}
  [ $? -ne 0 ] && trace SEVERE "failed to create symbolic link from '${sourceDir}' to '${targetDir}'" && exitOrLoop
  trace "Created symbolic link from '${sourceDir}' to '${targetDir}'"
}

# The following function will attempt to create a symbolic link from the server's default 'data' directory,
# (${DOMAIN_HOME}/servers/${SERVER_NAME}/data) to the centralized data directory specified by the
# 'dataHome' attribute of the CRD ($DATA_HOME/${SERVER_NAME}/data).  If both the ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
# and $DATA_HOME/${SERVER_NAME}/data directories contain persistent files that the Operator can't resolve
# than an error message is logged asking the user to manually resolve the files and then exit.
function linkServerDefaultDir() {
  # if server's default 'data' directory (${DOMAIN_HOME}/servers/${SERVER_NAME}/data) does not exist than create
  # symbolic link to location specified by $DATA_HOME/${SERVER_NAME}/data
  if [ ! -d ${DOMAIN_HOME}/servers/${SERVER_NAME}/data ]; then
    trace "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' does NOT exist as a directory"

    # Create the server's directory in $DOMAIN_HOME/servers
    if [ ! -d ${DOMAIN_HOME}/servers/${SERVER_NAME} ]; then
      trace "Creating directory '${DOMAIN_HOME}/servers/${SERVER_NAME}'"
      createFolder ${DOMAIN_HOME}/servers/${SERVER_NAME}
    else
      trace "'${DOMAIN_HOME}/servers/${SERVER_NAME}' already exists as a directory"
    fi

    # If server's 'data' directory is not already a symbolic link than create the symbolic link to
    # $DATA_HOME/${SERVER_NAME}/data
    if [ ! -L ${DOMAIN_HOME}/servers/${SERVER_NAME}/data ]; then
      createSymbolicLink ${DATA_HOME}/${SERVER_NAME}/data ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
    else
      trace "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' is already a symbolic link"
    fi
  else
    trace "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' exists as a directory"

    # server's default 'data' directory (${DOMAIN_HOME}/servers/${SERVER_NAME}/data) exists so first verify it's
    # not a symbolic link.  If it's already a symbolic link than there is nothing to do.
    if [ -L ${DOMAIN_HOME}/servers/${SERVER_NAME}/data ]; then
      trace "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' is already a symbolic link"
    else
      # Server's default 'data' directory (${DOMAIN_HOME}/servers/${SERVER_NAME}/data) exists and is not
      # a symbolic link so must be a directory.

      # count number of files found under directory ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
      countFilesInDir ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
      fileCountServerDomainHomeDir=$?

      # count number of files found under directory ${DATA_HOME}/${SERVER_NAME}/data
      countFilesInDir ${DATA_HOME}/${SERVER_NAME}/data
      fileCountServerDataDir=$?

      # Use file counts to determine whether or not we can create a symbolic link to centralize
      # data directory in specified ${DATA_HOME}/${SERVER_NAME}/data directory.
      if [ ${fileCountServerDataDir} -eq 0 ]; then
        if [ ${fileCountServerDomainHomeDir} -ne 0 ]; then
          cp -rf ${DOMAIN_HOME}/servers/${SERVER_NAME}/data ${DATA_HOME}/${SERVER_NAME}
          [ $? -ne 0 ] && trace SEVERE "failed to copy directory/files from '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' to '${DATA_HOME}/${SERVER_NAME}' directory" && exitOrLoop
          trace "Recursively copied directory/files from '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' to '${DATA_HOME}/${SERVER_NAME}' directory"
        else
          trace "'${DOMAIN_HOME}/servers/${SERVER_NAME}/data' directory is empty"
        fi

        # forcefully delete the server's data directory so we can create symbolic link
        rm -rf ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
        [ $? -ne 0 ] && trace SEVERE "failed to delete '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' directory" && exitOrLoop
        trace "Deleted directory '${DOMAIN_HOME}/servers/${SERVER_NAME}/data'"

        # Create the symbolic link from server's data directory to $DATA_HOME
        createSymbolicLink ${DATA_HOME}/${SERVER_NAME}/data ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
      elif [ ${fileCountServerDataDir} -ne 0 ]; then
        if [ ${fileCountServerDomainHomeDir} -ne 0 ]; then
          trace SEVERE "The directory located in DOMAIN_HOME at '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' and the directory located in the domain resource dataHome directory at '${DATA_HOME}/${SERVER_NAME}/data' both contain persistent files and the Operator cannot resolve which directory to use. You must manually move any persistent files from the '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' directory to '${DATA_HOME}/${SERVER_NAME}/data', or remove them, and then delete the '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' directory. Once this is done you can then restart the Domain. Alternatively, you can avoid this validation by setting the 'KEEP_DEFAULT_DATA_HOME' environment variable, in which case WebLogic custom and default stores will use the dataHome location (ignoring any files in the DOMAIN_HOME location), and other services will use the potentially ephemeral DOMAIN_HOME location for their files."
          exitOrLoop
        else
          # forcefully delete the server's data directory so we can create symbolic link
          rm -rf ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
          [ $? -ne 0 ] && trace SEVERE "failed to delete '${DOMAIN_HOME}/servers/${SERVER_NAME}/data' directory" && exitOrLoop
          trace "Deleted directory '${DOMAIN_HOME}/servers/${SERVER_NAME}/data'"

          # Create the symbolic link from server's data directory to $DATA_HOME
          createSymbolicLink ${DATA_HOME}/${SERVER_NAME}/data ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
        fi
      fi
    fi
  fi
}

#
# adjustPath
#   purpose: Prepend $PATH with $JAVA_HOME/bin if $JAVA_HOME is set
#            and if $JAVA_HOME/bin is not already in $PATH
#
function adjustPath() {
  if [ ! -z ${JAVA_HOME} ]; then
    if [[ ":$PATH:" != *":${JAVA_HOME}/bin:"* ]]; then
      export PATH="${JAVA_HOME}/bin:$PATH"
    fi
  fi
}
