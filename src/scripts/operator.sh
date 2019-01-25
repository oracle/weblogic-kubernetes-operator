#!/bin/bash
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export PATH=$PATH:/operator

echo "Launching Oracle WebLogic Server Kubernetes Operator..."

# Relays SIGTERM to all java processes
function relay_SIGTERM {
  pid=`grep java /proc/[0-9]*/comm | awk -F / '{ print $3; }'`
  echo "Sending SIGTERM to java process " $pid
  kill -SIGTERM $pid
}

trap relay_SIGTERM SIGTERM

/operator/initialize-internal-operator-identity.sh

if [[ ! -z "$REMOTE_DEBUG_PORT" ]]; then
  DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$HOSTNAME:$REMOTE_DEBUG_PORT"
  echo "DEBUG=$DEBUG"
else
  DEBUG=""
fi

# start logstash

# set up a logging.properties file that has a FileHandler in it, and have it
# write to /logs/operator.log
LOGGING_CONFIG="/operator/logstash.properties"

# if the java logging level has been customized and is a valid value, update logstash.properties to match
if [[ ! -z "$JAVA_LOGGING_LEVEL" ]]; then
  SEVERE="SEVERE"
  WARNING="WARNING"
  INFO="INFO"
  CONFIG="CONFIG"
  FINE="FINE"
  FINER="FINER"
  FINEST="FINEST"
  if [ $JAVA_LOGGING_LEVEL != $SEVERE  ] && \
     [ $JAVA_LOGGING_LEVEL != $WARNING ] && \
     [ $JAVA_LOGGING_LEVEL != $INFO    ] && \
     [ $JAVA_LOGGING_LEVEL != $CONFIG  ] && \
     [ $JAVA_LOGGING_LEVEL != $FINE    ] && \
     [ $JAVA_LOGGING_LEVEL != $FINER   ] && \
     [ $JAVA_LOGGING_LEVEL != $FINEST  ]; then
    echo "WARNING: Ignoring invalid JAVA_LOGGING_LEVEL: \"${JAVA_LOGGING_LEVEL}\". Valid values are $SEVERE, $WARNING, $INFO, $CONFIG, $FINE, $FINER and $FINEST."
  else
    sed -i -e "s|INFO|${JAVA_LOGGING_LEVEL}|g" $LOGGING_CONFIG
  fi
fi

if [ "${MOCK_WLS}" == 'true' ]; then
  MOCKING_WLS="-DmockWLS=true"
fi

LOGGING="-Djava.util.logging.config.file=${LOGGING_CONFIG}"
mkdir -m 777 -p /logs
cp /operator/logstash.conf /logs/logstash.conf
# assumption is that we have mounted a volume on /logs which is also visible to
# the logstash container/pod.

# Container memory optimizaton flags
HEAP="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XshowSettings:vm"

# Start operator
java $HEAP $MOCKING_WLS $DEBUG $LOGGING -jar /operator/weblogic-kubernetes-operator.jar &
PID=$!
wait $PID
