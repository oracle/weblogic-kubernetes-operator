# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -o pipefail

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils_base.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils_base.sh" && exit 1

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
# traceTiming
#   purpose: specially decorated trace statements for timing measurements
#
function traceTiming() {
  [ "${TRACE_TIMING^^}" = "TRUE" ] && trace INFO "TIMING: ""$@"
}

#
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
    PATH \
    TRACE_TIMING \
    OPERATOR_ENVVAR_NAMES
  do
    echo "    ${env_var}='${!env_var}'"
  done
}

#
# internal helper for logFileRotate():
#   return all files that match ${1}NNNNN in numeric order
#
function logFiles() {
  ls -1 ${1}[0-9][0-9][0-9][0-9][0-9] 2>/dev/null
}

#
# internal helper for logFileRotate():
#   return all files that match ${1}NNNNN in reverse order
#
function logFilesReverse() {
  ls -1r ${1}[0-9][0-9][0-9][0-9][0-9] 2>/dev/null
}

#
# internal helper for logFileRotate():
#   parse NNNNN out of $1, but if not found at end of $1 return 0
#
function logFileNum() {
  local logNum=$(echo "$1" | sed 's/.*\([0-9][0-9][0-9][0-9][0-9]\)$/\1/' | sed 's/^0*//')
  echo ${logNum:-0}
}

#
# internal helper for logFileRotate():
#   Rotate file from ${1} to ${1}NNNNN
#     $1 = filename
#     $2 = max files to keep
#     $3 = if "quiet", then suppress any tracing 
#   See logFileRotate() for detailed usage.A
#
function logFileRotateInner() {
  local logmax=${2:-7}
  local logcur

  [ $logmax -le 1 ] && logmax=1
  [ $logmax -gt 40000 ] && logmax=40000

  # find highest numbered log file (0 if none found)

  local lastlogfile=$(logFiles "$1" | tail -n 1)
  local lastlognum=$(logFileNum $lastlogfile)

  # delete oldest log files

  local _logmax_=$logmax
  if [ -f "$1" ]; then
    # account for the current file (the one we're about to rotate) if there is one
    _logmax_=$((logmax - 1))
  fi
  for logcur in $(logFilesReverse ${1} | tail -n +${_logmax_}); do
    if [ -f "$logcur" ] ; then
      [ ! "$3" = "quiet" ] && trace "Removing old log file '${logcur}'."
      rm $logcur
    fi
  done

  # if highest lognum is 99999, renumber existing files starting with 1
  # (there should be no overlap because we just deleted older files)

  if [ $lastlognum -ge 99999 ]; then
    lastlognum=0
    local logcur
    for logcur in $(logFiles "$1"); do
      lastlognum=$((lastlognum + 1))
      if [ -f "$logcur" ] ; then
        mv "$logcur" "${1}$(printf "%0.5i" $lastlognum)"
      fi
    done
  fi

  # rotate $1 if it exists, or simply remove it if logmax is 1

  if [ ${logmax} -gt 1 ]; then
    if [ -f "$1" ]; then
      local nextlognum=$((lastlognum + 1))
      [ ! "$3" = "quiet" ] && trace "Rotating '$1' to '${1}$(printf "%0.5i" $nextlognum)'."
      if [ -f "$1" ] ; then
        mv "$1" "${1}$(printf "%0.5i" $nextlognum)"
      fi
    fi
  else
    if [ -f "$1" ] ; then
     rm -f "$1"
    fi
  fi
}
#
# internal helper for logFileRotate():
#
function testLFRWarn() {
  trace WARNING "File rotation test failed. Log files named '${1}' will not be rotated, errcode='${2}'."
}

#
# internal helper for logFileRotate():
#   Convert new-lines to space, multi-spaces to single space, and trim
#
function testTR() {
  tr '\n' ' ' | sed 's/  */ /g' | sed 's/ $//g'
}

