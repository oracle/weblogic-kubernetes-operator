#!/bin/sh
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This script starts a node manager for either a WebLogic Server pod,
# or for the WebLogic Operator introspector job.
#
# Requires the following to already be set:
#
#   DOMAIN_UID        = Domain UID
#   JAVA_HOME         = Existing java home
#   DOMAIN_HOME       = Existing WebLogic domain home directory
#   NODEMGR_HOME      = Target directory for NM setup files, this script
#                       will append this value with /$DOMAIN_UID/$SERVER_NAME
#
# Optionally set:
#
#   SERVER_NAME       = If not set, assumes this is introspector.
#
#   WL_HOME           = WebLogic Install Home - defaults to /u01/oracle/wlserver
#
#   NODEMGR_LOG_HOME  = Directory that will contain contain both
#                          ${DOMAIN_UID}/${SERVER_NAME}_nodemanager.log
#                          ${DOMAIN_UID}/${SERVER_NAME}_nodemanager.out
#                       Default:
#                          Use LOG_HOME.  If LOG_HOME not set, use NODEMGR_HOME.
#
# If SERVER_NAME is set, then this NM is for a WL Server and these must also be set:
# 
#   SERVICE_NAME      = Internal DNS name for WL Server SERVER_NAME
#   ADMIN_NAME        = Admin server name
#   AS_SERVICE_NAME   = Internal DNS name for Admin Server ADMIN_NAME
#   USER_MEM_ARGS     = JVM mem args for starting WL server
#   JAVA_OPTIONS      = Java options for starting WL server
#

###############################################################################
#
#  Assert that expected global env vars are already set, pre-req files/dirs exist, etc.
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

source ${SCRIPTPATH}/traceUtils.sh 
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1 

export WL_HOME="${WL_HOME:-/u01/oracle/wlserver}"

stm_script=${WL_HOME}/server/bin/startNodeManager.sh

SERVER_NAME=${SERVER_NAME:-introspector}

trace "Starting node manager for domain-uid='$DOMAIN_UID' and server='$SERVER_NAME'."

checkEnv JAVA_HOME NODEMGR_HOME DOMAIN_HOME DOMAIN_UID WL_HOME || exit 1

if [ "${SERVER_NAME}" = "introspector" ]; then
  SERVICE_NAME=localhost
else
  checkEnv SERVER_NAME ADMIN_NAME AS_SERVICE_NAME SERVICE_NAME USER_MEM_ARGS || exit 1
fi

[ ! -d "${JAVA_HOME}" ]                     && trace "Error: JAVA_HOME directory not found '${JAVA_HOME}'."           && exit 1 
[ ! -d "${DOMAIN_HOME}" ]                   && trace "Error: DOMAIN_HOME directory not found '${DOMAIN_HOME}'."       && exit 1 
[ ! -f "${DOMAIN_HOME}/config/config.xml" ] && trace "Error: '${DOMAIN_HOME}/config/config.xml' not found."           && exit 1 
[ ! -d "${WL_HOME}" ]                       && trace "Error: WL_HOME '${WL_HOME}' not found."                         && exit 1 
[ ! -f "${stm_script}" ]                    && trace "Error: Missing script '${stm_script}' in WL_HOME '${WL_HOME}'." && exit 1 

#
# Helper fn to create a folder
# Arg $1 - path of folder to create
#
function createFolder {
  mkdir -m 750 -p "$1"
  if [ ! -d "$1" ]; then
    trace "Unable to create folder '$1'."
    exit 1
  fi
}


###############################################################################
#
# Determine WebLogic server log and out files locations
#
# -Dweblogic.Stdout system property is used to tell node manager to send server .out 
#  file to the configured location
#

if [ "${SERVER_NAME}" = "introspector" ]; then
  # introspector pod doesn't start a WL server
  serverOutOption=""
else
  # setup ".out" location for a WL server
  serverLogHome="${LOG_HOME:-${DOMAIN_HOME}/servers/${SERVER_NAME}/logs}"
  export SERVER_OUT_FILE="${serverLogHome}/${SERVER_NAME}.out"
  serverOutOption="-Dweblogic.Stdout=${SERVER_OUT_FILE}"
  createFolder "${serverLogHome}"
