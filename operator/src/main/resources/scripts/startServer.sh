#!/bin/bash

domain_uid=$1
server_name=$2
domain_name=$3
as_name=$4
as_port=$5
as_hostname=$1-$4

echo "debug arguments are $1 $2 $3 $4 $5"

nmProp="/u01/nodemanager/nodemanager.properties"

# TODO: parameterize shared home and domain name
export DOMAIN_HOME=/shared/domain/$domain_name

#
# Create a folder
# $1 - path of folder to create
function createFolder {
  mkdir -m 777 -p $1
  if [ ! -d $1 ]; then
    fail "Unable to create folder $1"
  fi
}

# Function to create server specific scripts and properties (e.g startup.properties, etc)
# $1 - Domain UID
# $2 - Server Name
# $3 - Domain Name
# $4 - Admin Server Hostname (only passed for managed servers)
# $5 - Admin Server port (only passed for managed servers)
function createServerScriptsProperties() {

  # Create nodemanager home for the server
  srvr_nmdir=/u01/nodemanager
  createFolder ${srvr_nmdir}
  cp /shared/domain/$3/nodemanager/nodemanager.domains ${srvr_nmdir}
  cp /shared/domain/$3/bin/startNodeManager.sh ${srvr_nmdir}

  # Edit the start nodemanager script to use the home for the server
  sed -i -e "s:/shared/domain/$3/nodemanager:/u01/nodemanager:g" ${srvr_nmdir}/startNodeManager.sh

  # Create startup.properties file
  datadir=${DOMAIN_HOME}/servers/$2/data/nodemanager
  nmdir=${DOMAIN_HOME}/nodemgr_home
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
  echo "NMHostName=$1-$2" >> ${startProp}
}

# Check for stale state file and remove if found"
if [ -f "$stateFile" ]; then
  echo "Removing stale file $stateFile"
  rm ${stateFile}
fi

# Create nodemanager home directory that is local to the k8s node
mkdir -p /u01/nodemanager
cp ${DOMAIN_HOME}/nodemanager/* /u01/nodemanager/

# Edit the nodemanager properties file to use the home for the server
sed -i -e "s:DomainsFile=.*:DomainsFile=/u01/nodemanager/nodemanager.domains:g" /u01/nodemanager/nodemanager.properties
sed -i -e "s:NodeManagerHome=.*:NodeManagerHome=/u01/nodemanager:g" /u01/nodemanager/nodemanager.properties
sed -i -e "s:ListenAddress=.*:ListenAddress=$1-$2:g" /u01/nodemanager/nodemanager.properties
sed -i -e "s:LogFile=.*:LogFile=/shared/logs/nodemanager-$2.log:g" /u01/nodemanager/nodemanager.properties

export JAVA_PROPERTIES="-DLogFile=/shared/logs/nodemanager-$server_name.log -DNodeManagerHome=/u01/nodemanager"
export NODEMGR_HOME="/u01/nodemanager"


# Create startup.properties
echo "Create startup.properties"
if [ -n "$4" ]; then
  echo "this is managed server"
  createServerScriptsProperties $domain_uid $server_name $domain_name $as_hostname $as_port
else
  echo "this is admin server"
  createServerScriptsProperties $domain_uid $server_name $domain_name
fi

echo "Start the nodemanager"
. ${NODEMGR_HOME}/startNodeManager.sh &

echo "Allow the nodemanager some time to start before attempting to connect"
sleep 15
echo "Finished waiting for the nodemanager to start"

echo "Update JVM arguments"
echo "Arguments=${USER_MEM_ARGS} -XX\:+UnlockExperimentalVMOptions -XX\:+UseCGroupMemoryLimitForHeap ${JAVA_OPTIONS}" >> ${startProp}

admin_server_t3_url=
if [ -n "$4" ]; then
  admin_server_t3_url=t3://$domain_uid-$as_name:$as_port
fi

echo "Start the server"
wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/start-server.py $domain_uid $server_name $domain_name $admin_server_t3_url

echo "Wait indefinitely so that the Kubernetes pod does not exit and try to restart"
while true; do sleep 60; done

