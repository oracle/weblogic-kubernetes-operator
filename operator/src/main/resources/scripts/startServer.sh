#!/bin/bash

# Copyright (c) 2017, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# startServer.sh
# This is the script WebLogic Operator WLS Pods use to start their WL Server.
#

if [ -z ${SCRIPTPATH+x} ]; then
  SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
fi

echo "script path is ${SCRIPTPATH}"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exitOrLoop

traceTiming "POD '${SERVICE_NAME}' MAIN START"

trace "Starting WebLogic Server '${SERVER_NAME}'."

source ${SCRIPTPATH}/modelInImage.sh

if [ $? -ne 0 ]; then
      trace SEVERE "Error sourcing modelInImage.sh" && exit 1
fi

exportInstallHomes

#
# if the auxiliary image feature is active, verify the mount, and log mount information
#
checkAuxiliaryImage || exitOrLoop

#
# Define function to start WebLogic
#

startWLS() {
  #
  # Start NM
  #

  traceTiming "POD '${SERVICE_NAME}' NM START"

  trace "Start node manager"
  # call script to start node manager in same shell
  # $SERVER_OUT_FILE, SERVER_PID_FILE, and SHUTDOWN_MARKER_FILE will be set in startNodeManager.sh
  . ${SCRIPTPATH}/startNodeManager.sh
  [ $? -ne 0 ] && trace SEVERE "failed to start node manager" && exitOrLoop

  traceTiming "POD '${SERVICE_NAME}' NM RUNNING"

  #
  # Verify that the domain secret hasn't changed
  #

  traceTiming "POD '${SERVICE_NAME}' MD5 BEGIN"

  checkDomainSecretMD5 || exitOrLoop

  traceTiming "POD '${SERVICE_NAME}' MD5 END"

  #
  # We "tail" the future WL Server .out file to stdout in background _before_ starting 
  # the WLS Server because we use WLST 'nmStart()' to start the server and nmStart doesn't return
  # control until WLS reaches the RUNNING state.
  #

  if [ "${SERVER_OUT_IN_POD_LOG}" == 'true' ] ; then
    trace "Showing the server out file from ${SERVER_OUT_FILE}"
    ${SCRIPTPATH}/tailLog.sh ${SERVER_OUT_FILE} ${SERVER_PID_FILE} &
  fi

  #
  # Start WL Server
  #

  # TBD We should probably || exitOrLoop if start-server.py itself fails, and dump NM log to stdout

  traceTiming "POD '${SERVICE_NAME}' WLS STARTING"

  trace "Start WebLogic Server via the nodemanager"
  ${SCRIPTPATH}/wlst.sh $SCRIPTPATH/start-server.py

  traceTiming "POD '${SERVICE_NAME}' WLS STARTED"

  FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR=${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR:-true}
  SERVER_OUT_MONITOR_INTERVAL=${SERVER_OUT_MONITOR_INTERVAL:-3}
  if [ ${DOMAIN_SOURCE_TYPE} != "FromModel" ]; then
    # If Domain source type is not FromModel (MII) then monitor server logs for invalid
    # Situational config override files.
    if [ ${FAIL_BOOT_ON_SITUATIONAL_CONFIG_ERROR} == 'true' ] ; then
      ${SCRIPTPATH}/monitorLog.sh ${SERVER_OUT_FILE} ${SERVER_OUT_MONITOR_INTERVAL} &
    fi
  fi
}

mockWLS() {

  trace "Mocking WebLogic Server"

  STATEFILE_DIR=${DOMAIN_HOME}/servers/${SERVER_NAME}/data/nodemanager
  STATEFILE=${STATEFILE_DIR}/${SERVER_NAME}.state

  createFolder "$STATEFILE_DIR" "This is the directory for holding '${SERVER_NAME}.state' in mock mode for 'server '${SERVER_NAME}' within the 'domain.spec.domainHome' directory." || exitOrLoop

  echo "RUNNING:Y:N" > $STATEFILE
}

# Define helper fn to copy sit cfg xml files from one dir to another
#   $src_dir files are assumed to start with $fil_prefix and end with .xml
#   Copied $tgt_dir files are stripped of their $fil_prefix
#   Any .xml files in $tgt_dir that are not in $src_dir/$fil_prefix+FILE are deleted
#
# This method is called during boot, see 'copySitCfgWhileRunning' in 'livenessProbe.sh'
# for the similar method that is periodically called while the server is running.

