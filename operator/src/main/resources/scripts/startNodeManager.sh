#!/bin/bash
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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
#   ORACLE_HOME       = Oracle Install Home - defaults via utils.sh/exportInstallHomes
#   MW_HOME           = MiddleWare Install Home - defaults to ${ORACLE_HOME}
#   WL_HOME           = WebLogic Install Home - defaults to ${ORACLE_HOME}/wlserver
#
#   NODEMGR_LOG_HOME  = Directory that will contain contain both
#                          ${DOMAIN_UID}/${SERVER_NAME}_nodemanager.log
#                          ${DOMAIN_UID}/${SERVER_NAME}_nodemanager.out
#                       Default:
#                          Use LOG_HOME.  If LOG_HOME not set, use NODEMGR_HOME.
#   NODEMGR_LOG_FILE_MAX = max NM .log and .out files to keep around (default=11)
#
#   ADMIN_PORT_SECURE = "true" if the admin protocol is secure. Default is false
#
#   FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR = "true" if WebLogic server should fail to 
#                       boot if situational configuration related errors are 
#                       found. Default to "true" if unspecified.
#
#   NODEMGR_MEM_ARGS  = JVM mem args for starting the Node Manager instance
#   NODEMGR_JAVA_OPTIONS  = Java options for starting the Node Manager instance
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

source ${SCRIPTPATH}/utils.sh 
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1 

# Set ORACLE_HOME/WL_HOME/MW_HOME to defaults if needed
exportInstallHomes

stm_script=${WL_HOME}/server/bin/startNodeManager.sh

SERVER_NAME=${SERVER_NAME:-introspector}
ADMIN_PORT_SECURE=${ADMIN_PORT_SECURE:-false}

trace "Starting node manager for domain-uid='$DOMAIN_UID' and server='$SERVER_NAME'."

checkEnv JAVA_HOME NODEMGR_HOME DOMAIN_HOME DOMAIN_UID ORACLE_HOME MW_HOME WL_HOME || exit 1

if [ ! -d "${JAVA_HOME}" ]; then
  trace SEVERE "JAVA_HOME directory not found '${JAVA_HOME}'." \
               "The Java install may be missing from the image or" \
               "JAVA_HOME is not set correctly."
  exit 1
fi

if [ ! -d "${WL_HOME}" ]; then
  trace SEVERE "WL_HOME '${WL_HOME}' not found." \
               "The WebLogic install may be missing from the image or" \
               "one of ORACLE_HOME, WL_HOME, or MW_HOME is not set correctly."
  exit 1
fi

if [ ! -f "${stm_script}" ]; then
  trace SEVERE "Missing script '${stm_script}' in WL_HOME '${WL_HOME}'." \
               "The WebLogic install may be missing from the image or" \
               "one of ORACLE_HOME, WL_HOME, or MW_HOME is not set correctly."
  exit 1
fi

if [ ! -d "${DOMAIN_HOME}" ]; then
  # MII is not specifically mentioned because MII checks for this problem earlier.
  trace SEVERE \
    "The directory '$DOMAIN_HOME' specified by 'domain.spec.domainHome' was not found." \
    "For Domain-in-Image and Domain-in-PV domains," \
    "this directory is expected to reference a fully configured WebLogic domain" \
    "that is set up before the domain resource is deployed."
  exit 1
fi

if [ ! -f "${DOMAIN_HOME}/config/config.xml" ]; then
  # MII is not specifically mentioned because MII checks for this problem earlier.
  trace SEVERE \
    "The directory '$DOMAIN_HOME' specified by 'domain.spec.domainHome' exists" \
    "but no 'config/config.xml' file was found within this directory." \
    "For Domain-in-Image and Domain-in-PV domains," \
    "this directory is expected to reference a fully configured WebLogic domain" \
    "that is set up before the domain resource is deployed."
  exit 1
fi

if [ "${SERVER_NAME}" = "introspector" ]; then
  SERVICE_NAME=localhost
  trace "Contents of '${DOMAIN_HOME}/config/config.xml':"
  cat ${DOMAIN_HOME}/config/config.xml
