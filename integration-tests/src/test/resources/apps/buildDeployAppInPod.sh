#!/bin/sh

# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

usage()
{
    printf "\n"
    echo 1>&2 "Usage: sh $0 node-hostname node-port username password dir-in-pod-to-save-app-files appname deploy-target app-info-dir archive-file-name"
    printf "\n"
    echo 1>&2 "e.g., to build WAR file: sh $0 hostname123 30305 myuser mypwd /u01/oracle/apps/webtestapp webtestapp cluster-1 WEB-INF war"
    printf "\n"
    echo 1>&2 "e.g., to build EAR file: sh $0 hostname123 30305 myuser mypwd /u01/oracle/apps/ejbtestapp ejbtestapp cluster-1 META-INF ear"
    printf "\n"
}


##### Main

if [ $1 = "-h" ] || [ $# -eq 0 ]; then
    usage
    exit 0
fi

HOST=$1
PORT=$2
USER=${3:-weblogic}
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

echo -e "mkdir -p stagedir/${APP_INFO_DIR}/classes\n"
mkdir -p stagedir/${APP_INFO_DIR}/classes

echo -e "cp -r ${APP_INFO_DIR}/* stagedir/${APP_INFO_DIR}/\n"
cp -r ${APP_INFO_DIR}/* stagedir/${APP_INFO_DIR}/

echo -e "javac -d stagedir/${APP_INFO_DIR}/classes *.java\n"
javac -d stagedir/${APP_INFO_DIR}/classes *.java

echo -e "jar -cvf ${ARCHIVE_FILE_NAME} -C stagedir .\n"
jar -cvf ${ARCHIVE_FILE_NAME} -C stagedir .

echo "Deploy ${APP_NAME} using cmd:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${APP_DIR_INPOD}/${ARCHIVE_FILE_NAME}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${APP_DIR_INPOD}/${ARCHIVE_FILE_NAME}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments

rm -rf stagedir

exit 0