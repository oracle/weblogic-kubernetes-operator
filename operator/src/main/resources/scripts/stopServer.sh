#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

SN="${SERVER_NAME?}"

echo "Stop server ${SN}"

wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/stop-server.py 

# Return status of 2 means failed to stop a server through the NodeManager.
# Look to see if there is a server process that can be killed.
if [ $? -eq 2 ]; then
  pid=$(jps -v | grep '[D]weblogic.Name=${SN}' | awk '{print $1}')
  if [ ! -z $pid ]; then
    echo "Killing the server process $pid"
    kill -15 $pid
  fi
fi


