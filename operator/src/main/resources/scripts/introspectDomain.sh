#!/bin/bash
# Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
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
# 
# It works as part of the following flow:
#
#   (1) When an operator discovers a new domain, it launches this script via an
#       introspector k8s job.
#   (2) This script then:
#       (2A) Configures and starts a NM via startNodeManager.sh (in NODEMGR_HOME)
#       (2B) Calls introspectDomain.py, which depends on the NM
#       (2C) Exits 0 on success, non-zero otherwise.
#   (5) Operator parses the output of introspectDomain.py into files and:
#       (5A) Uses one of the files to get the domain's name, cluster name, ports, etc.
#       (5B) Deploys a config map for the domain containing the files.
#   (6) Operator starts pods for domain's WebLogic servers.
#   (7) Pod 'startServer.sh' script loads files from the config map, 
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
#
#    - Transitively requires other env vars for startNodeManager.sh, wlst.sh,
#      and introspectDomain.py (see these scripts to find out what else needs to be set).
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

# setup tracing

source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

# Local createFolder method which does an 'exit 1' instead of exitOrLoop for
# immediate failure during introspection
function createFolder {
  mkdir -m 750 -p $1
  if [ ! -d $1 ]; then
    trace SEVERE "Unable to create folder $1"
    exit 1
  fi
}

trace "Introspecting the domain"

# list potentially interesting env-vars and dirs before they're updated by export.*Homes

traceEnv before
traceDirs before

# set defaults
# set ORACLE_HOME/WL_HOME/MW_HOME to defaults if needed

exportInstallHomes

# check if prereq env-vars, files, and directories exist

checkEnv -q \
         DOMAIN_UID \
         NAMESPACE \
         DOMAIN_HOME \
         ORACLE_HOME \
         JAVA_HOME \
         NODEMGR_HOME \
         WL_HOME \
         MW_HOME \
         || exit 1

for script_file in "${SCRIPTPATH}/wlst.sh" \
                   "${SCRIPTPATH}/startNodeManager.sh"  \
                   "${SCRIPTPATH}/introspectDomain.py"; do
  [ ! -f "$script_file" ] && trace SEVERE "Missing file '${script_file}'." && exit 1 
done 

for dir_var in DOMAIN_HOME JAVA_HOME WL_HOME MW_HOME ORACLE_HOME; do
  [ ! -d "${!dir_var}" ] && trace SEVERE "Missing ${dir_var} directory '${!dir_var}'." && exit 1
done

#
# DATA_HOME env variable exists implies override directory specified.  Attempt to create directory
#
if [ ! -z "${DATA_HOME}" ] && [ ! -d "${DATA_HOME}" ]; then
  trace "Creating data home directory: '${DATA_HOME}'"
  createFolder ${DATA_HOME}
fi


# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed

exportEffectiveDomainHome || exit 1

# list potentially interesting env-vars and dirs after they're updated by export.*Homes

traceEnv after
traceDirs after

# check if we're using a supported WebLogic version
# (the check  will log a message if it fails)

checkWebLogicVersion || exit 1

# start node manager

trace "Starting node manager"

${SCRIPTPATH}/startNodeManager.sh || exit 1

# put domain secret's md5 cksum in file '/tmp/DomainSecret.md5'
# the introspector wlst script and WL server pods will use this value

generateDomainSecretMD5File '/tmp/DomainSecret.md5' || exit 1

# run instrospector wlst script

trace "Running introspector WLST script ${SCRIPTPATH}/introspectDomain.py"

${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/introspectDomain.py || exit 1

trace "Domain introspection complete"

exit 0
