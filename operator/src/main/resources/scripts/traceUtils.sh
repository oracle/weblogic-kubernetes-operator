# Copyright 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Purpose:
#   Define a trace functions that match format of the trace function
#   in traceUtils.py and of the logging in the java operator.
#
# Load this file via the following pattern:
#   SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
#   source ${SCRIPTPATH}/traceUtils.sh
#   [ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1
#

# timestamp
#   purpose:  echo timestamp in the form yyyymmddThh:mm:ss.mmm ZZZ
#   example:  20181001T14:00:00.001 UTC
function timestamp() {
  local timestamp="`date --utc '+%Y-%m-%dT%H:%M:%S %N %s %Z' 2>&1`"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S 000000 %s %Z' 2>&1`"
  fi
  local ymdhms="`echo $timestamp | awk '{ print $1 }'`"
  # convert nano to milli
  local milli="`echo $timestamp | awk '{ print $2 }' | sed 's/\(^...\).*/\1/'`"
  local secs_since_epoch="`echo $timestamp | awk '{ print $3 }'`"
  local millis_since_opoch="${secs_since_epoch}${milli}"
  local timezone="`echo $timestamp | awk '{ print $4 }'`"
  echo "${ymdhms}.${milli} ${timezone}"
}

#
# trace
#   purpose: echo timestamp, caller's filename, and caller's line#
#   example: trace "Situation normal."
#            @[2018-09-28T18:10:52.417 UTC][myscript.sh:91] Info: situation normal.
#
#   Set TRACE_INCLUDE_FILE env var to false to suppress file name and line number.
#
function trace() {
  if [ ${TRACE_INCLUDE_FILE:-true} = "true" ]; then
    local line="${BASH_LINENO[0]}"
    echo "@[`timestamp`][`basename $0`:${line}] $*"
  else
    echo "@[`timestamp`] $*"
  fi
}

#
# tracen
#   purpose: same as trace, but no new new-line at the end.
#
function tracen() {
  if [ ${TRACE_INCLUDE_FILE:-true} = "true" ]; then
    local line="${BASH_LINENO[0]}"
    echo -n "@[`timestamp`][`basename $0`:${line}] $*"
  else
    echo -n "@[`timestamp`] $*"
  fi
}

#
# tracePipe
#   purpose:  Use to redirect stdout through a trace statement.
#   example:  ls 2>&1 | tracePipe "ls output:"
#
function tracePipe() {
  (
  # IFS='' causes read line to preserve leading spaces
  # -r cause read to treat backslashes as-is, e.g. '\n' --> '\n'
  IFS=''
  while read -r line; do
    trace "$@" "$line"
  done
  )
}

# 
# checkEnv
#   purpose: Check and trace the values of the provided env vars.
#            If any env vars don't exist or are empty, return non-zero
#            and trace an 'Error:'.
#
#   sample:  checkEnv HOST NOTSET1 USER NOTSET2
#            @[2018-10-05T22:48:04.368 UTC] Info: HOST='esscupcakes'
#            @[2018-10-05T22:48:04.393 UTC] Info: USER='friendly'
#            @[2018-10-05T22:48:04.415 UTC] Error: the following env vars are missing or empty:  NOTSET1 NOTSET2
#
function checkEnv() {
  local not_found=""
  while [ ! -z "${1}" ]; do 
    if [ -z "${!1}" ]; then
      not_found="$not_found ${1}"
    else
      trace "Info: ${1}='${!1}'"
    fi
    shift
  done
  if [ ! -z "${not_found}" ]; then
    trace "Error: the following env vars are missing or empty: ${not_found}"
    return 1
  fi
  return 0
}


#
# exportEffectiveDomainHome
#   if DOMAIN_HOME='/u01/oracle/user_projects/domains':
#     1) look for a config.xml in DOMAIN_HOME/config and
#        and in DOMAIN_HOME/*/config
#     2) Export DOMAIN_HOME to reflect the actual location 
#     3) Trace an Error and return non-zero if not found or more than 1 found
#
function exportEffectiveDomainHome() {
  local count=0
  local cur_domain_home=""
  local eff_domain_home=""
  local found_configs=""

  if [ ! "${DOMAIN_HOME?}" = "/u01/oracle/user_projects/domains" ]; then
    # nothing to do
    return 0
  fi

  local tfile=$(mktemp /tmp/homes.`basename $0`.XXXXXXXXX)
  rm -f $tfile
  ls -d ${DOMAIN_HOME} ${DOMAIN_HOME}/* > $tfile
  exec 22<> $tfile

  while read -u 22 cur_domain_home; do

    config_path="${cur_domain_home}/config/config.xml"

    if [ ! -f "${config_path}" ]; then
      continue
    fi

    count=$((count + 1))

    if [ $count -eq 1 ]; then
      eff_domain_home="${cur_domain_home}"
      found_configs="'${config_path}'"
    else
      found_configs="${found_configs}, '${config_path}'"
    fi

  done

  rm -f $tfile

  if [ $count -eq 1 ]; then
    export DOMAIN_HOME="${eff_domain_home}"
    return 0
  fi

  if [ $count -eq 0 ]; then
    trace "Error: No config.xml found at DOMAIN_HOME/config/config.xml or DOMAIN_HOME/*/config/config.xml, DOMAIN_HOME='$DOMAIN_HOME'. Check your 'domainHome' setting in your WebLogic Operator Domain resource, and your pv/pvc mount location (if any)."
    return 1
  fi
    
  # if we get this far, count is > 1 
  trace "Error: More than one config.xml found at DOMAIN_HOME/config/config.xml and DOMAIN_HOME/*/config/config.xml, DOMAIN_HOME='$DOMAIN_HOME': ${found_configs}. Configure your 'domainHome' setting in your WebLogic Operator Domain resource to reference a single WebLogic domain."
  return 1
}
