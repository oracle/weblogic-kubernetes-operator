#!/bin/bash

# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Kubernetes periodically calls this liveness probe script to determine whether
# the pod should be restarted. The script checks a WebLogic Server state file which
# is updated by the node manager.

function newCopySitCfg() {
  (
  shopt -s nullglob  # force file matching 'glob' for loops below to run 0 times if 0 matches
  local src_dir=${1?}
  local tgt_dir=${2?}
  local fil_prefix=${3?}
  local local_fname
  local tgt_file
  trace "Copying files starting with '$src_dir/$fil_prefix' to '$tgt_dir' without the prefix."
  mkdir -p $tgt_dir # TBD ignore any error?
  for local_fname in {src_dir}/${fil_prefix}*.xml ; do
    tgt_file=${local_fname/$fil_prefix//}   # strip out file prefix from source file
    tgt_file=$(basename $tgt_file)          # strip out dir path since it's the source file path
    tgt_file=$tgt_dir/$tgt_file             # add back in tgt dir path
    [ -f "$tgt_file" ] && [ -z "$(diff $local_fname $tgt_file 2>&1)" ] && continue  # nothing changed
    cp $local_fname $tgt_file # TBD ignore any error?
    chmod 750 $tgt_file # TBD ignore any error?
  done
  for local_fname in {tgt_dir}/*.xml ; do
    if [ -f "$src_dir/${fil_prefix}$(basename ${local_fname})" ]; then
      continue
    fi
    trace "Deleting '$local_fname' since it has no corresponding '$src_dir' file."
    rm -f $local_fname # TBD ignore any error?
  done
  )
}


# if the livenessProbeSuccessOverride file is available, treat failures as success:
RETVAL=$(test -f /weblogic-operator/debug/livenessProbeSuccessOverride ; echo $?)

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit $RETVAL

# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
exportEffectiveDomainHome || exit $RETVAL

DN=${DOMAIN_NAME?}
SN=${SERVER_NAME?}
DH=${DOMAIN_HOME?}

STATEFILE=${DH}/servers/${SN}/data/nodemanager/${SN}.state

if [ "${MOCK_WLS}" != 'true' ]; then
  # Adjust PATH if necessary before calling jps
  adjustPath

  if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
    trace SEVERE "WebLogic NodeManager process not found."
    exit $RETVAL
  fi
fi
if [ -f ${STATEFILE} ] && [ `grep -c "FAILED_NOT_RESTARTABLE" ${STATEFILE}` -eq 1 ]; then
  trace SEVERE "WebLogic Server state is FAILED_NOT_RESTARTABLE."
  exit $RETVAL
fi

newCopySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig             'Sit-Cfg-CFG--'
newCopySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jms         'Sit-Cfg-JMS--'
newCopySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jdbc        'Sit-Cfg-JDBC--'
newCopySitCfg /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/diagnostics 'Sit-Cfg-WLDF--'

exit 0
