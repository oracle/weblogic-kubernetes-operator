#!/usr/bin/env bash
# Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script contains the all the function of model in image
# It is used by introspectDomain.sh job and starServer.sh

source ${SCRIPTPATH}/utils.sh


INTROSPECTCM_IMAGE_MD5="/weblogic-operator/introspectormd5/inventory_image.md5"
INTROSPECTCM_CM_MD5="/weblogic-operator/introspectormd5/inventory_cm.md5"
INTROSPECTCM_PASSPHRASE_MD5="/weblogic-operator/introspectormd5/inventory_passphrase.md5"
INTROSPECTCM_MERGED_MODEL="/weblogic-operator/introspectormd5/merged_model.json"
INTROSPECTCM_WLS_VERSION="/weblogic-operator/introspectormd5/wls.version"
INTROSPECTCM_JDK_PATH="/weblogic-operator/introspectormd5/jdk.path"
INTROSPECTCM_SECRETS_MD5="/weblogic-operator/introspectormd5/secrets.md5"
DOMAIN_ZIPPED="/weblogic-operator/introspectormd5/domainzip.secure"
PRIMORDIAL_DOMAIN_ZIPPED="/weblogic-operator/introspectormd5/primordial_domainzip.secure"
INTROSPECTJOB_IMAGE_MD5="/tmp/inventory_image.md5"
INTROSPECTJOB_CM_MD5="/tmp/inventory_cm.md5"
INTROSPECTJOB_PASSPHRASE_MD5="/tmp/inventory_passphrase.md5"


WDT_CONFIGMAP_ROOT="/weblogic-operator/wdt-config-map"
WDT_ENCRYPTION_PASSPHRASE="/weblogic-operator/wdt-encrypt-key-passphrase/passphrase"
OPSS_KEY_PASSPHRASE="/weblogic-operator/opss-walletkey-secret/passphrase"
OPSS_KEY_B64EWALLET="/weblogic-operator/opss-walletfile-secret/ewallet.p12"
IMG_MODELS_HOME="/u01/wdt/models"
IMG_MODELS_ROOTDIR="${IMG_MODELS_HOME}"
IMG_ARCHIVES_ROOTDIR="${IMG_MODELS_HOME}"
IMG_VARIABLE_FILES_ROOTDIR="${IMG_MODELS_HOME}"
WDT_ROOT="/u01/wdt/weblogic-deploy"
WDT_OUTPUT="/tmp/wdt_output.log"
WDT_BINDIR="${WDT_ROOT}/bin"
WDT_FILTER_JSON="/weblogic-operator/scripts/model_filters.json"
WDT_CREATE_FILTER="/weblogic-operator/scripts/wdt_create_filter.py"

ARCHIVE_ZIP_CHANGED=0
WDT_ARTIFACTS_CHANGED=0
ROLLBACK_ERROR=3

# return codes for model_diff
UNSAFE_ONLINE_UPDATE=0
SAFE_ONLINE_UPDATE=1
FATAL_MODEL_CHANGES=2
MODELS_SAME=3
UNSAFE_SECURITY_UPDATE=4
SCRIPT_ERROR=255

operator_md5=${DOMAIN_HOME}/operatormd5

# sort_files  sort the files according to the names and naming conventions and write the result to stdout
#    $1  directory
#    $2  extension
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
  for k in "${!sequence_array[@]}" ;
    do
      # MUST use echo , caller depends on stdout
      echo $k ' - ' ${sequence_array["$k"]}
    done |
  sort -n -k3  | cut -d' ' -f 1
  shopt -u nullglob
}

#
# compareArtifactsMD5  checks the WDT artifacts MD5s in the introspect config map against the current introspect job
# WDT artifacts MD5s
#
# If there are any differences, set WDT_ARTIFACTS_CHANGED=1
# If there are any WDT archives changed set ARCHIVE_ZIP_CHANGED=1 (for online update) (TODO)
#

