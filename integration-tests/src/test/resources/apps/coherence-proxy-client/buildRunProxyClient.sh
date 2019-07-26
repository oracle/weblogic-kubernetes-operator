#!/bin/sh

# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

usage()
{
    printf "\n"
    echo 1>&2 "Usage: sh $0 proxy-host proxy-port operation"
    printf "\n"
    echo 1>&2 "e.g., to build and run proxy client to load cached: sh $0 hostname123 30305 load"
    printf "\n"
    echo 1>&2 "e.g., to build and run proxy client to validate cache: sh $0 hostname123 30305 validate"
    printf "\n"
}


##### Main

if [ $1 = "-h" ] || [ $# -eq 0 ]; then
    usage
    exit 0
fi

PROXY_HOST=$1
PROXY_PORT=$2
OP=$3
PASSWORD=${4:-welcome1}
APP_DIR_INPOD=$5
APP_NAME=$6
DEPLOY_TARGET=$7
APP_INFO_DIR=$8
ARCHIVE_FILE_EXT=$9
ARCHIVE_FILE_NAME=${APP_NAME}.${ARCHIVE_FILE_EXT}

echo "App location in the pod: ${APP_NAME}"
echo "App name: ${APP_NAME}"
echo "Deploy the app to: ${DEPLOY_TARGET}"

source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh

cd ${APP_DIR_INPOD}

echo -e "running ant build from ${APP_DIR_INPOD}\n"
ant build

echo -e "running proxy client"
java -Dtangosol.coherence.proxy.address=${PROXY_HOST} -Dtangosol.coherence.proxy.port=${PROXY_PORT} -cp ${ORACLE_HOME}/coherence/lib/coherence.jar:./target/proxy-client-1.0.jar cohapp.Main validate.

echo "Deploy ${APP_NAME} using cmd:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${APP_DIR_INPOD}/${ARCHIVE_FILE_NAME}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${APP_DIR_INPOD}/${ARCHIVE_FILE_NAME}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments

#rm -rf stagedir

exit