else
  checkEnv SERVER_NAME ADMIN_NAME AS_SERVICE_NAME SERVICE_NAME USER_MEM_ARGS || exit 1
fi

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
  serverLogHome="${LOG_HOME:-${DOMAIN_HOME}}"
  if [ -z ${LOG_HOME_LAYOUT} ] || [ "BY_SERVERS" = ${LOG_HOME_LAYOUT} ] ; then
    serverLogHome="${serverLogHome}/servers/${SERVER_NAME}/logs"
  fi
  export SERVER_OUT_FILE="${serverLogHome}/${SERVER_NAME}.out"
  export SERVER_PID_FILE="${serverLogHome}/${SERVER_NAME}.pid"
  export SHUTDOWN_MARKER_FILE="${serverLogHome}/${SERVER_NAME}.shutdown"
  serverOutOption="-Dweblogic.Stdout=${SERVER_OUT_FILE}"
  createFolder "${serverLogHome}" "This folder is used to hold server output for server '$SERVER_NAME'. If 'server.spec.logHomeEnabled' is set to true, then it is the 'domain.spec.logHome' directory, otherwise it is located within the 'domain.spec.domainHome' directory." || exit 1
  rm -f ${SHUTDOWN_MARKER_FILE}
fi


###############################################################################
#
# Init/create nodemanager home and nodemanager log env vars and directory
#

export NODEMGR_HOME=${NODEMGR_HOME}/${DOMAIN_UID}/${SERVER_NAME}

createFolder "${NODEMGR_HOME}" "This is the internal NODEMGR_HOME folder for the node manager for server '$SERVER_NAME' and domain UID '${DOMAIN_UID}'." || exit 1

NODEMGR_LOG_HOME=${NODEMGR_LOG_HOME:-${LOG_HOME:-${NODEMGR_HOME}/${DOMAIN_UID}}}
FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR=${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR:-true}

trace "NODEMGR_HOME='${NODEMGR_HOME}'"
trace "LOG_HOME='${LOG_HOME}'"
trace "SERVER_NAME='${SERVER_NAME}'"
trace "DOMAIN_UID='${DOMAIN_UID}'"
trace "NODEMGR_LOG_HOME='${NODEMGR_LOG_HOME}'"
trace "FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR='${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR}'"

createFolder "${NODEMGR_LOG_HOME}" "This directory is used to hold node manager logs for server '$SERVER_NAME'. If 'domain.spec.logHomeEnabled' is 'true', then it is located within the 'domain.spec.logHome' directory, otherwise it is located within within the NODEMGR_HOME '${NODEMGR_HOME}' directory." || exit 1

nodemgr_log_file=${NODEMGR_LOG_HOME}/${SERVER_NAME}_nodemanager.log
nodemgr_out_file=${NODEMGR_LOG_HOME}/${SERVER_NAME}_nodemanager.out
nodemgr_lck_file=${NODEMGR_LOG_HOME}/${SERVER_NAME}_nodemanager.log.lck

if [ -z ${LOG_HOME_LAYOUT} ] || [ "BY_SERVERS" = ${LOG_HOME_LAYOUT} ] ; then
  nodemgr_log_file=${NODEMGR_LOG_HOME}/servers/${SERVER_NAME}/logs/${SERVER_NAME}_nodemanager.log
  nodemgr_out_file=${NODEMGR_LOG_HOME}/servers/${SERVER_NAME}/logs//${SERVER_NAME}_nodemanager.out
  nodemgr_lck_file=${NODEMGR_LOG_HOME}/servers/${SERVER_NAME}/logs//${SERVER_NAME}_nodemanager.log.lck
fi


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
  trace SEVERE "Could not determine domain name"
  exit 1
fi


###############################################################################
#
# Create nodemanager.properties and nodemanager.domains files in NM home 
#

nm_domains_file=${NODEMGR_HOME}/nodemanager.domains
cat <<EOF > ${nm_domains_file}
  ${domain_name}=${DOMAIN_HOME}
EOF
[ ! $? -eq 0 ] && trace SEVERE "Failed to create '${nm_domains_file}'." && exit 1