function compareArtifactsMD5() {
  trap 'error_handler There was an error at compareArtifactsMD5 line $LINENO' ERR

  local has_md5=0

  trace "Entering checkExistInventory"

  trace "Checking wdt artifacts in image"
  if [ -f ${INTROSPECTCM_IMAGE_MD5} ] ; then
    has_md5=1
    # introspectorDomain py put two blank lines in the configmap, use -B to ignore blank lines
    diff -B ${INTROSPECTCM_IMAGE_MD5} ${INTROSPECTJOB_IMAGE_MD5} > /tmp/imgmd5diff
    if [ $? -ne 0 ] ; then
      trace "WDT artifacts in image changed: create domain again"
      WDT_ARTIFACTS_CHANGED=1
      echoFilesDifferences ${INTROSPECTCM_IMAGE_MD5} ${INTROSPECTJOB_IMAGE_MD5}
    fi
  fi

  trace "Checking wdt artifacts in config map"
  if [ -f ${INTROSPECTCM_CM_MD5} ] ; then
    has_md5=1
    diff -B  ${INTROSPECTCM_CM_MD5} ${INTROSPECTJOB_CM_MD5}
    if [ $? -ne 0 ] ; then
      trace "WDT artifacts in wdt config map changed: create domain again"
      WDT_ARTIFACTS_CHANGED=1
      echoFilesDifferences ${INTROSPECTCM_CM_MD5} ${INTROSPECTJOB_CM_MD5}
    fi
  else
    # if no config map before but adding one now
    if [ -f ${INTROSPECTJOB_CM_MD5} ]; then
      trace "New inventory in cm: create domain"
      WDT_ARTIFACTS_CHANGED=1
    fi
  fi

  trace "Checking passphrase"
  if [ -f ${INTROSPECTCM_PASSPHRASE_MD5} ] ; then
    has_md5=1
    diff -B  ${INTROSPECTCM_PASSPHRASE_MD5} ${INTROSPECTJOB_PASSPHRASE_MD5}
    if [ $? -ne 0 ] ; then
      trace "WDT Encryption passphrase changed: create domain again"
      WDT_ARTIFACTS_CHANGED=1
    fi
  else
    if [ -f ${INTROSPECTJOB_PASSPHRASE_MD5} ]; then
      trace "new passphrase: recreate domain"
      WDT_ARTIFACTS_CHANGED=1
    fi
  fi

  if [ $has_md5 -eq 0 ]; then
    # Initial deployment
    trace "no md5 found: create domain"
    WDT_ARTIFACTS_CHANGED=1
  fi

  trace "Exiting checkExistInventory"
  trap - ERR
}

# echo file contents

function echoFilesDifferences() {
  trace "------- from introspector cm -----------------"
  cat $1
  trace "------- from introspector job pod ------------"
  cat $2
  trace "----------------------------------------------"
}

# get_opss_key_wallet   returns opss key wallet ewallet.p12 location
#
# if there is one from the user config map, use it first
# otherwise use the one in the introspect job config map
#

function get_opss_key_wallet() {
  if [ -f ${OPSS_KEY_B64EWALLET} ]; then
    echo ${OPSS_KEY_B64EWALLET}
  else
    echo "/weblogic-operator/introspectormd5/ewallet.p12"
  fi
}

#
# buildWDTParams_MD5   Setup the WDT artifacts MD5 for comparison between updates
#  Also setup the wdt parameters
#

