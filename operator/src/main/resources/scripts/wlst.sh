#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Summary:
#   This helper script finds and runs weblogic.WLST for a given WLST script,
#   and exits Exits non-zero on failure.
#
# Input env vars:
#    - JAVA_HOME - required
#    - Optionally set
#        ORACLE_HOME = Oracle Install Home - defaults via utils.sh/exportInstallHomes
#        MW_HOME     = MiddleWare Install Home - defaults to ${ORACLE_HOME}
#        WL_HOME     = WebLogic Install Home - defaults to ${ORACLE_HOME}/wlserver
#
# Usage:
#   SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
#   $SCRIPTPATH/wlst.sh myscript.py
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1 

wlst_script=${1?}

trace "About to run wlst script '${wlst_script}'"

# Set ORACLE_HOME/WL_HOME/MW_HOME to defaults if needed
exportInstallHomes

checkEnv JAVA_HOME \
         ORACLE_HOME \
         WL_HOME \
         MW_HOME \
         || exit 1

[ ! -f "$wlst_script" ] && trace SEVERE "Missing file '$wlst_script'." && exit 1 

wlst_sh=""
wlst_loc1="${WL_HOME}/../oracle_common/common/bin/wlst.sh"
wlst_loc2="${MW_HOME}/oracle_common/common/bin/wlst.sh"
[ -f "$wlst_loc2" ]   && wlst_sh="$wlst_loc2"
[ -f "$wlst_loc1" ]   && wlst_sh="$wlst_loc1"
[ -z "$wlst_sh" ] && trace SEVERE "'${wlst_loc1}' or '${wlst_loc2}' not found, make sure ORACLE_HOME, WL_HOME, or MW_HOME is set correctly." && exit 1

urandom_prop="-Djava.security.egd=file:/dev/./urandom"
if [ ! -v WLST_EXTRA_PROPERTIES ]; then
  trace "Env var WLST_EXTRA_PROPERTIES not set, defaulting to '$urandom_prop'."
  WLST_EXTRA_PROPERTIES="$urandom_prop"
else
  trace "Env var WLST_EXTRA_PROPERTIES has been externally set to '$WLST_EXTRA_PROPERTIES' (it will not default to '$urandom_prop')."
fi
export WLST_PROPERTIES="$WLST_PROPERTIES $WLST_EXTRA_PROPERTIES"
trace "Env var WLST_PROPERTIES='$WLST_PROPERTIES' (includes WLST_EXTRA_PROPERTIES)."
if [ "${WLST_PROPERTIES/$urandom_prop/}" = "${WLST_PROPERTIES}" ]; then
  trace "Env var WLST_PROPERTIES does not include '$urandom_prop'; this may significantly slow any use of the WLST encrypt command on low entropy hosts."
fi

trace "Running wlst script '${wlst_script}'"

${wlst_sh} -skipWLSModuleScanning ${wlst_script} "${@:2}"
res="$?"

if [ $res -eq 0 ]; then
  trace "WLST script '${wlst_script}' completed."
else
  trace SEVERE "WLST script '${wlst_script}' failed with exit code '$res'." 
fi

exit $res
