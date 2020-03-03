#!/bin/bash

# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
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
  # Verify that the domain secret hasn't changed
  #

  checkDomainSecretMD5 || exitOrLoop

  #
  # Start WL Server
  #

  # TBD We should probably || exitOrLoop if start-server.py itself fails, and dump NM log to stdout

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

# trace env vars and dirs before export.*Home calls

traceEnv before
traceDirs before

#
# Configure startup mode
#

if [ ! -z "$STARTUP_MODE" ] && [[ $JAVA_OPTIONS != *"-Dweblogic.management.startupMode="* ]]; then
  export JAVA_OPTIONS="$JAVA_OPTIONS -Dweblogic.management.startupMode=$STARTUP_MODE"
fi

#
# Check input env vars
#

checkEnv -q \
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

# If DATA_HOME env variable exists than this implies override directory (dataHome attribute of CRD) specified
# so we need to try and link the server's 'data' directory to the centralized DATA_HOME directory
if [ ! -z ${DATA_HOME} ]; then
  # Create $DATA_HOME directory for server if doesn't exist
  if [ ! -d ${DATA_HOME}/${SERVER_NAME}/data ]; then
    trace "Creating directory '${DATA_HOME}/${SERVER_NAME}/data'"
    createFolder ${DATA_HOME}/${SERVER_NAME}/data
  else
    trace "Directory '${DATA_HOME}/${SERVER_NAME}/data' exists"
  fi

  # The following is experimental code that handles the specific case of services that don't provide a configurable way to
  # control the location of their persistent file data.  For example, web applications can configure file-based
  # session persistence where the default persistent file store location is automatically created in the
  # <server-name>\data\store\default directory.
  # If 'EXPERIMENTAL_LINK_SERVER_DEFAULT_DATA_DIR' env is defined and 'KEEP_DEFAULT_DATA_HOME' environment variable is not defined then
  # try to link server's default 'data' directory (${DOMAIN_HOME}/servers/${SERVER_NAME}/data) to $DATA_HOME/${SERVER_NAME}/data.
  # If 'EXPERIMENTAL_LINK_SERVER_DEFAULT_DATA_DIR' env is defined and 'KEEP_DEFAULT_DATA_HOME' env variable is defined then
  # we will NOT link the server's 'data' directory to the centralized DATA_HOME directory and instead keep the server's
  # 'data' directory in its default location of ${DOMAIN_HOME}/servers/${SERVER_NAME}/data
  if [ ! -z ${EXPERIMENTAL_LINK_SERVER_DEFAULT_DATA_DIR} ] && [ -z ${KEEP_DEFAULT_DATA_HOME} ]; then
    linkServerDefaultDir
  fi
fi

#
# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
#

exportEffectiveDomainHome || exitOrLoop

# trace env vars and dirs after export.*Home calls

traceEnv after
traceDirs after

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
