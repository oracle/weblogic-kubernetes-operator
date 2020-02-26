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
//APP_NAME=$6 - ignore this for coherence since the dir name and the app name are different.
EAR_DEPLOY_TARGET=$7
APP_NAME=$8
GAR_DEPLOY_TARGET=$9
ARCHIVE_FILE_GAR=${APP_DIR_INPOD}/${APP_NAME}.gar
ARCHIVE_FILE_EAR=${APP_DIR_INPOD}/${APP_NAME}.ear
echo "App location in the pod: ${APP_DIR_INPOD}"
echo "App name: ${APP_NAME}"
echo "Deploy the app to: ${DEPLOY_TARGET}"
if [ -f "${ARCHIVE_FILE_GAR}" ] && [ -f "${ARCHIVE_FILE_EAR}" ]
then
	echo "${APP_NAME}.war and ${APP_NAME}.ear found. Skipping build and deploy"
	exit 0
fi

source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh

cd ${APP_DIR_INPOD}

echo -e "calling ant to build GAR file and application EAR file"

#ant build -Dhost=${HOST}  -DclusterUrl=${CLUSTER_URL} -DappLocationInPod=${PWD} -DwsName=${WS_NAME} -DappName=${APP_NAME}
ant build 
antReturnCode=$?
 
echo "ANT: Return code is: \""$antReturnCode"\""
 
if [ $antReturnCode -ne 0 ];then
    echo "BUILD ERROR: ..."
    exit 1;
else
    echo "GREAT SUCCESS: !"
fi
cp ${APP_DIR_INPOD}/builddir/${APP_NAME}.gar ${APP_DIR_INPOD}/.
cp ${APP_DIR_INPOD}/builddir/${APP_NAME}.ear ${APP_DIR_INPOD}/.

echo "Deploy ${APP_NAME} gar using curl:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F \"model={ name: '${APP_NAME}GAR', targets: [ { identity: [ clusters, '${GAR_DEPLOY_TARGET}' ] } ] }\" -F \"sourcePath=@${ARCHIVE_FILE_GAR}\"  -H \"Prefer:respond-async\" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployGAR.out\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}GAR', targets: [ { identity: [ clusters, '${GAR_DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${ARCHIVE_FILE_GAR}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployGAR.out
sleep 10
grep -q "STATE_RUNNING" ${APP_DIR_INPOD}/deployGAR.out
cres=$?
[[ $cres != 0 ]] && echo "[FAIL] Unable to deploy  gar app ..." && exit -1
[[ $cres == 0 ]] && echo "[SUCCESS] gar app is deployed  ..."

echo "Deploy ${APP_NAME} ear using curl:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F \"model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${EAR_DEPLOY_TARGET}' ] } ] }\" -F \"sourcePath=@${ARCHIVE_FILE_EAR}\" -H \"Prefer:respond-async\" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployear.out\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${EAR_DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${ARCHIVE_FILE_EAR}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments -o ${APP_DIR_INPOD}/deployear.out
sleep 10
grep -q "STATE_RUNNING" ${APP_DIR_INPOD}/deployear.out
cres=$?
[[ $cres != 0 ]] && echo "[FAIL] Unable to deploy ear  ..." exit -1
[[ $cres == 0 ]] && echo "[SUCCESS] ear file is deployed  ..."


rm -rf ${APP_DIR_INPOD}/builddir

exit 0
