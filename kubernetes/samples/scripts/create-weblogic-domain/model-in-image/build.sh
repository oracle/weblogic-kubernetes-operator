#!/bin/bash
#
# Usage: build.sh <working directory> <oracle support id> <oracle support id password>
#
#set -e
usage() {
    echo "build.sh <working directory> <oracle support id> <oracle support id password> <image type:WLS|FMW>"
}
if [ "$#" != 4 ] ; then
    usage && exit
fi

if [ ! "$4" == "WLS" ] && [ ! "$4" == "FMW" ] ; then  echo "Invalid image type: WLS or FMW"; fi

if [ ! -d "$1" ] ; then
 echo "Directory $1 does not exists." && exit 
fi

if [ -f "V982783-01.zip" ] ; then
 echo "Directory $1 does not contain V982783-01.zip." && exit 
fi

if [ -f "V886243-01.zip" ] && [ "$4" == "WLS" ] ; then
 echo "Directory $1 does not contain V886243-01.zip." && exit 
fi

if [ -f "V886246-01.zip" ] && [ "$4" == "FMW" ] ; then
 echo "Directory $1 does not contain V886243-01.zip." && exit 
fi

#
#
shopt -s expand_aliases
cp -R * $1
cd $1
unzip V982783-01.zip
#
echo Downloading latest WebLogic Image Tool

downloadlink=$(curl -sL https://github.com/oracle/weblogic-image-tool/releases/latest | grep "/oracle/weblogic-image-tool/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo Downdloading $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-image-tool.zip

echo Downloading latest WebLogic Deploy Tool

downloadlink=$(curl -sL https://github.com/oracle/weblogic-deploy-tooling/releases/latest | grep "/oracle/weblogic-deploy-tooling/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-deploy.zip

unzip weblogic-image-tool.zip
#
echo Setting up imagetool
#
source $1/imagetool-*/bin/setup.sh
#
mkdir cache
export WLSIMG_CACHEDIR=`pwd`/cache
export WLSIMG_BLDDIR=`pwd`
#
imagetool cache addInstaller --type jdk --version 8u221 --path `pwd`/server-jre-8u221-linux-x64.tar.gz
if [ "$4" == "WLS" ] ; then
    imagetool cache addInstaller --type wls --version 12.2.1.3.0 --path `pwd`/V886423-01.zip
    IMGTYPE=wls
else 
    imagetool cache addInstaller --type fmw --version 12.2.1.3.0 --path `pwd`/V886426-01.zip
    IMGTYPE=fmw
fi
imagetool cache addInstaller --type wdt --version latest --path `pwd`/weblogic-deploy.zip
#
echo Creating base image with patches
#
imagetool create --tag model-in-image:x0 --user $2 --password $3 --patches 29135930_12.2.1.3.190416,29016089 --jdkVersion 8u221 --type ${IMGTYPE}
#
# Building sample app ear file
#
./build_app.sh
#
echo Creating deploy image with wdt models
#
cd image
imagetool update --tag model-in-image:x1 --fromImage model-in-image:x0 --wdtModel model1.yaml --wdtVariables model1.10.properties --wdtArchive archive1.zip --wdtModelOnly --wdtDomainType $4
cd ..
# cp weblogic-deploy.zip image
# cd image
# docker build --tag model-in-image:x1 .
# cd ..
#
echo Settng up domain resources
#
./k8sdomain.sh
#
echo "Getting pod status - ctrl-c when all is running and ready to exit"
kubectl get pods -n sample-domain1-ns --watch
#







