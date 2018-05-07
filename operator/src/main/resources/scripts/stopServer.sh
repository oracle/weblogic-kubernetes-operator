#!/bin/bash

echo "Stop the server"

wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/stop-server.py $1 $2 $3

# Return status of 2 means failed to stop a server through the NodeManager.
# Look to see if there is a server process that can be killed.
if [ $? -eq 2 ]; then
  pid=$(jps -v | grep '[D]weblogic.Name=$2' | awk '{print $1}')
  if [ ! -z $pid ]; then
    echo "Killing the server process $pid"
    kill -15 $pid
  fi
fi


