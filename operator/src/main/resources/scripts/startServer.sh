#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

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

echo "Starting WebLogic Server '${server_name}'."

echo "Inputs: "
for varname in domain_uid \
               server_name \
               admin_name \
               admin_port \
               domain_name \
               domain_home \
               log_home \
               nodemgr_home \
               service_name \
               admion_hostname \
               ;
do
  echo -n " $varname=${!varname}"
done
echo ""

#
# Create a folder
# $1 - path of folder to create
function createFolder {
  mkdir -m 777 -p $1
  if [ ! -d $1 ]; then
    echo "Unable to create folder $1"
    exit 1
  fi
}

# Function to create server specific scripts and properties (e.g startup.properties, etc)
# $1 - Domain UID
# $2 - Server Name
# $3 - Domain Home
# $4 - Admin Server Hostname (only passed for managed servers)
# $5 - Admin Server port (only passed for managed servers)
function createServerScriptsProperties() {

  # Create nodemanager home for the server
  createFolder ${nodemgr_home}
  cp $3/nodemanager/nodemanager.domains ${nodemgr_home}
  cp $3/bin/startNodeManager.sh ${nodemgr_home}

  # Edit the start nodemanager script to use the home for the server
  sed -i -e "s:$3/nodemanager:${nodemgr_home}:g" ${nodemgr_home}/startNodeManager.sh

  # Create startup.properties file
  datadir=$3/servers/$2/data/nodemanager
  stateFile=${datadir}/$2.state
  startProp=${datadir}/startup.properties
  if [ -f "$startProp" ]; then
    echo "startup.properties already exists"
    return 0
  fi

  createFolder ${datadir}
  echo "# Server startup properties" > ${startProp}
  echo "AutoRestart=true" >> ${startProp}
  if [ -n "$4" ]; then
    echo "AdminURL=http\://$4\:$5" >> ${startProp}
  fi
  echo "RestartMax=2" >> ${startProp}
  echo "RotateLogOnStartup=false" >> ${startProp}
  echo "RotationType=bySize" >> ${startProp}
  echo "RotationTimeStart=00\:00" >> ${startProp}
  echo "RotatedFileCount=100" >> ${startProp}
  echo "RestartDelaySeconds=0" >> ${startProp}
  echo "FileSizeKB=5000" >> ${startProp}
  echo "FileTimeSpanFactor=3600000" >> ${startProp}
  echo "RestartInterval=3600" >> ${startProp}
  echo "NumberOfFilesLimited=true" >> ${startProp}
  echo "FileTimeSpan=24" >> ${startProp}
  echo "NMHostName=$service_name" >> ${startProp}
}

echo "debug arguments are $1 $2 $3 $4 $5 $admin_hostname $service_name"

# Check for stale state file and remove if found"
if [ -f "$stateFile" ]; then
  echo "Removing stale file $stateFile"
  rm ${stateFile}
fi

# Create nodemanager home directory that is local to the k8s node
mkdir -p ${nodemgr_home}
cp ${domain_home}/nodemanager/* ${nodemgr_home}

nm_log="${log_home}/nodemanager-${server_name}.log"

# Edit the nodemanager properties file to use the home for the server
sed -i -e "s:DomainsFile=.*:DomainsFile=${nodemgr_home}/nodemanager.domains:g" ${nodemgr_home}/nodemanager.properties
sed -i -e "s:NodeManagerHome=.*:NodeManagerHome=${nodemgr_home}:g" ${nodemgr_home}/nodemanager.properties
sed -i -e "s:ListenAddress=.*:ListenAddress=$service_name:g" /u01/nodemanager/nodemanager.properties
sed -i -e "s:LogFile=.*:LogFile=${nm_log}:g" ${nodemgr_home}/nodemanager.properties

export JAVA_PROPERTIES="-DLogFile=${nm_log} -DNodeManagerHome=${nodemgr_home}"

# Create startup.properties
echo "Create startup.properties"
if [ ! "$admin_name" = "$server_name" ]; then
  echo "this is managed server"
  createServerScriptsProperties $domain_uid $server_name $domain_home $admin_hostname $admin_port
else
  echo "this is admin server"
  createServerScriptsProperties $domain_uid $server_name $domain_home
fi

echo "Start the nodemanager"
rm -f ${nm_log}
. ${nodemgr_home}/startNodeManager.sh &

echo "Allow the nodemanager some time to start before attempting to connect"
wait_count=0
while [ $wait_count -lt 15 ]; do
  sleep 1
  if [ -e ${nm_log} ] && [ `grep -c "Plain socket listener started" ${nm_log}` -gt 0 ]; then
    break
  fi
  wait_count=$((wait_count + 1))
done
echo "Finished waiting for the nodemanager to start"

echo "Update JVM arguments"
echo "Arguments=${USER_MEM_ARGS} -XX\:+UnlockExperimentalVMOptions -XX\:+UseCGroupMemoryLimitForHeap ${JAVA_OPTIONS}" >> ${startProp}

echo "Start the server"
wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/start-server.py

echo "Wait indefinitely so that the Kubernetes pod does not exit and try to restart"
while true; do sleep 60; done