#
# internal helper for logFileRotate():
#   Verify  logFileRotateInner works, return non-zero if not.
#
function testLogFileRotate() {
  local curfile=${1:-/tmp/unknown}
  local fname=$(dirname $curfile)/testFileRotate.$RANDOM.$SECONDS.tmp
  mkdir -p $(dirname $curfile)

  rm -f ${fname}*
  logFileRotateInner ${fname} 7 quiet
  [ ! "$(ls ${fname}* 2>/dev/null)" = "" ]                  && testLFRWarn "$curfile" A1 && return 1
  echo "a" > ${fname} && logFileRotateInner ${fname} 2 quiet
  [ ! "$(ls ${fname}*)" = "${fname}00001" ]                 && testLFRWarn "$curfile" B1 && return 1
  [ ! "$(cat ${fname}00001)" = "a" ]                        && testLFRWarn "$curfile" B2 && return 1
  logFileRotateInner ${fname} 2 quiet
  [ ! "$(ls ${fname}*)" = "${fname}00001" ]                 && testLFRWarn "$curfile" C1 && return 1
  [ ! "$(cat ${fname}00001)" = "a" ]                        && testLFRWarn "$curfile" C2 && return 1
  echo "b" > ${fname} && logFileRotateInner ${fname} 2 quiet
  [ ! "$(ls ${fname}*)" = "${fname}00002" ]                 && testLFRWarn "$curfile" C3 && return 1
  [ ! "$(cat ${fname}00002)" = "b" ]                        && testLFRWarn "$curfile" C4 && return 1
  echo "c" > ${fname} && logFileRotateInner ${fname} 0 quiet
  [ ! "$(ls ${fname}* 2>/dev/null)" = "" ]                  && testLFRWarn "$curfile" D1 && return 1

  echo 1 > ${fname} && logFileRotateInner ${fname} 3 quiet
  [ ! "$(ls ${fname}* | testTR)" = "${fname}00001" ]               && testLFRWarn "$curfile" E1 && return 1
  echo 2 > ${fname} && logFileRotateInner ${fname} 3 quiet
  [ ! "$(ls ${fname}* | testTR)" = "${fname}00001 ${fname}00002" ] && testLFRWarn "$curfile" E2 && return 1
  echo 3 > ${fname} && logFileRotateInner ${fname} 3 quiet
  [ ! "$(ls ${fname}* | testTR)" = "${fname}00002 ${fname}00003" ] && testLFRWarn "$curfile" E3 && return 1
  echo 4 > ${fname} && logFileRotateInner ${fname} 3 quiet
  [ ! "$(ls ${fname}* | testTR)" = "${fname}00003 ${fname}00004" ] && testLFRWarn "$curfile" E4 && return 1
  [ ! "$(cat ${fname}00003)" = "3" ]                               && testLFRWarn "$curfile" E5 && return 1
  [ ! "$(cat ${fname}00004)" = "4" ]                               && testLFRWarn "$curfile" E6 && return 1
  local count
  rm ${fname}*
  echo "0" > ${fname}
  for count in 99997 99998 99999; do
    echo $count > ${fname}${count}
  done
  logFileRotateInner ${fname} 4 quiet
  [ ! "$(ls ${fname}* | testTR)" = "${fname}00001 ${fname}00002 ${fname}00003" ]  \
                                                            && testLFRWarn "$curfile" F1 && return 1
  [ ! "$(cat ${fname}00001)" = "99998" ]                    && testLFRWarn "$curfile" F2 && return 1
  [ ! "$(cat ${fname}00002)" = "99999" ]                    && testLFRWarn "$curfile" F3 && return 1
  [ ! "$(cat ${fname}00003)" = "0" ]                        && testLFRWarn "$curfile" F4 && return 1
  logFileRotateInner ${fname} 2 quiet
  [ ! "$(ls ${fname}*)" = "${fname}00003" ]                 && testLFRWarn "$curfile" F5 && return 1
  rm ${fname}*
  return 0
}

