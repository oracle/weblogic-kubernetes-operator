#!/bin/sh
# Copyright (c) 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
drain() {
  # Simulate a final upload that needs time after all regular containers have exited.
  sleep 5
  if [ -s /recordings/final.jfr ] && [ -f /recordings/regular-final ]; then
    cp /recordings/final.jfr "/archives/${POD_UID}.jfr"
    cp /recordings/regular-final "/archives/${POD_UID}.regular"
    echo complete > "/archives/${POD_UID}.completed"
    echo "Final recording archived for ${POD_UID}"
  fi
  exit 0
}
trap drain TERM
touch /recordings/started
while :; do
  if [ -f /recordings/fail ]; then
    exit 1
  fi
  sleep 1 &
  wait $!
done
