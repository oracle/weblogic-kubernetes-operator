#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script introspects a WebLogic DOMAIN_HOME in order to generate:
#
#   - a description of the domain for the operator (domain name, cluster name, ports, etc)
#   - encrypted login files for accessing a NM
#   - encrypted boot.ini for booting WL 
#   - encrypted admin user password passed in via a plain-text secret (for use in sit config)
#   - md5 checksum of the DOMAIN_HOME/security/SerializedSystemIni.dat domain secret file
#   - situational config files for overriding the configuration within the DOMAIN_HOME
#   - Model in Image domain home zips and md5s (when the domain source type is MII)
# 
# It works as part of the following flow:
#
#   (1) When an operator discovers a new domain, it launches this script via an
#       introspector k8s job.
#   (2) This script then:
#       (2A) Generates MII domain home zips/md5 files if the domain home source type is MII
#       (2B) Configures and starts a NM via startNodeManager.sh (in NODEMGR_HOME)
#       (2C) Calls introspectDomain.py, which depends on the NM and puts files in stdout
#       (2D) Exits 0 on success, non-zero otherwise.
#   (5) Operator parses the output of introspectDomain.py into files and:
#       (5A) Uses one of the files to get the domain's name, cluster name, ports, etc.
#       (5B) Deploys a config map for the domain containing the files.
#   (6) Operator starts pods for domain's WebLogic servers.
#   (7) Pod 'startServer.sh' script loads files from the config map, 
#       generates a domain home from the files for the MII case,
#       copies/uses encrypted files, and applies sit config files. It
#       also checks that domain secret md5 cksum matches the cksum
#       obtained by this script.
#
# Prerequisites:
#
#    - Optionally set
#        ORACLE_HOME = Oracle Install Home - defaults via utils.sh/exportInstallHomes
#        MW_HOME     = MiddleWare Install Home - defaults to ${ORACLE_HOME}
#        WL_HOME     = WebLogic Install Home - defaults to ${ORACLE_HOME}/wlserver
#        INTROSPECTOR_LOG_FILE_MAX = Max number of log files to keep around (default 11).
#
#    - Transitively requires other env vars for startNodeManager.sh, wlst.sh, modelInImage.sh,
#      and introspectDomain.py (see these scripts to find out what else needs to be set).
#


SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

#
# setup tracing
#

source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

traceTiming "INTROSPECTOR '${DOMAIN_UID}' MAIN START"

#
# Local createFolder method which does an 'exit 1' instead of exitOrLoop for
# immediate failure during introspection
#

function createFolder {
  mkdir -m 750 -p $1
  if [ ! -d $1 ]; then
    trace SEVERE "Unable to create folder $1"
    exit 1
  fi
}

#
# setup MII functions in case this is a MII domain
#

source ${SCRIPTPATH}/modelInImage.sh
[ $? -ne 0 ] && trace SEVERE "Error sourcing ${SCRIPTPATH}/modelInImage.sh" && exit 1

#
# setup introspector log file
#   keep max 11 total by default (delete oldest first)
#

traceDirs before LOG_HOME

if [ ! -z "${LOG_HOME}" ] && [ ! -d "${LOG_HOME}" ]; then
  trace "Creating log home directory: '${LOG_HOME}'"
  createFolder ${LOG_HOME}
fi

ilog_dir="${LOG_HOME:-/tmp}"
ilog_file="${ilog_dir}/introspector_script.out"

if [ ! -d "${ilog_dir}" ]; then
  trace "Creating introspector log directory: '${ilog_dir}'"
  createFolder "${ilog_dir}"
fi

testLogFileRotate "${ilog_file}"
[ $? -ne 0 ] && trace SEVERE "Error accessing '${ilog_dir}'. See previous log messages." && exit 1

logFileRotate ${ilog_file} ${INTROSPECTOR_LOG_FILE_MAX:-11}

#
# main introspection function
#