nm_props_file=${NODEMGR_HOME}/nodemanager.properties

cat <<EOF > ${nm_props_file}
  #Node manager properties
  NodeManagerHome=${NODEMGR_HOME}
  JavaHome=${JAVA_HOME}
  DomainsFile=${nm_domains_file}
  DomainsFileEnabled=true
  DomainsDirRemoteSharingEnabled=true
  NativeVersionEnabled=true
  PropertiesVersion=12.2.1
  ListenAddress=127.0.0.1
  ListenPort=5556
  ListenBacklog=50
  AuthenticationEnabled=false
  SecureListener=false
  weblogic.StartScriptEnabled=true
  weblogic.StartScriptName=startWebLogic.sh
  weblogic.StopScriptEnabled=false
  QuitEnabled=false
  StateCheckInterval=500
  CrashRecoveryEnabled=false
  LogFile=${nodemgr_log_file}
  LogToStderr=true
  LogFormatter=weblogic.nodemanager.server.LogFormatter
  LogAppend=true
  LogLimit=0
  LogLevel=FINEST
  LogCount=1

EOF

[ ! $? -eq 0 ] && trace SEVERE "Failed to create '${nm_props_file}'." && exit 1

###############################################################################
#
#  If we're a WL Server pod, cleanup its old state file and 
#  create its NM startup.properties file.
#

if [ ! "${SERVER_NAME}" = "introspector" ]; then

  wl_data_dir=${DOMAIN_HOME}/servers/${SERVER_NAME}/data/nodemanager
  wl_state_file=${wl_data_dir}/${SERVER_NAME}.state
  wl_props_file=${wl_data_dir}/startup.properties

  createFolder "${wl_data_dir}" "This is a directory the server '$SERVER_NAME' node manager uses to track its state. It is located within the 'domain.spec.domainHome' directory." || exit 1

  # Remove state file, because:
  #   1 - The liveness probe checks this file
  #   2 - It might have a stale value
  #   3 - NM checks this file, and may auto-start the server if it's missing
  
  if [ -f "$wl_state_file" ]; then
    trace "Removing stale file '$wl_state_file'."
    rm -f ${wl_state_file} 
    [ ! $? -eq 0 ] && trace SEVERE "Could not remove stale file '$wl_state_file'." && exit 1
  fi

  if [ ${DOMAIN_SOURCE_TYPE} == "FromModel" ]; then
    # Domain source type is 'FromModel' (MII) then disable Situation config override for WebLogic.
    failBootOnErrorOption=""
  else
    failBootOnErrorOption="-Dweblogic.SituationalConfig.failBootOnError=${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR}"
  fi

cat <<EOF > ${wl_props_file}
# Server startup properties
AutoRestart=true
RestartMax=${RESTART_MAX:-2}
RestartInterval=${RESTART_INTERVAL:-3600}
NMHostName=${SERVICE_NAME}
Arguments=${USER_MEM_ARGS} ${failBootOnErrorOption} ${serverOutOption} ${JAVA_OPTIONS}

EOF
 
  [ ! $? -eq 0 ] && trace SEVERE "Failed to create '${wl_props_file}'." && exit 1

  if [ ! "${ADMIN_NAME}" = "${SERVER_NAME}" ]; then
    ADMIN_URL=$(getAdminServerUrl)
    echo "AdminURL=$ADMIN_URL" >> ${wl_props_file}
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

# Apply JAVA_OPTIONS to Node Manager if NODEMGR_JAVA_OPTIONS not specified
if [ -z ${NODEMGR_JAVA_OPTIONS} ]; then
  NODEMGR_JAVA_OPTIONS="${JAVA_OPTIONS}"
fi

if [ -z "${NODEMGR_MEM_ARGS}" ]; then
  # Default JVM memory arguments for Node Manager
  NODEMGR_MEM_ARGS="-Xms64m -Xmx100m -Djava.security.egd=file:/dev/./urandom "
fi

