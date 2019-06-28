#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This script introspects a WebLogic DOMAIN_HOME in order to generate:
#
#   - a description of the domain for the operator (domain name, cluster name, ports, etc)
#   - encrypted login files for accessing a NM
#   - encrypted boot.ini for booting WL 
#   - encrypted admin user password passed in via a plain-text secret (for use in sit config)
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
#       (5A) Uses one to get the domain's name, cluster name, ports, etc.
#       (5B) Deploys a config map for the domain containing the files.
#   (6) Operator starts pods for domain's WebLogic servers.
#   (7) Pod 'startServer.sh' script loads files from the config map, 
#       copies/uses encrypted files, and applies sit config files.
#
# Prerequisites:
#
#    - Optionally set WL_HOME - default is /u01/oracle/wlserver.
#
#    - Optionally set MW_HOME - default is /u01/oracle.
#
#    - Transitively requires other env vars for startNodeManager.sh, wlst.sh,
#      and introspectDomain.py (see these scripts to find out what else needs to be set).
#


function createWLDomain() {
    wdt_config_root="/weblogic-operator/wdt-config-map"
    model_home="/u01/model_home"
    model_root="${model_home}/models"
    archive_root="${model_home}/archives"
    variable_root="${model_home}/variables"
    model_list=""
    archive_list=""
    variable_list=""


    ls ${wdt_config_root}/
    cp ${wdt_config_root}/*.properties ${variable_root}
    cp ${wdt_config_root}/*.yaml ${model_root}


    for file in $(ls ${model_root}/*.yaml | sort)
        do
            if [ "$model_list" != "" ]; then
                model_list="${model_list},"
            fi
            model_list="${model_list}${file}"
        done
    for file in $(ls ${archive_root}/*.zip | sort)
        do
            if [ "$archive_list" != "" ]; then
                archive_list="${archive_list},"
            fi
            archive_list="${archive_list}${file}"
        done

    for file in $(ls ${variable_root}/*.properties | sort)
        do
            if [ "$variable_list" != "" ]; then
                variable_list="${variable_list},"
            fi
            variable_list="${variable_list}${file}"
        done

    if [ "$variable_list" != "" ]; then
        variable_list="-variable_file ${variable_list}"
    fi

    if [ "$archive_list" != "" ]; then
        archive_list="-archive_file ${archive_list}"
    fi

    if [ "$model_list" != "" ]; then
        model_list="-model_file ${model_list}"
    fi

    /u01/weblogic-deploy/bin/createDomain.sh -oracle_home $MW_HOME -domain_home $DOMAIN_HOME $model_list $archive_list $variable_list
    ret=$?
    if [ $ret -ne 0 ]; then
       echo "Create Domain Failed"
       exit 1
    fi

    #cp ${wdt_config_root}/* ${DOMAIN_HOME}/
}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

# setup tracing

source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1 

trace "Introspecting the domain"

trace "Current environment:"
env

# set defaults

export WL_HOME=${WL_HOME:-/u01/oracle/wlserver}
export MW_HOME=${MW_HOME:-/u01/oracle}

# check if prereq env-vars, files, and directories exist

checkEnv DOMAIN_UID \
         NAMESPACE \
         DOMAIN_HOME \
         JAVA_HOME \
         NODEMGR_HOME \
         WL_HOME \
         MW_HOME \
         || exit 1

for script_file in "${SCRIPTPATH}/wlst.sh" \
                   "${SCRIPTPATH}/startNodeManager.sh"  \
                   "${SCRIPTPATH}/introspectDomain.py"; do
  [ ! -f "$script_file" ] && trace "Error: missing file '${script_file}'." && exit 1 
done 

for dir_var in JAVA_HOME WL_HOME MW_HOME; do
  [ ! -d "${!dir_var}" ] && trace "Error: missing ${dir_var} directory '${!dir_var}'." && exit 1
done

if [ ! -d "$DOMAIN_HOME" ]; then
    mkdir -p $DOMAIN_HOME
    createWLDomain
fi


# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed

exportEffectiveDomainHome || exit 1

# start node manager -why ??

trace "Starting node manager"

${SCRIPTPATH}/startNodeManager.sh || exit 1

# run instrospector wlst script

trace "Running introspector WLST script ${SCRIPTPATH}/introspectDomain.py"


${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/introspectDomain.py || exit 1

trace "Domain introspection complete"

exit 0
