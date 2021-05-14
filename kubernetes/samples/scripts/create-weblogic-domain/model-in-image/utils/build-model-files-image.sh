# !/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

WORKDIR=/tmp/mii-files-image
TAGNAME=model-files-image:v1
mkdir -p $WORKDIR/models
cp /tmp/mii-sample/dockerfile/Dockerfile $WORKDIR
unzip  /tmp/mii-sample/model-images/weblogic-deploy.zip -d $WORKDIR
cp /tmp/mii-sample/model-images/model-in-image__WLS-v1/* $WORKDIR/models
rm $WORKDIR/weblogic-deploy/bin/*.cmd
cd $WORKDIR
#  Use additional docker build-arg as necessary to override default --build-arg=NAME=VALUE
#
#  MOUNT_PATH=/common (default)
#  USER_ID=1000 (default)d
#  USER=oracle (default)
#  GROUP=root (default)
#
docker build --tag ${TAGNAME}  .
