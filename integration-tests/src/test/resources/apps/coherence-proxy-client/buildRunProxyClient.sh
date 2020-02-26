#!/bin/sh

# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

usage()
{
    printf "\n"
    echo 1>&2 "Usage: sh $0 operation proxy-host proxy-port"
    printf "\n"
    echo 1>&2 "e.g., to build and run proxy client to load cached: sh $0 /u01/oracle/app  load hostname123 30305"
    printf "\n"
    echo 1>&2 "e.g., to build and run proxy client to validate cache: sh $0 /u01/oracle/app  validate hostname123 30305"
    printf "\n"
}


##### Main

if [ $1 = "-h" ] || [ $# -eq 0 ]; then
    usage
    exit 0
fi

APP_DIR_INPOD=$1
OP=$2
PROXY_HOST=$3
PROXY_PORT=$4

echo "App location in the pod: ${APP_DIR_INPOD}"
echo "Operation : ${OP}"
echo "PROXY_HOST : ${PROXY_HOST}"
echo "PROXY_PORT : ${PROXY_PORT}"

source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh

cd ${APP_DIR_INPOD}

echo -e "running ant build from ${APP_DIR_INPOD}"
ant

# Proxy client will return exit code of 0 for success
echo -e "running proxy client"
java -Dtangosol.coherence.proxy.address=${PROXY_HOST} -Dtangosol.coherence.proxy.port=${PROXY_PORT} -cp ${ORACLE_HOME}/coherence/lib/coherence.jar:./target/proxy-client-1.0.jar cohapp.Main ${OP}
retVal=$?
if [ $retVal -eq 0 ] ; then
  # echo the marker string that is expected by the integration test
  echo "CACHE-SUCCESS"
fi

exit $retVal