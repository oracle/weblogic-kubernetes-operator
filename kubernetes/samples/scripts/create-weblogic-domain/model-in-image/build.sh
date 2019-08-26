#!/bin/bash
shopt -s expand_aliases
cp -R * $1
cd $1
unzip V982783-01.zip
#
downloadlink=$(curl -sL https://github.com/oracle/weblogic-image-tool/releases/latest | grep "/oracle/weblogic-image-tool/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo Downdloading $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-image-tool.zip
#
downloadlink=$(curl -sL https://github.com/oracle/weblogic-deploy-tooling/releases/latest | grep "/oracle/weblogic-deploy-tooling/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-deploy.zip
#
unzip weblogic-image-tool.zip
#
. $1/imagetool-*/bin/setup.sh
#
mkdir cache
export WLSIMG_CACHEDIR=`pwd`/cache
export WLSIMG_BLDDIR=`pwd`
#
imagetool cache addInstaller --type jdk --version 8u221 --path `pwd`/server-jre-8u221-linux-x64.tar.gz
imagetool cache addInstaller --type wls --version 12.2.1.3.0 --path `pwd`/V886423-01.zip
#
imagetool create --tag model-in-image:x0 --user $2 --password $3 --patches 29135930_12.2.1.3.190416,29016089 --jdkVersion 8u221
#
./build_app.sh
#
cp weblogic-deploy.zip image
cd image
docker build --tag model-in-image:x1 .
cd ..
#
./k8sdomain.sh
#
echo "Getting pod status - ctrl-c when all is running and ready to exit"
kubectl get pods -n sample-domain1-ns --watch
#







