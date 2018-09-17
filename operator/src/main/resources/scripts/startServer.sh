#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

#
# startServer.sh
# This is the script WebLogic Operator WLS Pods use to start their WL Server.
#

#
# Helper fn for trace output
# Date reported in same format as operator-log.  01-22-2018T21:49:01
# Args $* - Information to echo
#
function trace {
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] [WL Start Script]: ""$@"
}

#
# Helper fn to create a folder
# Arg $1 - path of folder to create
#
function createFolder {
  mkdir -m 777 -p $1
  if [ ! -d $1 ]; then
    trace "Unable to create folder $1"
    exit 1
  fi
}

domain_uid=${DOMAIN_UID?}
server_name=${SERVER_NAME?}
domain_name=${DOMAIN_NAME?}
admin_name=${ADMIN_NAME?}
admin_port=${ADMIN_PORT?}
domain_home=${DOMAIN_HOME?}
log_home=${LOG_HOME?}
nodemgr_home=${NODEMGR_HOME?}
service_name=${SERVICE_NAME?}
admin_hostname=${AS_SERVICE_NAME?}
user_mem_args=${USER_MEM_ARGS}
java_options=${JAVA_OPTIONS}

trace "Starting WebLogic Server '${server_name}'."

for varname in domain_uid \
               domain_name \
               domain_home \
               admin_name \
               admin_port \
               admin_hostname \
               server_name \
               service_name \
               log_home \
               nodemgr_home \
               user_mem_args \
               java_options \
               ;
do
  trace "  Input $varname='${!varname}'"
done
trace ""


wlDataDir=${domain_home}/servers/${server_name}/data/nodemanager
wlStateFile=${wlDataDir}/${server_name}.state
wlStartPropFile=${wlDataDir}/startup.properties
nmLogFile="${log_home}/nodemanager-${server_name}.log"
nmPropFile=${nodemgr_home}/nodemanager.properties

# Check for stale state file and remove if found
# (The liveness probe checks this file)

if [ -f "$wlStateFile" ]; then
  trace "Removing stale file $wlStateFile"
  rm ${wlStateFile}
fi

# Create nodemanager home directory that is local to the k8s node

createFolder ${nodemgr_home}
cp ${domain_home}/nodemanager/* ${nodemgr_home}
cp ${domain_home}/bin/startNodeManager.sh ${nodemgr_home}

# Edit the start nodemanager script to use the home for the server

sed -i -e "s:${domain_home}/nodemanager:${nodemgr_home}:g" ${nodemgr_home}/startNodeManager.sh

# Edit the nodemanager properties file to use the home for the server

sed -i -e "s:DomainsFile=.*:DomainsFile=${nodemgr_home}/nodemanager.domains:g" ${nmPropFile}
sed -i -e "s:NodeManagerHome=.*:NodeManagerHome=${nodemgr_home}:g" ${nmPropFile}
sed -i -e "s:ListenAddress=.*:ListenAddress=$service_name:g" ${nmPropFile}
sed -i -e "s:LogFile=.*:LogFile=${nmLogFile}:g" ${nmPropFile}

# Init JAVA_PROPERTIES used by startNodeManager script

export JAVA_PROPERTIES="-DLogFile=${nmLogFile} -DNodeManagerHome=${nodemgr_home}"

# Create the startup.properties used when WebLogic Server is started

trace "Create startup.properties"
createFolder ${wlDataDir}
echo "# Server startup properties" > ${wlStartPropFile}
echo "AutoRestart=true" >> ${wlStartPropFile}
if [ ! "$admin_name" = "$server_name" ]; then
  echo "AdminURL=http\://${admin_hostname}\:${admin_port}" >> ${wlStartPropFile}
fi
echo "RestartMax=2" >> ${wlStartPropFile}
echo "RotateLogOnStartup=false" >> ${wlStartPropFile}
echo "RotationType=bySize" >> ${wlStartPropFile}
echo "RotationTimeStart=00\:00" >> ${wlStartPropFile}
echo "RotatedFileCount=100" >> ${wlStartPropFile}
echo "RestartDelaySeconds=0" >> ${wlStartPropFile}
echo "FileSizeKB=5000" >> ${wlStartPropFile}
echo "FileTimeSpanFactor=3600000" >> ${wlStartPropFile}
echo "RestartInterval=3600" >> ${wlStartPropFile}
echo "NumberOfFilesLimited=true" >> ${wlStartPropFile}
echo "FileTimeSpan=24" >> ${wlStartPropFile}
echo "NMHostName=${service_name}" >> ${wlStartPropFile}
trace "Update JVM arguments"
echo "Arguments=${user_mem_args} -XX\:+UnlockExperimentalVMOptions -XX\:+UseCGroupMemoryLimitForHeap ${java_options}" >> ${wlStartPropFile}

# Start the nodemanager and wait until it's ready

trace "Start the nodemanager and wait for it to initialize"
rm -f ${nmLogFile}
. ${nodemgr_home}/startNodeManager.sh &

wait_count=0
while [ $wait_count -lt 15 ]; do
  sleep 1
  if [ -e ${nmLogFile} ] && [ `grep -c "Plain socket listener started" ${nmLogFile}` -gt 0 ]; then
    break
  fi
  wait_count=$((wait_count + 1))
done
trace "Finished waiting for the nodemanager to start"

# Start the server

trace "Start the WebLogic Server via the nodemanager"
wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/start-server.py

trace "Wait indefinitely so that the Kubernetes pod does not exit and try to restart"
while true; do sleep 60; done

