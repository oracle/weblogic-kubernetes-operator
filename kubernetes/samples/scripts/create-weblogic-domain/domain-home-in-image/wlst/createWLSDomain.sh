#!/bin/bash
#
#Copyright (c) 2021, Oracle and/or its affiliates.
#Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
#Adopted from https://github.com/oracle/docker-images/blob/main/OracleWebLogic/samples/12213-domain-home-in-image/container-scripts/createWLSDomain.sh
#Define DOMAIN_HOME
echo "Domain Home is: " $DOMAIN_HOME

# If AdminServer.log does not exists, container is starting for 1st time
# So it should start NM and also associate with AdminServer
# Otherwise, only start NM (container restarted)
########### SIGTERM handler ############
function _term() {
   echo "Stopping container."
   echo "SIGTERM received, shutting down the server!"
   ${DOMAIN_HOME}/bin/stopWebLogic.sh
}

########### SIGKILL handler ############
function _kill() {
   echo "SIGKILL received, shutting down the server!"
   kill -9 $childPID
}

# Set SIGTERM handler
trap _term SIGTERM

# Set SIGKILL handler
trap _kill SIGKILL

#Loop determining state of WLS
function check_wls {
    action=$1
    host=$2
    port=$3
    sleeptime=$4
    while true
    do
        sleep $sleeptime
        if [ "$action" == "started" ]; then
            started_url="http://$host:$port/weblogic/ready"
            echo -e "Waiting for WebLogic server to get $action, checking $started_url"
            status=`/usr/bin/curl -s -i $started_url | grep "200 OK"`
            echo "Status:" $status
            if [ ! -z "$status" ]; then
              break
            fi
        elif [ "$action" == "shutdown" ]; then
            shutdown_url="http://$host:$port"
            echo -e "Waiting for WebLogic server to get $action, checking $shutdown_url"
            status=`/usr/bin/curl -s -i $shutdown_url | grep "500 Can't connect"`
            if [ ! -z "$status" ]; then
              break
            fi
        fi
    done
    echo -e "WebLogic Server has $action"
}


ADD_DOMAIN=1
if [  -f ${DOMAIN_HOME}/servers/${ADMIN_NAME}/logs/${ADMIN_NAME}.log ]; then
    exit
fi

# Create Domain only if 1st execution
DOMAIN_PROPERTIES_FILE=${PROPERTIES_FILE_DIR}/domain.properties
if [ ! -e "${DOMAIN_PROPERTIES_FILE}" ]; then
   echo "A properties file with the username and password needs to be supplied."
   exit
fi

# Get Username
USER=`awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep ADMIN_USER_NAME | cut -d "=" -f2`
if [ -z "${USER}" ]; then
   echo "The domain username is blank.  The Admin username must be set in the properties file."
   exit
fi
# Get Password
PASS=`awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep ADMIN_USER_PASS | cut -d "=" -f2`
if [ -z "${PASS}" ]; then
   echo "The domain password is blank.  The Admin password must be set in the properties file."
   exit
fi

echo "Content of ${DOMAIN_PROPERTIES_FILE}:"
cat ${DOMAIN_PROPERTIES_FILE}

# Create domain
wlst.sh -skipWLSModuleScanning -loadProperties ${DOMAIN_PROPERTIES_FILE} /u01/oracle/create-wls-domain.py
