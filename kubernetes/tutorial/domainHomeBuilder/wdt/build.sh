#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
set -e 

if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

MYDIR="$(dirname "$(readlink -f "$0")")" # get the absolute path of this file's folder

# It may be better to do this in the container, but we need to handle proxy settings specifically.
if [ ! -e $MYDIR/weblogic-deploy.zip ] ; then
  echo "weblogic-deploy.zip does not exist. Downloading it from github."  
  wget -P $MYDIR https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-0.11/weblogic-deploy.zip
  echo "download complete"
fi

imageName=$1-image
echo "build image $imageName"
docker build --build-arg ARG_DOMAIN_NAME=$1  --build-arg ADMIN_USER=$2 \
 --build-arg ADMIN_PWD=$3 $MYDIR --force-rm -t $imageName

#rm $MYDIR/weblogic-deploy.zip
