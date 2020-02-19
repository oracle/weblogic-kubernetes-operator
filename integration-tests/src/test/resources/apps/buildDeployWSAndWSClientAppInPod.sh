#!/bin/sh

# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

usage()
{
    printf "\n"
    echo 1>&2 "Usage: sh $0 node-hostname node-port username password dir-in-pod-to-save-app-files appname deploy-target app-info-dir clusterdns webservicename"
    printf "\n"
    echo 1>&2 "e.g., to build WAR file: sh $0 hostname123 30305 myuser mypwd /u01/oracle/apps/testwsapp testwsapp cluster-1 domainonpvwlst-managed-server1.svc.cluster.local:8001"
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
CLUSTER_URL=$8
WS_NAME=$9
ARCHIVE_FILE_WS=${APP_DIR_INPOD}/${WS_NAME}.war
ARCHIVE_FILE_SERVLET=${APP_DIR_INPOD}/${WS_NAME}Servlet.war
echo "App location in the pod: ${APP_DIR_INPOD}"
echo "App name: ${APP_NAME}, WebService module name: ${WS_NAME}"
echo "Deploy the app to: ${DEPLOY_TARGET}"
if [ -f "${ARCHIVE_FILE_WS}" ] && [ -f "${ARCHIVE_FILE_SERVLET}" ]
then
	echo "${WS_NAME}.war and ${WS_NAME}Servlet.war found. Skipping build and deploy"
	exit 0
fi

source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh

cd ${APP_DIR_INPOD}

echo -e "calling ant to build webservice and it's servlet client"

ant build -Dhost=${HOST}  -DclusterUrl=${CLUSTER_URL} -DappLocationInPod=${PWD} -DwsName=${WS_NAME} -DappName=${APP_NAME}
cp ${APP_DIR_INPOD}/buildfiles/${WS_NAME}.war ${APP_DIR_INPOD}/.
cp ${APP_DIR_INPOD}/buildfiles/${WS_NAME}Servlet.war ${APP_DIR_INPOD}/.

echo "Deploy ${APP_NAME} using curl:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F \"model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }\" -F \"sourcePath=@${ARCHIVE_FILE_WS}\"  -H \"Prefer:respond-async\" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployWS.out\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${ARCHIVE_FILE_WS}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployWS.out
sleep 10
grep -q "STATE_RUNNING" ${APP_DIR_INPOD}/deployWS.out
cres=$?
[[ $cres != 0 ]] && echo "[FAIL] Unable to deploy wsapp ..." && exit -1
[[ $cres == 0 ]] && echo "[SUCCESS] wsapp is deployed  ..."

echo "Deploy ${APP_NAME}Servlet using curl:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F \"model={ name: '${APP_NAME}servlet', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }\" -F \"sourcePath=@${ARCHIVE_FILE_SERVLET}\" -H \"Prefer:respond-async\" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployWSServlet.out\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}servlet', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${ARCHIVE_FILE_SERVLET}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployWSServlet.out
sleep 10
grep -q "STATE_RUNNING" ${APP_DIR_INPOD}/deployWSServlet.out
cres=$?
[[ $cres != 0 ]] && echo "[FAIL] Unable to deploy wsclientapp ..." exit -1
[[ $cres == 0 ]] && echo "[SUCCESS] wsclientapp is deployed  ..."


rm -rf ${APP_DIR_INPOD}/buildfiles

exit 0
