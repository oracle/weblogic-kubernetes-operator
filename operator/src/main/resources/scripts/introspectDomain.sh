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
#    - Optionally set
#        ORACLE_HOME = Oracle Install Home - defaults via utils.sh/exportInstallHomes
#        MW_HOME     = MiddleWare Install Home - defaults to ${ORACLE_HOME}
#        WL_HOME     = WebLogic Install Home - defaults to ${ORACLE_HOME}/wlserver
#
#    - Transitively requires other env vars for startNodeManager.sh, wlst.sh,
#      and introspectDomain.py (see these scripts to find out what else needs to be set).
#

function sort_files() {
    shopt -s nullglob
    root_dir=$1
    ext=$2
    declare -A sequence_array
    for file in ${root_dir}/*${ext} ;
      do
        actual_filename=$(basename $file)
        base_filename=$(basename ${file%.*})
        sequence="${base_filename##*.}"
        sequence_array[${actual_filename}]=${sequence}
      done
    for k in "${!sequence_array[@]}"
    do
        # MUST use echo , caller depends on stdout
        echo $k ' - ' ${sequence_array["$k"]}
    done |
    sort -n -k3  | cut -d' ' -f 1
    shopt -u nullglob
}

function checkExistInventory() {
    has_md5=0

    trace "Checking model in image"
    if [ -f ${inventory_image_md5} ] ; then
        source -- ${inventory_image_md5}
        has_md5=1
        if [ ${#introspect_image[@]} -ne ${#inventory_image[@]} ]; then
            trace "Contents in model home changed: create domain again"
            return 1
        fi
        for K in "${!inventory_image[@]}"; do
            extension="${K##*.}"
            if [ "$extension" == "yaml" -o "$extension" == "properties" -o "$extension" == "zip" ]; then
                if [ ! "${inventory_image[$K]}" == "${introspect_image[$K]}" ]; then
                    trace "md5 not equal: create domain" $K
                    archive_zip_changed=1
                    return 1
                fi
            fi
        done
    fi

    trace "Checking images in config map"
    if [ -f ${inventory_cm_md5} ] ; then
        source -- ${inventory_cm_md5}
        has_md5=1
        if [ ${#introspect_cm[@]} -ne ${#inventory_cm[@]} ]; then
            trace "Contents of config map changed: create domain again"
            return 1
        fi
        for K in "${!inventory_cm[@]}"; do
            extension="${K##*.}"
            if [ "$extension" == "yaml" -o "$extension" == "properties" ]; then
                if [ ! "${inventory_cm[$K]}" == "${introspect_cm[$K]}" ]; then
                    trace "md5 not equal: create domain" $K
                    return 1
                fi
            fi
         done
    else
        # if no config map before but adding one now
        if [ ${#inventory_cm[@]} -ne 0 ]; then
            trace "New inventory in cm: create domain"
            return 1
        fi
    fi
    trace "Checking passphrase"
    if [ -f ${inventory_passphrase_md5} ] ; then
        has_md5=1
        source -- ${inventory_passphrase_md5}
        found_wdt_pwd=$(find ${wdt_secret_path} -name wdtpassword -type f)
        if [ -f "${found_wdt_pwd}" ] ; then
            target_md5=$(md5sum $found_wdt_pwd | cut -d' ' -f1)
        fi
        for K in "${!inventory_passphrase[@]}"; do
            if [ ! "$target_md5" == "${inventory_passphrase[$K]}" ]; then
                trace "passphrase changed: recreate domain " $target_md5 ${inventory_passphrase[$K]}
                return 1
            fi
        done
    else
        if [ ${#inventory_passphrase[@]} -ne 0 ]; then
            trace "new passphrase: recreate domain"
            return 1
        fi
    fi

    if [ $has_md5 -eq 0 ]; then
        trace "no md5 found: create domain"
        return 1
    fi
    return 0

}

function createWLDomain() {

    model_list=""
    archive_list=""
    variable_list="${model_home}/_k8s_generated_props.properties"

    # in case retry
    if [ -f ${variable_list} ] ; then
        cat /dev/null > ${variable_list}
    fi

    for file in $(sort_files $model_root ".yaml")
        do
            inventory_image[$file]=$(md5sum ${model_root}/${file} | cut -d' ' -f1)
            if [ "$model_list" != "" ]; then
                model_list="${model_list},"
            fi
            model_list="${model_list}${model_root}/${file}"
        done

    for file in $(sort_files $wdt_config_root ".yaml")
        do
            inventory_cm[$file]=$(md5sum ${wdt_config_root}/$file | cut -d' ' -f1)
            if [ "$model_list" != "" ]; then
                model_list="${model_list},"
            fi
            model_list="${model_list}${wdt_config_root}/${file}"
        done

    # Should only have one !!

    for file in $(ls ${archive_root}/*.zip | sort)
        do
            if [ "$archive_list" != "" ]; then
                archive_list="${archive_list},"
                trace "More than one archive file"
                exit 1
            fi
            inventory_image[$file]=$(md5sum $file | cut -d' ' -f1)
            archive_list="${archive_list}${file}"
        done

    # Merge all properties together

    for file in $(sort_files ${variable_root} ".properties")
        do
            inventory_image[$file]=$(md5sum ${variable_root}/$file | cut -d' ' -f1)
            cat ${variable_root}/${file} >> ${variable_list}
        done

    for file in $(sort_files ${wdt_config_root} ".properties")
        do
            inventory_cm[$file]=$(md5sum  ${wdt_config_root}/$file | cut -d' ' -f1)
            cat ${wdt_config_root}/${file} >> ${variable_list}
        done

    if [ -f ${variable_list} ]; then
        variable_list="-variable_file ${variable_list}"
    else
        variable_list=""
    fi

    if [ "$archive_list" != "" ]; then
        archive_list="-archive_file ${archive_list}"
    fi

    if [ "$model_list" != "" ]; then
        model_list="-model_file ${model_list}"
    fi

    use_encryption=""
    use_passphrase=0
    found_wdt_pwd=$(find ${wdt_secret_path} -name wdtpassword -type f)
    if [ -f "${found_wdt_pwd}" ] ; then
        inventory_passphrase[wdtpassword]=$(md5sum $found_wdt_pwd | cut -d' ' -f1)
        wdt_passphrase=$(cat ${found_wdt_pwd})
        use_passphrase=1
    fi

    found_opss_passphrase=$(find ${wdt_secret_path} -name opsspassphrase -type f)
    if [ -f "${found_opss_passphrase}" ] ; then
        export OPSS_PASSPHRASE=$(cat ${found_opss_passphrase})
    fi
    # just in case is not set
    if [ -z "${OPSS_PASSPHRASE}" ] ; then
        export OPSS_PASSPHRASE=${DOMAIN_UID}_welcome1
    fi

    # check to see if any model including changed (or first model in image deploy)
    # if yes. then run create domain again

    checkExistInventory
    create_domain=$?
    # something changed in the wdt artifacts
    if  [ ${create_domain} -ne 0 ] ; then

        trace "Need to create domain ${WDT_DOMAIN_TYPE}"
        export __WLSDEPLOY_STORE_MODEL__=1

        # We need to run wdt create to get a new merged model
        # otherwise for the update case we won't have one to compare with

        if [ -z "${WDT_DOMAIN_TYPE}" ] ; then
            WDT_DOMAIN_TYPE=WLS
        fi
        trace "Run wdt create domain ${WDT_DOMAIN_TYPE}"

        #  We cannot strictly run create domain for JRF type because it's tied to a database schema
        #  We shouldn't require user to drop the db first since it may have data in it
        #  Can we safely switch to use WLS as type.
        if [ -f "${opss_wallet}" ] ; then
            if [ ! -z ${KEEP_JRF_SCHEMA} ] && [ ${KEEP_JRF_SCHEMA} -eq 1 ] ; then
               trace "keeping rcu schema"
               mkdir -p /tmp/opsswallet
               base64 -d  ${opss_wallet} > /tmp/opsswallet/ewallet.p12
               OPSS_FLAGS="-opss_wallet /tmp/opsswallet -opss_wallet_passphrase ${OPSS_PASSPHRASE}"
            fi
        else
            OPSS_FLAGS=""
        fi


        if [ $use_passphrase -eq 1 ]; then
            yes ${wdt_passphrase} | ${wdt_bin}/createDomain.sh -oracle_home $MW_HOME -domain_home \
            $DOMAIN_HOME $model_list $archive_list $variable_list -use_encryption -domain_type ${WDT_DOMAIN_TYPE} \
            ${OPSS_FLAGS}
        else
            ${wdt_bin}/createDomain.sh -oracle_home $MW_HOME -domain_home $DOMAIN_HOME $model_list \
            $archive_list $variable_list  -domain_type ${WDT_DOMAIN_TYPE} ${OPSS_FLAGS}
        fi
        ret=$?
        if [ $ret -ne 0 ]; then
            trace "Create Domain Failed"
            exit 1
        fi


        # if there is a merged model in the cm then it is an update case, try online update first
        if [ -f ${inventory_merged_model} ] && [ ${archive_zip_changed} -eq 0 ] ; then


            ${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/model_diff.py ${inventory_merged_model} $DOMAIN_HOME/wlsdeploy/domain_model.json || exit 1
            if [ $? -eq 0 ] ; then
                trace "Using online update"
                admin_user=$(cat /weblogic-operator/secrets/username)
                admin_pwd=$(cat /weblogic-operator/secrets/password)

                cat /tmp/diffed_model.json

                yes ${admin_pwd} | /u01/weblogic-deploy/bin/updateDomain.sh -oracle_home $MW_HOME \
                 -admin_url "t3://${AS_SERVICE_NAME}:${ADMIN_PORT}" -admin_user ${admin_user} -model_file \
                 /tmp/diffed_model.json $variable_list -domain_home $DOMAIN_HOME
                retcode=$?
                trace "Completed update"
                if [ ${retcode} -eq 103 ] ; then
                    trace ">>>  updatedomainResult=103"
                elif [ ${retcode} -eq 102 ] ; then
                    trace ">>>  updatedomainResult=102"
                elif [ ${retcode} -ne 0 ] ; then
                    trace ">>>  updatedomainResult=${retcode}"
                    exit 1
                else
                    trace ">>>  updatedomainResult=${retcode}"
                fi
                trace "wrote updateResult"

                # if online update is successful, then we extract the old domain and use offline update, so that
                # we can update the domain

                rm -fr $DOMAIN_HOME
                cd / && base64 -d /weblogic-operator/introspectormd5/domainzip.secure > /tmp/domain.zip && unzip \
                /tmp/domain.zip
                  # zip does not store external attributes - should we use find ?
                #chmod +x $DOMAIN_HOME/bin/*.sh $DOMAIN_HOME/*.sh

                #Perform an offline update so that we can have a new domain zip later

                /u01/weblogic-deploy/bin/updateDomain.sh -oracle_home $MW_HOME \
                 -model_file /tmp/diffed_model.json $variable_list -domain_home $DOMAIN_HOME

              # perform wdt online update if the user has specify in the spec ? How to get it from the spec ?  env ?
              # write something to the instrospec output so that the operator knows whether to restart the server
            fi
        fi

        # The reason for copying the associative array is because they cannot be passed to the function for checking
        # and the script source the persisted associative variable shell script to retrieve it back to a variable
        # we are comparing  inventory* (which is the current image md5 contents) vs introspect* (which is the previous
        # run stored in the config map )

        if [ "${#inventory_image[@]}" -ne "0" ] ; then
            declare -A introspect_image
            for K in "${!inventory_image[@]}"; do introspect_image[$K]=${inventory_image[$K]}; done
            declare -p introspect_image > /tmp/inventory_image.md5
        fi
        if [ "${#inventory_cm[@]}" -ne "0" ] ; then
            declare -A introspect_cm
            for K in "${!inventory_cm[@]}"; do introspect_cm[$K]=${inventory_cm[$K]}; done
            declare -p introspect_cm > /tmp/inventory_cm.md5
        fi
        if [ "${#inventory_passphrase[@]}" -ne "0" ] ; then
            declare -A introspect_passphrase
            for K in "${!inventory_passphrase[@]}"; do introspect_passphrase[$K]=${inventory_passphrase[$K]}; done
            declare -p introspect_passphrase > /tmp/inventory_passphrase.md5
        fi

    fi
    return ${create_domain}
}


declare -A inventory_image
declare -A inventory_cm
declare -A inventory_passphrase
inventory_image_md5="/weblogic-operator/introspectormd5/inventory_image.md5"
inventory_cm_md5="/weblogic-operator/introspectormd5/inventory_cm.md5"
inventory_passphrase_md5="/weblogic-operator/introspectormd5/inventory_passphrase.md5"
inventory_merged_model="/weblogic-operator/introspectormd5/merged_model.json"
opss_wallet="/weblogic-operator/introspectormd5/ewallet.p12"
domain_zipped="/weblogic-operator/introspectormd5/domainzip.secure"
wdt_config_root="/weblogic-operator/wdt-config-map"
wdt_secret_path="/weblogic-operator/wdt-config-map-secrets"
model_home="/u01/wdt/models"
model_root="${model_home}"
archive_root="${model_home}"
variable_root="${model_home}"
wdt_bin="/u01/wdt/weblogic-deploy/bin"
operator_md5=$DOMAIN_HOME/operatormd5
archive_zip_changed=0


SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

# setup tracing

source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

trace "Introspecting the domain"

env | tracePipe "Current environment:"

# set ORACLE_HOME/WL_HOME/MW_HOME to defaults if needed

exportInstallHomes

# check if prereq env-vars, files, and directories exist

checkEnv DOMAIN_UID \
         NAMESPACE \
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

for dir_var in JAVA_HOME WL_HOME MW_HOME ORACLE_HOME; do
  [ ! -d "${!dir_var}" ] && trace SEVERE "Missing ${dir_var} directory '${!dir_var}'." && exit 1
done

if [ ! -d "$DOMAIN_HOME" ]; then
    mkdir -p $DOMAIN_HOME
    createWLDomain
    created_domain=$?
    trace "created domain " ${created_domain}
else
    created_domain=1
fi


# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed

exportEffectiveDomainHome || exit 1

# check if we're using a supported WebLogic version
# (the check  will log a message if it fails)

checkWebLogicVersion || exit 1

# start node manager

# run instrospector wlst script

if [ ${created_domain} -ne 0 ]; then
    # start node manager -why ??
    trace "Starting node manager"
    ${SCRIPTPATH}/startNodeManager.sh || exit 1
    trace "Running introspector WLST script ${SCRIPTPATH}/introspectDomain.py"
    ${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/introspectDomain.py || exit 1
fi
trace "Domain introspection complete"

exit 0