fi


###############################################################################
#
# Init/create nodemanager home and nodemanager log env vars and directory
#

export NODEMGR_HOME=${NODEMGR_HOME}/${DOMAIN_UID}/${SERVER_NAME}

createFolder ${NODEMGR_HOME} 

NODEMGR_LOG_HOME=${NODEMGR_LOG_HOME:-${LOG_HOME:-${NODEMGR_HOME}/${DOMAIN_UID}}}

trace "Info: NODEMGR_HOME='${NODEMGR_HOME}'"
trace "Info: LOG_HOME='${LOG_HOME}'"
trace "Info: SERVER_NAME='${SERVER_NAME}'"
trace "Info: DOMAIN_UID='${DOMAIN_UID}'"
trace "Info: NODEMGR_LOG_HOME='${NODEMGR_LOG_HOME}'"

createFolder ${NODEMGR_LOG_HOME}

nodemgr_log_file=${NODEMGR_LOG_HOME}/${SERVER_NAME}_nodemanager.log
nodemgr_out_file=${NODEMGR_LOG_HOME}/${SERVER_NAME}_nodemanager.out
nodemgr_lck_file=${NODEMGR_LOG_HOME}/${SERVER_NAME}_nodemanager.log.lck

checkEnv NODEMGR_LOG_HOME nodemgr_log_file nodemgr_out_file nodemgr_lck_file

trace "remove nodemanager .lck file"
rm -f ${nodemgr_lck_file}


###############################################################################
#
# Determine domain name by parsing ${DOMAIN_HOME}/config/config.xml
#
# We need the domain name to register the domain with the node manager
# but we only have the domain home.
#
# The 'right' way to find the domain name is to use offline wlst to
# read the domain then get it from the domain mbean, but that's slow
# and complicated. Instead, just get it by reading config.xml directly.
#

# Look for the 1st occurence of <name>somestring</name> and assume somestring
# is the domain name:
domain_name=`cat ${DOMAIN_HOME}/config/config.xml | sed 's/[[:space:]]//g' | grep '^<name>' | head -1 | awk -F'<|>' '{print $3}'`
if [ "$domain_name" = "" ]; then
  trace "Could not determine domain name"
  exit 1
fi


###############################################################################
#
# Create nodemanager.properties and nodemanager.domains files in NM home 
#

nm_props_file=${NODEMGR_HOME}/nodemanager.properties
nm_domains_file=${NODEMGR_HOME}/nodemanager.domains

cat <<EOF > ${nm_domains_file}
  ${domain_name}=${DOMAIN_HOME}
EOF

  [ ! $? -eq 0 ] && trace "Failed to create '${nm_domains_file}'." && exit 1

cat <<EOF > ${nm_props_file}
  #Node manager properties
  DomainsFile=${nm_domains_file}
  LogLimit=0
  DomainsDirRemoteSharingEnabled=true
  PropertiesVersion=12.2.1
  AuthenticationEnabled=false
  NodeManagerHome=${NODEMGR_HOME}
  JavaHome=${JAVA_HOME}
  LogLevel=FINEST
  DomainsFileEnabled=true
  ListenAddress=${SERVICE_NAME}
  NativeVersionEnabled=true
  ListenPort=5556
  LogToStderr=true
  weblogic.StartScriptName=startWebLogic.sh
  SecureListener=false
  LogCount=1
  QuitEnabled=false
  LogAppend=true
  weblogic.StopScriptEnabled=false
  StateCheckInterval=500
  CrashRecoveryEnabled=false
  weblogic.StartScriptEnabled=false
  LogFormatter=weblogic.nodemanager.server.LogFormatter
  ListenBacklog=50
  LogFile=${nodemgr_log_file}

EOF

  [ ! $? -eq 0 ] && trace "Failed to create '${nm_props_file}'." && exit 1

###############################################################################
#
#  If we're a WL Server pod, cleanup its old state file and 
#  create its NM startup.properties file.
#

