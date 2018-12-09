# !/bin/sh

# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# 
# Description:
# ------------
#
# This helper utility prints dots using a background process 
# while a foreground process runs.  Use it to provide feedback
# while you wait for the foreground process to finish.
#
# It stops printing dots when any of the following are true:
#   - printdots_end is called
#   - the process that called printdots_start is no longer around
#   - more than 120 seconds has passed
#
# Usage example:
#    source ./util_dots.sh
#
#    echo -n "Hello"
#    printdots_start 1
#    sleep 6
#    printdots_end
#
# The first parameter of printdots_start is the number of seconds
# to wait before each dot - default is 2 seconds.
#
# 'printdots_end' calls 'echo' without any parameters.
#

printdots_inner() {
  local start_pids="`ps $PPID 2>&1`"
  local dotsfile=$1
  local begin_sec=$SECONDS
  [ -f "$dotsfile" ] || return
  while [ 1 -eq 1 ]; do
    sleep ${2:-1}
    # if parent pids changed, then our parent died and we should exit
    local end_pids="`ps $PPID 2>&1`"
    [ "$start_pids" = "$end_pids" ] || break

    # if too much time has passed, then exit
    [ $((SECONDS-begin_sec)) -lt 120 ] || break

    # if parent deleted our temp file, then exit
    [ -f "$dotsfile" ] || break

    echo -n "."
  done
  rm -f $dotsfile
}

printdots_start() {
  dotsfile=$(mktemp /tmp/dots.`basename $0`.XXXXXXXXX)
  touch $dotsfile
  printdots_inner $dotsfile $1 &
  printdots_pid=$!
  printdots_time_start=$SECONDS
}

printdots_end() {
  rm -f $dotsfile
  wait $printdots_pid > /dev/null 2>&1
  echo "  ($((SECONDS - printdots_time_start)) seconds)"
}
