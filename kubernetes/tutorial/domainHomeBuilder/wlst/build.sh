#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

MYDIR="$(dirname "$(readlink -f "$0")")"
imageName=$1-image
echo "build image $imageName"
docker build --build-arg ARG_DOMAIN_NAME=$1  --build-arg ADMIN_USER=$2 \
 --build-arg ADMIN_PWD=$3 $MYDIR --force-rm -t $imageName
