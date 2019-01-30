#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

id=$(docker create store/oracle/weblogic:19.1.0.0)
docker cp $id:/u01/oracle/wlserver/server/lib/wlthint3client.jar $SCRIPTPATH
docker rm -v $id