# We prevent USER_MEM_ARGS from being applied to the NM here and only pass
# USER_MEM_ARGS to WL Servers via the WL Server startup properties file above.
# This is so that WL Servers and NM can have different tuning. Use NODEMGR_MEM_ARGS or
# NODEMGR_JAVA_OPTIONS to specify JVM memory arguments for NMs.
# NOTE: Specifying USER_MEM_ARGS with ' ' (space, not empty string)
# prevents MEM_ARGS from being implicitly set by the WebLogic env
# scripts in the WebLogic installation and WLS from inserting default
# values for memory arguments. (See commBaseEnv.sh).
USER_MEM_ARGS=" "
export USER_MEM_ARGS

# NODEMGR_MEM_ARGS and NODEMGR_JAVA_OPTIONS are exported to Node Manager as JAVA_OPTIONS
# environment variable.
export JAVA_OPTIONS="${NODEMGR_MEM_ARGS} ${NODEMGR_JAVA_OPTIONS} -Dweblogic.RootDirectory=${DOMAIN_HOME}"

###############################################################################
#
#  Start the NM
#  1) rotate old NM log file, and old NM out file, if they exist
#  2) start NM in background
#  3) wait up to ${NODE_MANAGER_MAX_WAIT:-60} seconds for NM by monitoring NM's .out file
#  4) log SEVERE, log INFO with 'exit 1' if wait more than ${NODE_MANAGER_MAX_WAIT:-60} seconds
# 

trace "Start the nodemanager, node manager home is '${NODEMGR_HOME}', log file is '${nodemgr_log_file}', out file is '${nodemgr_out_file}'."

logFileRotate ${nodemgr_log_file} ${NODEMGR_LOG_FILE_MAX:-11}
logFileRotate ${nodemgr_out_file} ${NODEMGR_LOG_FILE_MAX:-11}

${stm_script} > ${nodemgr_out_file} 2>&1 &

start_secs=$SECONDS
max_wait_secs=${NODE_MANAGER_MAX_WAIT:-60}
while [ 1 -eq 1 ]; do
  sleep 1
  # Test if node manager listen port is reachable
  $(timeout 1 bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/5556' > /dev/null 2>&1)
  res="$?"
  if [ $res -eq 0 ]; then
    break
  fi
  if [ $((SECONDS - $start_secs)) -ge $max_wait_secs ]; then
    pid=$(jps | grep NodeManager | awk '{ print $1 }')
    if [ -z $pid ]; then
      trace INFO "Node manager process id not found. Cannot create thread dump."
    else
      trace INFO "Node manager process id is '$pid'."
      trace INFO "Trying to put a node manager thread dump in '$nodemgr_out_file'."
      kill -3 $pid
      if [ -x "$(command -v $JAVA_HOME/bin/jcmd)" ]; then
        trace INFO "Node manager thread dump:"
        $JAVA_HOME/bin/jcmd $pid Thread.print
      fi
    fi
    trace INFO "Entropy: "
    cat /proc/sys/kernel/random/entropy_avail
    trace INFO "Contents of node manager log '$nodemgr_log_file':"
    cat ${nodemgr_log_file}
    trace INFO "Contents of node manager out '$nodemgr_out_file':"
    cat ${NODEMGR_OUT_FILE}

    trace SEVERE $(cat << EOF
The node manager failed to start within $max_wait_secs seconds.
To increase this timeout, define the NODE_MANAGER_MAX_WAIT
environment variable in your domain resource, and set it higher
than $max_wait_secs. To diagnose the problem, see the above INFO
messages for node manager log contents, stdout contents, pid,
thread dump, and entropy. If the log and stdout contents are
sparse and reveal no errors, then the node manager may be stalled
while generating entropy -- especially if entropy is below 500.
If entropy is the problem, then for testing purposes you can
temporarily work around this problem by specifying
'-Djava.security.egd=file:/dev/./urandom' in a USER_MEM_ARGS
environment variable defined via your domain resource, but
for production purposes the problem should be solved by following
the guidance in
'https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/nodem/starting_nodemgr.html#GUID-53961E3A-D8E1-4556-B78A-9A56B676D57E'
(search for keyword 'rngd').
EOF
)
    exit 1
  fi
done

trace "Nodemanager started in $((SECONDS - start_secs)) seconds."
