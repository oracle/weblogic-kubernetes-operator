#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Summary:
#   This helper script finds and runs weblogic.WLST for a given WLST script,
#   and exits Exits non-zero on failure.
#
# Input env vars:
#    - JAVA_HOME - required
#    - WL_HOME - optional - default is /u01/oracle/wlserver
#    - MW_HOME - optional - default is /u01/oracle
#
# Usage:
#   SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
#   $SCRIPTPATH/wlst.sh myscript.py
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1 

wlst_script=${1?}

trace "Running wlst script '${wlst_script}'"

export WL_HOME=${WL_HOME:-/u01/oracle/wlserver}
export MW_HOME=${MW_HOME:-/u01/oracle}

checkEnv JAVA_HOME \
         WL_HOME \
         MW_HOME \
         || exit 1

[ ! -f "$wlst_script" ] && trace "Error: missing file '$wlst_script'." && exit 1 

wlst_sh=""
wlst_loc1="${WL_HOME}/../oracle_common/common/bin/wlst.sh"
wlst_loc2="${MW_HOME}/oracle_common/common/bin/wlst.sh"
[ -f "$wlst_loc2" ]   && wlst_sh="$wlst_loc2"
[ -f "$wlst_loc1" ]   && wlst_sh="$wlst_loc1"
[ -z "$wlst_sh" ] && trace "Error: '${wlst_loc1}' or '${wlst_loc2}' not found, make sure WL_HOME or MW_HOME is set correctly." && exit 1

${wlst_sh} -skipWLSModuleScanning ${wlst_script}
res="$?"

if [ $res -eq 0 ]; then
  trace "Info: WLST script '${wlst_script}' completed."
else
  trace "Error: WLST script '${wlst_script}' failed with exit code '$res'." 
fi

exit $res
