#!/bin/bash
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "Launching the Domain Custom Resource Conversion Webhook for Oracle WebLogic Server Kubernetes Operator..."

# Relays SIGTERM to all java processes
relay_SIGTERM() {
  pid=`grep java /proc/[0-9]*/comm | awk -F / '{ print $3; }'`
  echo "Sending SIGTERM to java process " $pid
  kill -SIGTERM $pid
}

trap relay_SIGTERM SIGTERM

if [[ ! -z "$REMOTE_DEBUG_PORT" ]]; then
  DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:$REMOTE_DEBUG_PORT"
  echo "DEBUG=$DEBUG"
else
  DEBUG=""
fi

# start logstash

# set up a logging.properties file that has a FileHandler in it, and have it
# write to /logs/operator.log
LOGGING_CONFIG="/deployment/logstash.properties"

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

sed -i -e "s|JAVA_LOGGING_MAXSIZE|${JAVA_LOGGING_MAXSIZE:-20000000}|g" $LOGGING_CONFIG
sed -i -e "s|JAVA_LOGGING_COUNT|${JAVA_LOGGING_COUNT:-10}|g" $LOGGING_CONFIG

LOGGING="-Djava.util.logging.config.file=${LOGGING_CONFIG}"
# assumption is that we have mounted a volume on /logs which is also visible to
# the logstash container/pod.
mkdir -m 777 -p /logs

# Container memory optimization flags
HEAP="-XshowSettings:vm"

# Start operator
java -cp /operator/weblogic-kubernetes-operator.jar $HEAP $MOCKING_WLS $DEBUG $LOGGING oracle.kubernetes.operator.WebhookMain &
PID=$!
wait $PID

DEPLOYMENT_DIR="/deployment"
SHUTDOWN_COMPLETE_MARKER_FILE="${DEPLOYMENT_DIR}/marker.shutdown-complete"

touch ${SHUTDOWN_COMPLETE_MARKER_FILE}
