#!/bin/bash

# Copyright (c) 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# startServer.sh
# This is the script WebLogic Operator WLS Pods use to start their WL Server.
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exitOrLoop

trace "Starting WebLogic Server '${SERVER_NAME}'."

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

#
# Define helper fn to copy a file only if src & tgt differ
#

function copyIfChanged() {
  [ ! -f "${1?}" ] && trace SEVERE "File '$1' not found." && exit 1
  if [ ! -f "${2?}" ] || [ ! -z "`diff $1 $2 2>&1`" ]; then
    trace "Copying '$1' to '$2'."
    cp $1 $2
    [ $? -ne 0 ] && trace SEVERE "failed cp $1 $2" && exitOrLoop
    chmod 750 $2 
    [ $? -ne 0 ] && trace SEVERE "failed chmod 750 $2" && exitOrLoop
  else
    trace "Skipping copy of '$1' to '$2' -- these files already match."
  fi
}

#
# Define function to start weblogic
#

function startWLS() {
  #
  # Start NM
  #

  trace "Start node manager"
  # call script to start node manager in same shell
  # $SERVER_OUT_FILE, SERVER_PID_FILE, and SHUTDOWN_MARKER_FILE will be set in startNodeManager.sh
  . ${SCRIPTPATH}/startNodeManager.sh
  [ $? -ne 0 ] && trace SEVERE "failed to start node manager" && exitOrLoop

  #
  # Start WL Server
  #

  # TBD We should probably || exit 1 if start-server.py itself fails, and dump NM log to stdout

  trace "Start WebLogic Server via the nodemanager"
  ${SCRIPTPATH}/wlst.sh $SCRIPTPATH/start-server.py
}

function mockWLS() {

  trace "Mocking WebLogic Server"

  STATEFILE_DIR=${DOMAIN_HOME}/servers/${SERVER_NAME}/data/nodemanager
  STATEFILE=${STATEFILE_DIR}/${SERVER_NAME}.state

  createFolder $STATEFILE_DIR
  echo "RUNNING:Y:N" > $STATEFILE
}

function waitUntilShutdown() {
  #
  # Wait forever.   Kubernetes will monitor this pod via liveness and readyness probes.
  #
  if [ "${SERVER_OUT_IN_POD_LOG}" == 'true' ] ; then
    trace "Showing the server out file from ${SERVER_OUT_FILE}"
    ${SCRIPTPATH}/tailLog.sh ${SERVER_OUT_FILE} ${SERVER_PID_FILE} &
  fi
  FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR=${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR:-true} 
  SERVER_OUT_MONITOR_INTERVAL=${SERVER_OUT_MONITOR_INTERVAL:-3}
  if [ ${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR} == 'true' ] ; then
    ${SCRIPTPATH}/monitorLog.sh ${SERVER_OUT_FILE} ${SERVER_OUT_MONITOR_INTERVAL} &
  fi
  waitForShutdownMarker
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

# Define helper fn to copy sit cfg xml files from one dir to another
#   $src_dir files are assumed to start with $fil_prefix and end with .xml
#   Copied $tgt_dir files are stripped of their $fil_prefix
#   Any .xml files in $tgt_dir that are not in $src_dir/$fil_prefix+FILE are deleted
#

function copySitCfg() {
  src_dir=${1?}
  tgt_dir=${2?}
  fil_prefix=${3?}

  trace "Copying files starting with '$src_dir/$fil_prefix' to '$tgt_dir' without the prefix."

  createFolder $tgt_dir

  ls ${src_dir}/${fil_prefix}*.xml > /dev/null 2>&1
  if [ $? = 0 ]; then
    for local_fname in ${src_dir}/${fil_prefix}*.xml ; do
      copyIfChanged $local_fname $tgt_dir/`basename ${local_fname/${fil_prefix}//}`
      trace "Printing contents of situational configuration file $local_fname:"
      cat $local_fname
    done
  fi

  ls ${tgt_dir}/*.xml 2>&1 > /dev/null 2>&1
  if [ $? = 0 ]; then
    for local_fname in ${tgt_dir}/*.xml ; do
      if [ ! -f "$src_dir/${fil_prefix}`basename ${local_fname}`" ]; then
        trace "Deleting '$local_fname' since it has no corresponding '$src_dir' file."
        rm -f $local_fname
        [ $? -ne 0 ] && trace SEVERE "failed rm -f $local_fname" && exitOrLoop
      fi
    done
  fi
}

#
# Configure startup mode
#

if [ ! -z "$STARTUP_MODE" ] && [[ $JAVA_OPTIONS != *"-Dweblogic.management.startupMode="* ]]; then
  export JAVA_OPTIONS="$JAVA_OPTIONS -Dweblogic.management.startupMode=$STARTUP_MODE"
fi

#
# Check and display input env vars
#

checkEnv \
  DOMAIN_UID \
  DOMAIN_NAME \
  DOMAIN_HOME \
  NODEMGR_HOME \
  ORACLE_HOME \
  SERVER_NAME \
  SERVICE_NAME \
  ADMIN_NAME \
  ADMIN_PORT \
  SERVER_OUT_IN_POD_LOG \
  AS_SERVICE_NAME || exitOrLoop

trace "LOG_HOME=${LOG_HOME}"
trace "SERVER_OUT_IN_POD_LOG=${SERVER_OUT_IN_POD_LOG}"
trace "USER_MEM_ARGS=${USER_MEM_ARGS}"
trace "JAVA_OPTIONS=${JAVA_OPTIONS}"

#
# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
#

exportEffectiveDomainHome || exitOrLoop

#
# Check if introspector actually ran.  This should never fail since
# the operator shouldn't try run a wl pod if the introspector failed.
#

bootpfile="/weblogic-operator/introspector/boot.properties"
if [ ! -f ${bootpfile} ]; then
  trace SEVERE "Missing introspector file '${bootpfile}'.  Introspector failed to run."
  exitOrLoop
fi

#
# Check if we're using a supported WebLogic version. The check  will
# log a message if it fails.
#

if ! checkWebLogicVersion ; then
  exitOrLoop
fi

#
# Copy/update domain introspector files for the domain
#
# - Copy introspector boot.properties to '${DOMAIN_HOME}/servers/${SERVER_NAME}/security
# 
# - Copy introspector situational config files to '${DOMAIN_HOME}/optconfig
#
# - Note:  We don't update a file when it's unchanged because a new timestamp might
#          trigger unnecessary situational config overhead.
#

createFolder ${DOMAIN_HOME}/servers/${SERVER_NAME}/security
copyIfChanged /weblogic-operator/introspector/boot.properties \
              ${DOMAIN_HOME}/servers/${SERVER_NAME}/security/boot.properties

copySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig             'Sit-Cfg-CFG--'
copySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jms         'Sit-Cfg-JMS--'
copySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jdbc        'Sit-Cfg-JDBC--'
copySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/diagnostics 'Sit-Cfg-WLDF--'



if [ "${MOCK_WLS}" == 'true' ]; then
  mockWLS
  waitForShutdownMarker
else
  startWLS
  waitUntilShutdown
fi