function doIntrospect() {

  trace "Introspecting domain '${DOMAIN_UID}', log location: '$ilog_file'"

  traceDirs after LOG_HOME

  # list potentially interesting env-vars and dirs before they're updated by export.*Homes

  traceEnv before
  traceDirs before DOMAIN_HOME DATA_HOME

  # set defaults
  # set ORACLE_HOME/WL_HOME/MW_HOME to defaults if needed

  exportInstallHomes

  # check if prereq env-vars, files, and directories exist

  checkEnv -q \
         DOMAIN_UID \
         NAMESPACE \
         ORACLE_HOME \
         JAVA_HOME \
         NODEMGR_HOME \
         WL_HOME \
         MW_HOME \
         OPERATOR_ENVVAR_NAMES \
         || exit 1

  for script_file in "${SCRIPTPATH}/wlst.sh" \
                     "${SCRIPTPATH}/startNodeManager.sh"  \
                     "${SCRIPTPATH}/introspectDomain.py"; do
    [ ! -f "$script_file" ] && trace SEVERE "Missing file '${script_file}'." && exit 1 
  done 

  for dir_var in JAVA_HOME WL_HOME MW_HOME ORACLE_HOME; do
    [ ! -d "${!dir_var}" ] && trace SEVERE "Missing ${dir_var} directory '${!dir_var}'." && exit 1
  done

  #
  # DATA_HOME env variable exists implies override directory specified.  Attempt to create directory
  #
  if [ ! -z "${DATA_HOME}" ] && [ ! -d "${DATA_HOME}" ]; then
    trace "Creating data home directory: '${DATA_HOME}'"
    createFolder ${DATA_HOME}
  fi

  traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII CREATE DOMAIN START"

  # Add another env/attribute in domain yaml for model in image
  # log error if dir exists and attribute set
  DOMAIN_CREATED=0
  if [ ${DOMAIN_SOURCE_TYPE} == "FromModel" ]; then
    trace "Beginning Model In Image"
    command -v gzip
    if [ $? -ne 0 ] ; then
      trace SEVERE "DomainSourceType is 'FromModel', 'gzip' is missing in the image. Please use an image with 'gzip' installed" && exit 1
    fi
    command -v tar
    if [ $? -ne 0 ] ; then
      trace SEVERE "DomainSourceType is 'FromModel', 'tar' is missing in the image. Please use an image with 'tar' installed" && exit 1
    fi
    command -v unzip
    if [ $? -ne 0 ] ; then
      trace SEVERE "DomainSourceType is 'FromModel', 'unzip' is missing in the image. Please use an image with 'unzip' installed" && exit 1
    fi
    mkdir -p ${DOMAIN_HOME}
    if [ $? -ne 0 ] ; then
      trace SEVERE "DomainSourceType is 'FromModel', cannot create domain home directory '${DOMAIN_HOME}'" && exit 1
    fi
    touch ${DOMAIN_HOME}/testaccess.tmp
    if [ $? -ne 0 ]; then
      trace SEVERE "DomainSourceType is 'FromModel', cannot write to domain home directory '${DOMAIN_HOME}'" && exit 1
    fi
    rm -f ${DOMAIN_HOME}/testaccess.tmp
    createWLDomain || exit 1
    created_domain=$DOMAIN_CREATED
    trace "Create domain return code = " ${created_domain}
  else
    created_domain=1
  fi

  traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII CREATE DOMAIN END" 

  # check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed

  exportEffectiveDomainHome || exit 1

  # list potentially interesting env-vars and dirs after they're updated by export.*Homes

  traceEnv after
  traceDirs after DOMAIN_HOME DATA_HOME

  # check if we're using a supported WebLogic version
  # (the check  will log a message if it fails)

  checkWebLogicVersion || exit 1

  # start node manager
  # run instrospector wlst script
  if [ ${created_domain} -ne 0 ]; then
    traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII NM START" 

    # introspectDomain.py uses an NM to setup credentials for the server NMs
    #  (see 'nmConnect' in introspectDomain.py)
    trace "Starting node manager"
    ${SCRIPTPATH}/startNodeManager.sh || exit 1

    traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII NM END" 
    traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII MD5 START"
    traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII NM END" 
    traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII MD5 START"

    # put domain secret's md5 cksum in file '/tmp/DomainSecret.md5'
    # the introspector wlst script and WL server pods will use this value
    generateDomainSecretMD5File '/tmp/DomainSecret.md5' || exit 1

    traceTiming "INTROSPECTOR '${DOMAIN_UID}' MII MD5 END"
    traceTiming "INTROSPECTOR '${DOMAIN_UID}' INTROSPECT START"

    trace "Running introspector WLST script ${SCRIPTPATH}/introspectDomain.py"
    ${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/introspectDomain.py || exit 1

    traceTiming "INTROSPECTOR '${DOMAIN_UID}' INTROSPECT END"
  fi
  trace "Domain introspection complete"
}

# we have different log file modes in case we need to revert 'tee' mode

case "${INTROSPECTOR_LOG_FILE_MODE:-tee}" in
  tee)
    set -o pipefail
    doIntrospect |& tee $ilog_file
    exit $?
    ;;
  bg_and_tail)
    ${SCRIPTPATH}/tailLog.sh $ilog_file /tmp/dontcare &
    tail_log_pid=$!
    doIntrospect >> $ilog_file 2>&1 &
    wait $!
    exitCode=$?
    # sleep 1 second in case background 'tail' needs time to catch up
    sleep 1
    kill -9 $tail_log_pid
    exit $exitCode
    ;;
  *)
    # no log file - everything simply goes to stdout/stderr (old behavior)
    doIntrospect
    exit $?
    ;;
esac
