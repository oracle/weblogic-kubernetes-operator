#!/bin/bash

# Copyright (c) 2017, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Kubernetes periodically calls this liveness probe script to determine whether
# the pod should be restarted. The script checks a WebLogic Server state file.

copySitCfgWhileRunning() {
  # Helper fn to copy sit cfg xml files to the WL server's domain home.
  #   - params $1/$2/$3 == 'src_dir tgt_dir fil_prefix'
  #   - $src_dir files are assumed to start with $fil_prefix and end with .xml
  #   - copied $tgt_dir files are stripped of their $fil_prefix
  #   - any .xml files in $tgt_dir that are not in $src_dir/$fil_prefix+FILE are deleted
  #
  # This method is called while the server is already running, see 
  # 'copySitCfgWhileBooting' in 'startServer.sh' for a similar method that
  # is called just before the server boots.
  #
  # It will do nothing unless the environment variable DYNAMIC_CONFIG_OVERRIDE is set.

if [ ! "${DYNAMIC_CONFIG_OVERRIDE:-notset}" = notset ]; then
    (
    shopt -s nullglob  # force file matching 'glob' for loops below to run 0 times if 0 matches
    local src_dir=${1?}
    local tgt_dir=${2?}
    local fil_prefix=${3?}
    local local_fname
    local tgt_file
    mkdir -p $tgt_dir # TBD ignore any error?
    for local_fname in ${src_dir}/${fil_prefix}*.xml ; do
      tgt_file=${local_fname/$fil_prefix//}   # strip out file prefix from source file
      tgt_file=$(basename $tgt_file)          # strip out dir path since it's the source file path
      tgt_file=$tgt_dir/$tgt_file             # add back in tgt dir path
      [ -f "$tgt_file" ] && [ -z "$(diff $local_fname $tgt_file 2>&1)" ] && continue  # nothing changed
      trace "Copying file '$local_fname' to '$tgt_file'."
      copyIfChanged $local_fname $tgt_file
      if [ -O "$tgt_file" ]; then
        chmod 770 $tgt_file
      fi
    done
    for local_fname in ${tgt_dir}/*.xml ; do
      if [ -f "$src_dir/${fil_prefix}$(basename ${local_fname})" ]; then
        continue
      fi
      trace "Deleting '$local_fname' since it has no corresponding '$src_dir' file."
      rm -f $local_fname # TBD ignore any error?
    done
    )
  fi
}

# if the livenessProbeSuccessOverride file is available, treat failures as success:
RETVAL=$(test -f /weblogic-operator/debug/livenessProbeSuccessOverride ; echo $?)

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit $RETVAL

# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
exportEffectiveDomainHome || exit $RETVAL
exportInstallHomes || exit $RETVAL

# DEBUG
trace "Entering livenessProbe.sh" >> /proc/1/fd/1

state=$(${SCRIPTPATH}/readState.sh)
RETVAL=$?
if [ $RETVAL -eq 2 ]; then
  trace "Server state file not found; assuming dead"

  # DEBUG
  trace "Exiting 2 livenessProbe.sh" >> /proc/1/fd/1

  exit 2
fi

# If state failed, then dead
if [[ "$state" =~ ^FAILED ]]; then
  trace SEVERE "Server in failed state"

  # DEBUG
  trace "Exiting 2 livenessProbe.sh" >> /proc/1/fd/1

  exit 2
fi

# If state not shutdown and process not found, then dead
if [ $RETVAL -eq 1 ] && [[ ! "$state" =~ SHUT ]]; then
  trace "Server process not found and state not shutdown"

  # DEBUG
  trace "Exiting 1 livenessProbe.sh" >> /proc/1/fd/1

  exit 1
fi

if [ -x ${LIVENESS_PROBE_CUSTOM_SCRIPT} ]; then
  $LIVENESS_PROBE_CUSTOM_SCRIPT
elif [ -O ${LIVENESS_PROBE_CUSTOM_SCRIPT} ]; then
  chmod 770 $LIVENESS_PROBE_CUSTOM_SCRIPT && $LIVENESS_PROBE_CUSTOM_SCRIPT
fi
if [ $? != 0 ]; then
  trace SEVERE "Execution of custom liveness probe script ${LIVENESS_PROBE_CUSTOM_SCRIPT} failed."
  exit $RETVAL
fi

if [ ${DOMAIN_SOURCE_TYPE} != "FromModel" ]; then
  copySitCfgWhileRunning /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig             'Sit-Cfg-CFG--'
  copySitCfgWhileRunning /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jms         'Sit-Cfg-JMS--'
  copySitCfgWhileRunning /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/jdbc        'Sit-Cfg-JDBC--'
  copySitCfgWhileRunning /weblogic-operator/introspector ${DOMAIN_HOME}/optconfig/diagnostics 'Sit-Cfg-WLDF--'
fi

# DEBUG
trace "Exiting alive livenessProbe.sh" >> /proc/1/fd/1

exit 0