function buildWDTParams_MD5() {

  trap 'error_handler There was an error at buildWDTParams_MD5 line $LINENO' ERR

  trace "Entering setupInventoryList"

  model_list=""
  archive_list=""
  variable_list="${IMG_MODELS_HOME}/_k8s_generated_props.properties"


  #
  # First build the command line parameters for WDT
  # based on the file listing in the image or config map
  #

  for file in $(sort_files $IMG_MODELS_ROOTDIR ".yaml") ;
    do
      md5sum ${IMG_MODELS_ROOTDIR}/${file} >> ${INTROSPECTJOB_IMAGE_MD5}
      if [ "$model_list" != "" ]; then
        model_list="${model_list},"
      fi
      model_list="${model_list}${IMG_MODELS_ROOTDIR}/${file}"
    done

  for file in $(sort_files $WDT_CONFIGMAP_ROOT ".yaml") ;
    do
      md5sum ${WDT_CONFIGMAP_ROOT}/$file >> ${INTROSPECTJOB_CM_MD5}
      if [ "$model_list" != "" ]; then
        model_list="${model_list},"
      fi
      model_list="${model_list}${WDT_CONFIGMAP_ROOT}/${file}"
    done

  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip") ;
    do
      md5sum ${IMG_ARCHIVES_ROOTDIR}/$file >> ${INTROSPECTJOB_IMAGE_MD5}
      if [ "$archive_list" != "" ]; then
        archive_list="${archive_list},"
      fi
      archive_list="${archive_list}${IMG_ARCHIVES_ROOTDIR}/${file}"
    done

  # Merge all properties together

  for file in $(sort_files ${IMG_VARIABLE_FILES_ROOTDIR} ".properties") ;
    do
      md5sum ${IMG_VARIABLE_FILES_ROOTDIR}/$file >> ${INTROSPECTJOB_IMAGE_MD5}
      cat ${IMG_VARIABLE_FILES_ROOTDIR}/${file} >> ${variable_list}
    done

  for file in $(sort_files ${WDT_CONFIGMAP_ROOT} ".properties") ;
    do
      md5sum  ${WDT_CONFIGMAP_ROOT}/$file >> ${INTROSPECTJOB_CM_MD5}
      cat ${WDT_CONFIGMAP_ROOT}/${file} >> ${variable_list}
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


  if [ -f "${WDT_ENCRYPTION_PASSPHRASE}" ] ; then
    #inventory_passphrase[wdtpassword]=$(md5sum $(wdt_encryption_passphrase) | cut -d' ' -f1)
    md5sum $(wdt_encryption_passphrase) >> ${INTROSPECTJOB_PASSPHRASE_MD5}
    WDT_PASSPHRASE=$(cat $(wdt_encryption_passphrase))
  fi


  if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
    if [ ! -f "${OPSS_KEY_PASSPHRASE}" ] ; then
      trace SEVERE "JRF domain requires k8s secrets opssKeyPassPhrase with key passphrase"
      exit 1
    else
      # Set it for introspectDomain.py to use
      export OPSS_PASSPHRASE=$(cat ${OPSS_KEY_PASSPHRASE})
    fi
  fi

  #  We cannot strictly run create domain for JRF type because it's tied to a database schema
  #  We shouldn't require user to drop the db first since it may have data in it
  #
  opss_wallet=$(get_opss_key_wallet)
  if [ -f "${opss_wallet}" ] ; then
      trace "keeping rcu schema"
      mkdir -p /tmp/opsswallet
      base64 -d  ${opss_wallet} > /tmp/opsswallet/ewallet.p12
      OPSS_FLAGS="-opss_wallet /tmp/opsswallet -opss_wallet_passphrase ${OPSS_PASSPHRASE}"
  else
    OPSS_FLAGS=""
  fi

  trace "Exiting setupInventoryList"
  trap - ERR
}

# createWLDomain
#
# 1. Create the parameter list for WDT
# 2. Check if any WDT artifacts changed
# 3. If nothing changed return 0
# 4. If something changed (or new) then use WDT createDomain.sh
# 5. With the new domain created, the generated merged model is compare with the previous one (if any)
# 6. If there are safe changes and  user select useOnlineUpdate, use wdt online update
# 6.1.   if online update failed then exit the introspect job
# 6.2    if online update succeeded and no restart is need then go to 7.1
# 6.3    if online update succeeded and restart is needed but user set rollbackIfRequireRestart then exit the job
# 6.4    go to 7.1.
# 7. else
# 7.1    unzip the old domain and use wdt offline updates