copySitCfgWhileBooting() {
  # Helper fn to copy sit cfg xml files to the WL server's domain home.
  #   - params $1/$2/$3 == 'src_dir tgt_dir fil_prefix'
  #   - $src_dir files are assumed to start with $fil_prefix and end with .xml
  #   - copied $tgt_dir files are stripped of their $fil_prefix
  #   - any .xml files in $tgt_dir that are not in $src_dir/$fil_prefix+FILE are deleted
  #
  # This method is called before the server boots, see
  # 'copySitCfgWhileRunning' in 'livenessProbe.sh' for a similar method that
  # is called periodically while the server is running. 

  src_dir=${1?}
  tgt_dir=${2?}
  fil_prefix=${3?}

  trace "Copying files starting with '$src_dir/$fil_prefix' to '$tgt_dir' without the prefix."

  createFolder "$tgt_dir" "This is a directory within directory 'domain.spec.domainHome' that the operator uses for supplying internal or user specified configuration overrides." || exitOrLoop

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
traceDirs before DOMAIN_HOME LOG_HOME DATA_HOME

traceTiming "POD '${SERVICE_NAME}' MII UNZIP START"

if [ ${DOMAIN_SOURCE_TYPE} == "FromModel" ]; then
  prepareMIIServer
  if [ $? -ne 0 ] ; then
    trace SEVERE  "Domain Source Type is FromModel, unable to start the server, check other error messages in the log"
    exitOrLoop
  fi

fi

traceTiming "POD '${SERVICE_NAME}' MII UNZIP COMPLETE"

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
    createFolder "${DATA_HOME}/${SERVER_NAME}/data" "This is the server '$SERVER_NAME' data directory within directory DATA_HOME 'domain.spec.dataHome'." || exitOrLoop
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
traceDirs after DOMAIN_HOME LOG_HOME DATA_HOME

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

createFolder "${DOMAIN_HOME}/servers/${SERVER_NAME}/security" "This is the server '${SERVER_NAME}' security directory within directory DOMAIN_HOME 'domain.spec.domainHome'." || exitOrLoop

copyIfChanged /weblogic-operator/introspector/boot.properties \
              ${DOMAIN_HOME}/servers/${SERVER_NAME}/security/boot.properties

# remove write and execute permissions for group to prevent insecure file system warnings.
chmod g-wx  ${DOMAIN_HOME}/servers/${SERVER_NAME}/security/boot.properties


if [ ${DOMAIN_SOURCE_TYPE} != "FromModel" ]; then
  trace "Copying situational configuration files from operator cm to ${DOMAIN_HOME}/optconfig directory"
  copySitCfgWhileBooting /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig             'Sit-Cfg-CFG--'
  copySitCfgWhileBooting /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jms         'Sit-Cfg-JMS--'
  copySitCfgWhileBooting /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jdbc        'Sit-Cfg-JDBC--'
  copySitCfgWhileBooting /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/diagnostics 'Sit-Cfg-WLDF--'
fi

if [[ "${KUBERNETES_PLATFORM^^}" == "OPENSHIFT" ]]; then
    # When the Operator is running on Openshift platform, disable insecure file system warnings.
    export JAVA_OPTIONS="-Dweblogic.SecureMode.WarnOnInsecureFileSystem=false $JAVA_OPTIONS"
    if [[ "${DOMAIN_SOURCE_TYPE}" == "Image" ]]; then
      # Change the file permissions in the DOMAIN_HOME dir to give group same permissions as user .
      chmod -R g=u ${DOMAIN_HOME} || return 1
    fi

fi
export JAVA_OPTIONS="${JAVA_XX_OPTIONS:--XX:+CrashOnOutOfMemoryError} $JAVA_OPTIONS"

#
# Start WLS
#

if [ "${MOCK_WLS}" == 'true' ]; then
  mockWLS
else
  startWLS
fi

#
# Wait forever. Kubernetes will monitor this pod via liveness and readyness probes.
#

waitForShutdownMarker
