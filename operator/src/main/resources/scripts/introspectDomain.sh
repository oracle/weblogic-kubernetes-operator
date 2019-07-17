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
        echo $k ' - ' ${sequence_array["$k"]}
    done |
    sort -n -k3  | cut -d' ' -f 1  
    shopt -u nullglob
}

function checkExistInventory() {
    has_md5=0

    echo "Checking model in image"
    if [ -f ${inventory_image_md5} ] ; then
        source -- ${inventory_image_md5}
        has_md5=1
        if [ ${#introspect_image[@]} -ne ${#inventory_image[@]} ]; then
            echo "Contents in model home changed: create domain again"
            return 1
        fi
        for K in "${!inventory_image[@]}"; do
            extension="${K##*.}"
            if [ "$extension" == "yaml" -o "$extension" == "properties" -o "$extension" == "zip" ]; then
                if [ ! "${inventory_image[$K]}" == "${introspect_image[$K]}" ]; then
                    echo "md5 not equal: create domain" $K
                    return 1
                fi
            fi
        done
    fi

    echo "Checking images in config map"
    if [ -f ${inventory_cm_md5} ] ; then
        source -- ${inventory_cm_md5}
        has_md5=1
        if [ ${#introspect_cm[@]} -ne ${#inventory_cm[@]} ]; then
            echo "Contents of config map changed: create domain again"
            return 1
        fi
        for K in "${!inventory_cm[@]}"; do
            extension="${K##*.}"
            if [ "$extension" == "yaml" -o "$extension" == "properties" ]; then
                if [ ! "${inventory_cm[$K]}" == "${introspect_cm[$K]}" ]; then
                    echo "md5 not equal: create domain" $K
                    return 1
                fi
            fi
         done
    else
        # if no config map before but adding one now
        if [ ${#inventory_cm[@]} -ne 0 ]; then
            echo "New inventory in cm: create domain"
            return 1
        fi
    fi
    echo "Checking passphrase"
    if [ -f ${inventory_passphrase_md5} ] ; then
        has_md5=1
        source -- ${inventory_passphrase_md5}
        found_wdt_pwd=$(find ${wdt_secret_path} -name wdtpassword -type f)
        if [ -f "${found_wdt_pwd}" ] ; then
            target_md5=$(md5sum $found_wdt_pwd | cut -d' ' -f1)
        fi
        for K in "${!inventory_passphrase[@]}"; do
            if [ ! "$target_md5" == "${inventory_passphrase[$K]}" ]; then
                echo "passphrase changed: recreate domain " $target_md5 ${inventory_passphrase[$K]}
                return 1
            fi
        done
    else
        if [ ${#inventory_passphrase[@]} -ne 0 ]; then
            echo "new passphrase: recreate domain"
            return 1
        fi
    fi

    if [ $has_md5 -eq 0 ]; then
        echo "no md5 found: create domain"
        return 1
    fi
    return 0

}

function createWLDomain() {

    model_list=""
    archive_list=""
    variable_list="${model_home}/variables/_k8s_generated_props.properties"

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
                echo "More than one archive file"
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
    checkExistInventory
    create_domain=$?
    if  [ ${create_domain} -ne 0 ] ; then

        echo "NEED TO CREATE DOMAIN"
        if [ $use_passphrase -eq 1 ]; then
            yes ${wdt_passphrase} | /u01/weblogic-deploy/bin/createDomain.sh -oracle_home $MW_HOME -domain_home $DOMAIN_HOME $model_list $archive_list $variable_list -use_encryption
        else
            /u01/weblogic-deploy/bin/createDomain.sh -oracle_home $MW_HOME -domain_home $DOMAIN_HOME $model_list $archive_list $variable_list
        fi
        ret=$?
        if [ $ret -ne 0 ]; then
            echo "Create Domain Failed"
            exit 1
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
wdt_config_root="/weblogic-operator/wdt-config-map"
wdt_secret_path="/weblogic-operator/wdt-config-map-secrets"
model_home="/u01/model_home"
model_root="${model_home}/models"
archive_root="${model_home}/archives"
variable_root="${model_home}/variables"
operator_md5=$DOMAIN_HOME/operatormd5


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
    created_domain=$?
    echo "created domain " ${created_domain}
else
    created_domain=1
fi


# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed

exportEffectiveDomainHome || exit 1



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
