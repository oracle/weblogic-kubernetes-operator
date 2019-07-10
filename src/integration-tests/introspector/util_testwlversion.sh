#!/bin/sh

# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Description:
# ------------
#
# This helper utility tests the operator's wl version check code. 
# It's intended to be run on an image with an oracle install
# and assumes the ORACLE_HOME env var has been set.
#
# Usage:
# ------
#
# ./util_testwlversion.sh input_file_name output_dir
#

traceFile=/weblogic-operator/scripts/traceUtils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

test_checkWebLogicVersion()
{
  local testout="/tmp/unit_test_checkWebLogicVersion"
  rm -f $testout
  (

    local WLVER="12.2.1.3"
    versionGE "$WLVER" "12.2.1.3" || echo "ERROR not GE 12.2.1.3"
    versionGE "$WLVER" "12.2.1.2" || echo "ERROR not GE 12.2.1.2"
    versionGE "$WLVER" "11"       || echo "ERROR not GE 11.9.9"
    versionGE "$WLVER" "11.9.9"   || echo "ERROR not GE 11.9.9"
    versionGE "$WLVER" "11.9.9.9" || echo "ERROR not GE 11.9.9.9"
    versionEQ "$WLVER" "12.2.1.3" || echo "ERROR not EQ 12.2.1.3"
    versionGE "$WLVER" "12.2.1.4" && echo "ERROR GE 12.2.1.4"
    versionEQ "$WLVER" "12.2.1"   || echo "ERROR EQ 12.2.1"
    versionEQ "$WLVER" "12.2.1.4" && echo "ERROR EQ 12.2.1.4"
    versionEQ "$WLVER" "12.2.1.2" && echo "ERROR EQ 12.2.1.2"
    # hasWebLogicPatches returns success if entire inventory file is missing
    hasWebLogicPatches 999767676         && echo "ERROR has impossible patch"
    local WLVER="`getWebLogicVersion`"
    # WLVER will be 9999.9999.9999.9999 if the version can't be retrieved
    versionGE "$WLVER" "888"      && echo "ERROR could not get WLVER, got $WLVER"
    versionGE "$WLVER" "12.2.1.3" || echo "ERROR wl version too low, got $WLVER"
    checkWebLogicVersion

  ) 2<&1 > $testout 2>&1
  cat $testout
  grep --silent -i ERROR $testout && return 1
  rm -f $testout
  return 0
}

# TBD move this to the unit test script
test_checkWebLogicVersion || exit 1
trace Test passed.
exit 0