#
# logFileRotate
#   Rotate file from ${1} to ${1}NNNNN, starting with 00001.
#     $1 = filename
#     $2 = max log files, default is 7
#   Notes:
#     - $2 = 0 or 1 implies there should be no saved files
#            and causes $1 to be removed instead of rotated
#
#     - Silently tests rotation on scratch files first, and,
#       if that fails logs a WARNING, does nothing, and returns.
#
#     - If current max file is 99999, then old files are
#       renumbered starting with 00001.
#
function logFileRotate() {
  # test rotation, if it fails, log a Warning that rotation of $1 is skipped.
  testLogFileRotate "$1" || return 0
  # now do the actual rotation
  logFileRotateInner "$1" $2
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

  diff -wB "$cur_md5_file" "$orig_md5_file" > ${cur_md5_file}.diff 2>&1

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
    sleep 0.1
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

#
# checkAuxiliaryImage
#   purpose: If the AUXILIARY_IMAGE_PATH directory exists, it echoes the contents of output files
#            in ${AUXILIARY_IMAGE_PATH}/auxiliaryImagetLogs dir. It returns 1 if a SEVERE message
#            is found in any of the output files in ${AUXILIARY_IMAGE_PATH}/auxiliaryImageLogs dirs.
#            It also returns 1 if 'successfully' message is not found in the output files
#            or if the AUXILIARY_IMAGE_PATH directory is empty. Otherwise it returns 0 (success).
#            See also 'auxImage.sh'.
#            See also initAuxiliaryImage in 'utils_base.sh'.
#
function checkAuxiliaryImage() {
  # check auxiliary image results (if any)
  if [ -z "$AUXILIARY_IMAGE_PATHS" ]; then
    trace FINE "Auxiliary Image: Skipping auxiliary image checks (no auxiliary images configured)."
    return
  fi

  trace FINE "Auxiliary Image: AUXILIARY_IMAGE_PATHS is '$AUXILIARY_IMAGE_PATHS'."
  for AUXILIARY_IMAGE_PATH in ${AUXILIARY_IMAGE_PATHS/,/ }; do
    trace FINE "Auxiliary Image: AUXILIARY_IMAGE_PATH is '$AUXILIARY_IMAGE_PATH'."
    traceDirs $AUXILIARY_IMAGE_PATH
    touch ${AUXILIARY_IMAGE_PATH}/testaccess.tmp
    if [ $? -ne 0 ]; then
      trace SEVERE "Auxiliary Image: Cannot write to the AUXILIARY_IMAGE_PATH '${AUXILIARY_IMAGE_PATH}'. " \
                   "This path is configurable using the domain resource 'spec.auxiliaryImageVolumes.mountPath' " \
                   "attribute." && return 1
    fi
    rm -f ${AUXILIARY_IMAGE_PATH}/testaccess.tmp || return 1

    # The container .out files embed their container name, the names will sort in the same order in which the containers ran
    out_files=$(ls -1 $AUXILIARY_IMAGE_PATH/auxiliaryImageLogs/*.out 2>/dev/null | sort --version-sort)
    if [ -z "${out_files}" ]; then
      trace SEVERE "Auxiliary Image: Assertion failure. No files found in '$AUXILIARY_IMAGE_PATH/auxiliaryImageLogs/*.out'"
      return 1
    fi
    severe_found=false
    for out_file in $out_files; do
      if [ "$(grep -c SEVERE $out_file)" != "0" ]; then
        trace FINE "Auxiliary Image: Error found in file '${out_file}' while initializing auxiliaryImage."
        severe_found=true
      elif [ "$(grep -c successfully $out_file)" = "0" ]; then
        trace SEVERE "Auxiliary Image: Command execution was unsuccessful in file '${out_file}' while initializing auxiliaryImage. " \
                     "Contents of '${out_file}':"
        cat $out_file
        severe_found=true
        continue
      fi
      trace "Auxiliary Image: Contents of '${out_file}':"
      cat $out_file
      trace "Auxiliary Image: End of '${out_file}' contents"
    done
    [ "${severe_found}" = "true" ] && return 1
    [ -z "$(ls -A $AUXILIARY_IMAGE_PATH 2>/dev/null | grep -v auxiliaryImageLogs)" ] \
      && trace SEVERE "Auxiliary Image: No files found in '$AUXILIARY_IMAGE_PATH'. " \
       "Do your auxiliary images have files in their '$AUXILIARY_IMAGE_PATH' directories? " \
       "This path is configurable using the domain resource 'spec.auxiliaryImageVolumes.mountPath' attribute." \
      && return 1
  done
  return 0
}
