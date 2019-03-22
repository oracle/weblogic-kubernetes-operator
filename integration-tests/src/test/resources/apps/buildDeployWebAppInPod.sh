#!/bin/sh

usage()
{
    printf "\n"
    echo 1>&2 "Usage: sh $0 node-hostname node-port username password dir-in-pod-to-save-app-files appname deploy-target"
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
WAR_FILE_NAME=${APP_NAME}.war

echo "App location in the pod: ${APP_NAME}"
echo "App name: ${APP_NAME}"
echo "Deploy the app to: ${DEPLOY_TARGET}"

source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh

cd ${APP_DIR_INPOD}

echo -e "mkdir -p stagedir/WEB-INF/classes\n"
mkdir -p stagedir/WEB-INF/classes

echo -e "cp -r WEB-INF/* stagedir/WEB-INF/\n"
cp -r WEB-INF/* stagedir/WEB-INF/

echo -e "javac -d stagedir/WEB-INF/classes *.java\n"
javac -d stagedir/WEB-INF/classes *.java

echo -e "jar -cvf ${WAR_FILE_NAME} -C stagedir .\n"
jar -cvf ${WAR_FILE_NAME} -C stagedir .

echo "Deploy ${APP_NAME} using cmd:"
echo -e "curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${APP_DIR_INPOD}/${WAR_FILE_NAME}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments\n"
curl --noproxy '*' --silent  --user ${USER}:${PASSWORD} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: '${APP_NAME}', targets: [ { identity: [ clusters, '${DEPLOY_TARGET}' ] } ] }" -F "sourcePath=@${APP_DIR_INPOD}/${WAR_FILE_NAME}" -H "Prefer:respond-async" -X POST http://${HOST}:${PORT}/management/weblogic/latest/edit/appDeployments

exit 0