function createWLDomain() {
  trap 'error_handler There was an error at createWLDomain line $LINENO' ERR

  trace "Entering createWLDomain"

  # Check if /u01/wdt/models and /u01/wdt/weblogic-deploy exists

  checkDirNotExistsOrEmpty ${IMG_MODELS_HOME}
  checkDirNotExistsOrEmpty ${WDT_BINDIR}

  # copy the filter related files to the wdt lib

  cp ${WDT_FILTER_JSON} ${WDT_ROOT}/lib
  cp ${WDT_CREATE_FILTER} ${WDT_ROOT}/lib

  # check to see if any model including changed (or first model in image deploy)
  # if yes. then run create domain again


  local current_version=$(getWebLogicVersion)
  local current_jdkpath=$(readlink -f $JAVA_HOME)
  # check for version:  can only be rolling

  local version_changed=0
  local jdk_changed=0
  local secrets_changed=0
  trace "current version "${current_version}

  getSecretsMD5
  local current_secrets_md5=$(cat /tmp/secrets.md5)

  trace "Checking changes in secrets and jdk path"

  if [ -f ${INTROSPECTCM_SECRETS_MD5} ] ; then
    previous_secrets_md5=$(cat ${INTROSPECTCM_SECRETS_MD5})
    if [ "${current_secrets_md5}" != "${previous_secrets_md5}" ]; then
      trace "secrets different: before: ${previous_secrets_md5} current: ${current_secrets_md5}"
      secrets_changed=1
    fi
  fi

  # If No WDT artifacts changed but WLS version changed
#  if [ -f ${INTROSPECTCM_WLS_VERSION} ] ; then
#    previous_version=$(cat ${INTROSPECTCM_WLS_VERSION})
#    if [ "${current_version}" != "${previous_version}" ]; then
#      trace "version different: before: ${previous_version} current: ${current_version}"
#      version_changed=1
#    fi
#  fi

  if [ -f ${INTROSPECTCM_JDK_PATH} ] ; then
    previous_jdkpath=$(cat ${INTROSPECTCM_JDK_PATH})
    if [ "${current_jdkpath}" != "${previous_jdkpath}" ]; then
      trace "jdkpath different: before: ${previous_jdkpath} current: ${current_jdkpath}"
      jdk_changed=1
    fi
  fi

  # write out version, introspectDomain.py will write it to the configmap

  echo ${current_version} > /tmp/wls_version
  echo $(readlink -f $JAVA_HOME) > /tmp/jdk_path

  # setup wdt parameters and also associative array before calling comparing md5 in checkExistInventory
  #
  trace "Building WDT parameters and MD5s"

  buildWDTParams_MD5

  compareArtifactsMD5
  DOMAIN_CREATED=0

  # something changed in the wdt artifacts or wls version changed
  # create domain again

  if  [ ${WDT_ARTIFACTS_CHANGED} -ne 0 ] || [ ${jdk_changed} -eq 1 ] \
    || [ ${secrets_changed} -ne 0 ] ; then

    trace "Need to create domain ${WDT_DOMAIN_TYPE}"
    wdtCreateDomain
    DOMAIN_CREATED=1

    # For lifecycle updates:
    # 1. If there is a merged model in the cm and
    # 2. If the archive changed and
    # 3. If the useOnlineUpdate is define in the spec and set to true and
    # 4. not for version upgrade
    #
    # Experimental Phase 2 logic for handling dynamic online lifecycle update of weblogic configuration

    if [ -f ${INTROSPECTCM_MERGED_MODEL} ] && [ ${ARCHIVE_ZIP_CHANGED} -eq 0 ] && [ "true" == "${USE_ONLINE_UPDATE}" \
            ] && [ ${version_changed} -ne 1 ]; then

      ${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/model_diff.py ${DOMAIN_HOME}/wlsdeploy/domain_model.json \
          ${INTROSPECTCM_MERGED_MODEL}

      diff_rc=$(cat /tmp/model_diff_rc)
      # 0 not safe
      # 1 safe for online changes
      # 2 fatal
      # 3 no difference

      trace "model diff returns "${diff_rc}

      cat /tmp/diffed_model.json

      if [ ${diff_rc} -eq ${SCRIPT_ERROR} ]; then
        exit 1
      fi

      # Perform online changes
      if [ ${diff_rc} -eq ${SAFE_ONLINE_UPDATE} ] ; then
        trace "Using online update"
        handleOnlineUpdate
      fi

      # Changes are not supported - shape changes
      if [ ${diff_rc} -eq ${FATAL_MODEL_CHANGES} ] ; then
        trace "Introspect job terminated: Unsupported changes in the model is not supported"
        exit 1
      fi

      if [ ${diff_rc} -eq ${MODELS_SAME} ] ; then
        trace "Introspect job terminated: Nothing changed"
        return 0
      fi

      # Changes are not supported yet for online update - non shape changes.. deletion, deploy app.
      # app deployments may involve shared libraries, shared library impacted apps, although WDT online support
      # it but it has not been fully tested - forbid it for now.

      if [ ${diff_rc} -eq ${UNSAFE_ONLINE_UPDATE} ] ; then
        trace "Introspect job terminated: Changes are not safe to do online updates. Use offline changes. See introspect job logs for
        details"
        exit 1
      fi
    fi

  fi
  trace "Exiting createWLDomain"
  trap - ERR
}

# checkDirNotExistsOrEmpty
#  Test directory exists or empty

function checkDirNotExistsOrEmpty() {
  trap 'error_handler There was an error at dirNotExistsOrEmpty line $LINENO' ERR

  trace "Entering checkDirNotExistsOrEmpty"

  if [ $# -eq 1 ] ; then
    if [ ! -d $1 ] ; then
      trace SEVERE "Directory $1 does not exists"
      exit 1
    else
      if [ -z "$(ls -A $1)" ] ; then
        trace SEVERE "Directory $1 is empty"
        exit 1
      fi
    fi
  fi

  trace "Exiting checkDirNotExistsOrEmpty"
  trap - ERR
}

# getSecretsMD5
#
# concatenate all the secrets, calculate the md5 and delete the file.
# The md5 is used to determine whether the domain needs to be recreated
# Note: the secrets are two levels indirections, so use find and filter out the ..data
# output:  /tm/secrets.md5

function getSecretsMD5() {
  trap 'error_handler There was an error at getSecretsMD5 line $LINENO' ERR
  trace "Entering getSecretsMD5"

  local secrets_text="/tmp/secrets.txt"
  local override_secrets="/weblogic-operator/config-overrides-secrets/"
  local weblogic_secrets="/weblogic-operator/secrets/"

  if [ -d "${override_secrets}" ] ; then
    # find the link and exclude ..data so that the normalized file name will be found
    # otherwise it will return ../data/xxx ..etc. Note: the actual file is in a timestamp linked directory
    find ${override_secrets} -type l -not -name "..data" -print  | sort  | xargs cat >> ${secrets_text}
  fi

  if [ -d "${weblogic_secrets}" ] ; then
    find ${weblogic_secrets} -type l -not -name "..data" -print |  sort  | xargs cat >> ${secrets_text}
  fi

  if [ ! -f "${secrets_text}" ] ; then
    echo "0" > ${secrets_text}
  fi
  local secrets_md5=$(md5sum ${secrets_text} | cut -d' ' -f1)
  echo ${secrets_md5} > /tmp/secrets.md5
  trace "Found secrets ${secrets_md5}"
  rm ${secrets_text}
  trace "Exiting getSecretsMD5"
  trap - ERR
}

#
# wdtCreatePrimordialDomain call WDT to create the domain
#

function wdtCreatePrimordialDomain() {
  trap 'error_handler There was an error at wdtCreatePrimordialDomain line $LINENO' ERR

  trace "Entering wdtCreatePrimodialDomain"

  #if [ ! -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
    # make sure wdt create write out the merged model to a file in the root of the domain
  export __WLSDEPLOY_STORE_MODEL__=1

  if [ ! -z ${WDT_PASSPHRASE} ]; then
    yes ${WDT_PASSPHRASE} | ${WDT_BINDIR}/createDomain.sh -oracle_home ${MW_HOME} -domain_home \
    ${DOMAIN_HOME} ${model_list} ${archive_list} ${variable_list} -use_encryption -domain_type ${WDT_DOMAIN_TYPE} \
    ${OPSS_FLAGS} >  ${WDT_OUTPUT}
  else
    ${WDT_BINDIR}/createDomain.sh -oracle_home ${MW_HOME} -domain_home ${DOMAIN_HOME} $model_list \
    ${archive_list} ${variable_list}  -domain_type ${WDT_DOMAIN_TYPE} ${OPSS_FLAGS} > ${WDT_OUTPUT}
  fi
  ret=$?
  if [ $ret -ne 0 ]; then
    trace SEVERE "Create Domain Failed "
    if [ -d ${LOG_HOME} ] && [ ! -z ${LOG_HOME} ] ; then
      cp  ${WDT_OUTPUT} ${LOG_HOME}/introspectJob_createDomain.log
    fi
    local WDT_ERROR=$(cat ${WDT_OUTPUT})
    trace SEVERE ${WDT_ERROR}
    exit 1
  fi

  # TODO: temporary logic (use validate to checck for changes once ready
  local create_primordial_tgz=0
  if [ -f  ${INTROSPECTCM_MERGED_MODEL} ] ; then

    ${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/model_diff.py ${DOMAIN_HOME}/wlsdeploy/domain_model.json \
        ${INTROSPECTCM_MERGED_MODEL}

    diff_rc=$(cat /tmp/model_diff_rc)

    trace "wdtCreatePrimordialDomain: model diff returns "${diff_rc}

    cat /tmp/diffed_model.json

    # only needs to targz if security info changed in domainInfo
    if [ ${diff_rc} -eq ${UNSAFE_SECURITY_UPDATE} ]; then
      create_primordial_tgz=1
    fi
  else
    create_primordial_tgz=1
  fi

  # tar up primodial domain with em.ear if it is there.  The zip will be added to the introspect config map by the
  # introspectDomain.py
  if [ ${create_primordial_tgz} -eq 1 ]; then
    empath=""
    if [ "${WDT_DOMAIN_TYPE}" != "WLS" ] ; then
      empath=$(grep "/em.ear" ${DOMAIN_HOME}/config/config.xml | grep -oPm1 "(?<=<source-path>)[^<]+")
    fi

    tar -pczf /tmp/prim_domain.tar.gz --exclude ${DOMAIN_HOME}/wlsdeploy --exclude ${DOMAIN_HOME}/lib  ${empath} \
    ${DOMAIN_HOME}/*
  fi

  #fi


  trace "Exiting wdtCreatePrimordialDomain"
  trap - ERR

}

#
# wdtCreateDomain call WDT to create the domain
#

function wdtCreateDomain() {
  trap 'error_handler There was an error at wdtCreateDomain line $LINENO' ERR

  trace "Entering wdtCreateDomain"

  wdtCreatePrimordialDomain

  # if the primordial domain already in the configmap, restore it

  if [ -f "/tmp/prim_domain.tar.gz" ] ; then
    trace "Using newly created domain"
  elif [ -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
    cd / && base64 -d ${PRIMORDIAL_DOMAIN_ZIPPED} > /tmp/primordial_tar.gz && tar -xzvf /tmp/primordial_tar.gz
  fi

  # make sure wdt create write out the merged model to a file in the root of the domain
  export __WLSDEPLOY_STORE_MODEL__=1

  if [ ! -z ${WDT_PASSPHRASE} ]; then
    yes ${WDT_PASSPHRASE} | ${WDT_BINDIR}/updateDomain.sh -oracle_home ${MW_HOME} -domain_home \
    ${DOMAIN_HOME} ${model_list} ${archive_list} ${variable_list} -use_encryption -domain_type ${WDT_DOMAIN_TYPE} \
     > ${WDT_OUTPUT}
  else
    ${WDT_BINDIR}/updateDomain.sh -oracle_home ${MW_HOME} -domain_home ${DOMAIN_HOME} $model_list \
    ${archive_list} ${variable_list}  -domain_type ${WDT_DOMAIN_TYPE}  >  ${WDT_OUTPUT}
  fi
  ret=$?
  if [ $ret -ne 0 ]; then
    trace SEVERE "Create Domain Failed "
    if [ -d ${LOG_HOME} ] && [ ! -z ${LOG_HOME} ] ; then
      cp  ${WDT_OUTPUT} ${LOG_HOME}/introspectJob_updateDomain.log
    fi
    local WDT_ERROR=$(cat ${WDT_OUTPUT})
    trace SEVERE ${WDT_ERROR}
    exit 1
  fi

  trace "Exiting wdtCreateDomain"
  trap - ERR
}


function handleOnlineUpdate() {

  cp ${DOMAIN_HOME}/wlsdeploy/domain_model.json /tmp/domain_model.json.new
  admin_user=$(cat /weblogic-operator/secrets/username)
  admin_pwd=$(cat /weblogic-operator/secrets/password)


  ROLLBACK_FLAG=""
  if [ ! -z "${ROLLBACK_IF_REQUIRE_RESTART}" ] && [ "${ROLLBACK_IF_REQUIRE_RESTART}" == "true" ]; then
      ROLLBACK_FLAG="-rollback_if_require_restart"
  fi
  # no need for encryption phrase because the diffed model has real value
  # note: using yes seems to et a 141 return code, switch to echo seems to be ok
  # the problem is likely due to how wdt closing the input stream


  echo ${admin_pwd} | ${WDT_BINDIR}/updateDomain.sh -oracle_home ${MW_HOME} \
   -admin_url "t3://${AS_SERVICE_NAME}:${ADMIN_PORT}" -admin_user ${admin_user} -model_file \
   /tmp/diffed_model.json -domain_home ${DOMAIN_HOME} ${ROLLBACK_FLAG}

  ret=$?

  echo "Completed online update="${ret}

  if [ ${ret} -eq ${ROLLBACK_ERROR} ] ; then
    trace ">>>  updatedomainResult=3"
    exit 1
  elif [ ${ret} -ne 0 ] ; then
    trace "Introspect job terminated: Online update failed. Check error in the logs"
    trace "Note: Changes in the optional configmap and/or image may needs to be correction"
    trace ">>>  updatedomainResult=${ret}"
    exit 1
  else
    trace ">>>  updatedomainResult=${ret}"
  fi

  trace "wrote updateResult"

  # if online update is successful, then we extract the old domain and use offline update, so that
  # we can update the domain and reuse the old ldap
  rm -fr ${DOMAIN_HOME}
  cd / && base64 -d ${DOMAIN_ZIPPED} > /tmp/domain.tar.gz && tar -xzvf /tmp/domain.tar.gz
  chmod +x ${DOMAIN_HOME}/bin/*.sh ${DOMAIN_HOME}/*.sh

  # We do not need OPSS key for offline update

  ${WDT_BINDIR}/updateDomain.sh -oracle_home ${MW_HOME} \
   -model_file /tmp/diffed_model.json ${variable_list} -domain_home ${DOMAIN_HOME} -domain_type \
   ${WDT_DOMAIN_TYPE}

  mv  /tmp/domain_model.json.new ${DOMAIN_HOME}/wlsdeploy/domain_model.json

}

#
# Generic error handler
#
function error_handler() {
    echo $*
    exit 1
}