if [ ! "${SERVER_NAME}" = "introspector" ]; then

  wl_data_dir=${DOMAIN_HOME}/servers/${SERVER_NAME}/data/nodemanager
  wl_state_file=${wl_data_dir}/${SERVER_NAME}.state
  wl_props_file=${wl_data_dir}/startup.properties

  createFolder ${wl_data_dir}

  # Remove state file, because:
  #   1 - The liveness probe checks this file
  #   2 - It might have a stale value
  #   3 - NM checks this file, and may auto-start the server if it's missing
  
  if [ -f "$wl_state_file" ]; then
    trace "Removing stale file '$wl_state_file'."
    rm -f ${wl_state_file} || exit 1
  fi


cat <<EOF > ${wl_props_file}
# Server startup properties
AutoRestart=true
RestartMax=2
RotateLogOnStartup=false
RotationType=bySize
RotationTimeStart=00\\:00
RotatedFileCount=100
RestartDelaySeconds=0
FileSizeKB=5000
FileTimeSpanFactor=3600000
RestartInterval=3600
NumberOfFilesLimited=true
FileTimeSpan=24
NMHostName=${SERVICE_NAME}
Arguments=${USER_MEM_ARGS} -XX\\:+UnlockExperimentalVMOptions -XX\\:+UseCGroupMemoryLimitForHeap ${serverOutOption} ${JAVA_OPTIONS}

EOF
 
  [ ! $? -eq 0 ] && trace "Failed to create '${wl_props_file}'." && exit 1

  if [ ! "${ADMIN_NAME}" = "${SERVER_NAME}" ]; then
    echo "AdminURL=http\\://${AS_SERVICE_NAME}\\:${ADMIN_PORT}" >> ${wl_props_file}
  fi
fi



###############################################################################
#
#  Set additional env vars required to start NM
#

#  Customized properties
export JAVA_PROPERTIES="-DLogFile=${nodemgr_log_file} -DNodeManagerHome=${NODEMGR_HOME}"

#  Copied from ${DOMAIN_HOME}/bin/setNMJavaHome.sh
#  (We assume a Oracle Sun Hotspot JVM since we're only using Linux VMs
#  and only support other JVM types on non-Linux OS (HP-UX, IBM AIX, IBM zLinux)).
export BEA_JAVA_HOME=""
export DEFAULT_BEA_JAVA_HOME=""
export SUN_JAVA_HOME="${JAVA_HOME?}"
export DEFAULT_SUN_JAVA_HOME="${JAVA_HOME?}"
export JAVA_VENDOR="Oracle"
export VM_TYPE="HotSpot"

#  Copied from ${DOMAIN_HOME}/bin/startNodeManager.sh 
export NODEMGR_HOME="${NODEMGR_HOME?}"
export DOMAIN_HOME="${DOMAIN_HOME?}"
export JAVA_OPTIONS="${JAVA_OPTIONS} -Dweblogic.RootDirectory=${DOMAIN_HOME}"

###############################################################################
#
#  Start the NM
#  1) remove old NM log file, if it exists
#  2) start NM in background
#  3) wait up to 15 seconds for NM by monitoring log file
#  4) 'exit 1' if wait more than 15 seconds
# 

trace "Start the nodemanager, node manager home is '${NODEMGR_HOME}', log file is '${nodemgr_log_file}', out file is '${nodemgr_out_file}'."

rm -f ${nodemgr_log_file} || exit 1
rm -f ${nodemgr_out_file} || exit 1

${stm_script} > ${nodemgr_out_file} 2>&1 &

wait_count=0
start_secs=$SECONDS
max_wait_secs=15
while [ 1 -eq 1 ]; do
  sleep 1
  if [ -e ${nodemgr_log_file} ] && [ `grep -c "Plain socket listener started" ${nodemgr_log_file}` -gt 0 ]; then
    break
  fi
  if [ $((SECONDS - $start_secs)) -ge $max_wait_secs ]; then
    trace "Info: Contents of node manager log '$nodemgr_log_file':"
    cat ${nodemgr_log_file}
    trace "Info: Contents of node manager out '$nodemgr_out_file':"
    cat ${NODEMGR_OUT_FILE}
    trace "Error: node manager failed to start within $max_wait_secs seconds."
    exit 1
  fi
  wait_count=$((wait_count + 1))
done

trace "Nodemanager started in $((SECONDS - start_secs)) seconds."
