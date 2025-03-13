#!/usr/bin/env bash
# Copyright (c) 2023, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script contains the all the function of creating domain on pv
# It is used by introspectDomain.sh job

source ${SCRIPTPATH}/utils.sh
source ${SCRIPTPATH}/wdt_common.sh


# we export the opss password file location because it's also used by introspectDomain.py
export OPSS_KEY_PASSPHRASE="/weblogic-operator/opss-walletkey-secret/walletPassword"
OPSS_KEY_B64EWALLET="/weblogic-operator/opss-walletfile-secret/walletFile"
WDT_MODEL_ENCRYPTION_PASSPHRASE_ROOT="/weblogic-operator/wdt-encryption-passphrase"
WDT_MODEL_ENCRYPTION_PASSPHRASE="${WDT_MODEL_ENCRYPTION_PASSPHRASE_ROOT}/passphrase"

IMG_MODELS_HOME="/auxiliary/models"
IMG_MODELS_ROOTDIR="${IMG_MODELS_HOME}"
IMG_ARCHIVES_ROOTDIR="${IMG_MODELS_HOME}"
IMG_VARIABLE_FILES_ROOTDIR="${IMG_MODELS_HOME}"
WDT_ROOT="/auxiliary/weblogic-deploy"
WDT_OUTPUT_DIR="${LOG_HOME:-/tmp}"
WDT_OUTPUT="${WDT_OUTPUT_DIR}/wdt_output.log"
WDT_CREATE_DOMAIN_LOG=createDomain.log


WDT_BINDIR="${WDT_ROOT}/bin"
WLSDEPLOY_PROPERTIES="${WLSDEPLOY_PROPERTIES} -Djava.security.egd=file:/dev/./urandom"
WDT_CONFIGMAP_ROOT="/weblogic-operator/wdt-config-map"

FATAL_JRF_INTROSPECTOR_ERROR_MSG="Domain On PV domain creation encountered an unrecoverable error.
 Please correct the errors and patch the domain resource 'domain.spec.introspectVersion' with a new
 value and try again. If the domain type requires RCU and you are not setting 'DomainAndRCU' in 'domain.spec.configuration.initializeDomainOnPV.domain.createIfNotExists'
 then you may have to drop and recreate the RCU schema before retry. Introspection Error: "

export WDT_MODEL_SECRETS_DIRS="/weblogic-operator/config-overrides-secrets"
[ ! -d ${WDT_MODEL_SECRETS_DIRS} ] && unset WDT_MODEL_SECRETS_DIRS

export WDT_MODEL_SECRETS_NAME_DIR_PAIRS="__weblogic-credentials__=/weblogic-operator/secrets,__WEBLOGIC-CREDENTIALS__=/weblogic-operator/secrets"

if [ ! -d "${WDT_OUTPUT_DIR}" ]; then
  trace "Creating WDT standard output directory: '${WDT_OUTPUT_DIR}'"
  createFolder "${WDT_OUTPUT_DIR}"  "This folder is for holding WDT command output files for logging purposes. If 'domain.spec.logHomeEnabled' is 'true', then it is located in 'domain.spec.logHome', otherwise it is located within '/tmp'." || exitOrLoop
fi

required_rcu() {
  if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] || [ "${WDT_DOMAIN_TYPE}" == "RestrictedJRF" ] ; then
    echo "0"
  else
    echo "1"
  fi
}

createDomainOnPVWLDomain() {
  start_trap
  trace "Entering createDomainOnPVWLDomain"

  if [ ! -f "${WDT_ROOT}/lib/weblogic-deploy-core.jar" ]; then
    trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
         "is 'PersistentVolume' " \
         "and a WebLogic Deploy Tool (WDT) install is not located at " \
         "'${WDT_ROOT}'. A WDT install " \
         "is normally created when you use the WebLogic Image Tool " \
         "to create an image for PersistentVolume."
     exitOrLoop
  fi

  checkDirNotExistsOrEmpty ${IMG_MODELS_HOME}
  checkDirNotExistsOrEmpty ${WDT_BINDIR}

  checkModelDirectoryExtensions

  # setup wdt parameters and also associative array before calling comparing md5 in checkExistInventory
  #
  trace "Building WDT parameters and MD5s"

  buildWDTParams

  # something changed in the wdt artifacts or wls version changed
  # create domain again
  if  [ -f ${DOMAIN_HOME}/config/config.xml ]; then
    trace "Domain already exists: no operation needed"
    if [ "$(required_rcu)" == "1" ]; then
      local curl_wl_ver="`getWebLogicVersion`"
      local curl_domain_ver="`getDomainVersion`"
      local wl_major_ver="`getMajorVersion ${curl_wl_ver}`"
      local domain_major_ver="`getMajorVersion ${curl_domain_ver}`"
  
      trace INFO "WebLogic najor version='$wl_major_ver'. Domain major version='${domain_major_ver}'."
      if ! versionEQ "$wl_major_ver" "${domain_major_ver}" ; then
        trace SEVERE "WebLogic version '${curl_wl_ver}' is newer than the initialized domain version '${curl_domain_ver}'. Once an initializeDomainOnPV domain is created, the major version of the WebLogic Server in the base image cannot be changed."
        exitOrLoop
      fi
    fi

  else
    createDomainFromWDTModel
  fi
  # Set this so that the introspectDomain.sh can decide to call the python script or not
  DOMAIN_CREATED=1

  trace "Exiting createDomainOnPVWLDomain"
  stop_trap
}

buildWDTParams() {
  trace "Entering buildWDTParams"

  model_list=""
  archive_list=""
  variable_list="/tmp/_k8s_generated_props.properties"

  #
  # First build the command line parameters for WDT
  # based on the file listing in the image or config map
  #

  for file in $(sort_files $IMG_MODELS_ROOTDIR ".yaml") ;
    do
      if [ "$model_list" != "" ]; then
        model_list="${model_list},"
      fi
      model_list="${model_list}${IMG_MODELS_ROOTDIR}/${file}"
    done

  for file in $(sort_files $WDT_CONFIGMAP_ROOT ".yaml") ;
    do
      if [ "$model_list" != "" ]; then
        model_list="${model_list},"
      fi
      model_list="${model_list}${WDT_CONFIGMAP_ROOT}/${file}"
    done

  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip") ;
    do
      if [ "$archive_list" != "" ]; then
        archive_list="${archive_list},"
      fi
      archive_list="${archive_list}${IMG_ARCHIVES_ROOTDIR}/${file}"
    done

  # Merge all properties together
  local SPACE_BLANK_LINE=" "
  for file in $(sort_files ${IMG_VARIABLE_FILES_ROOTDIR} ".properties") ;
    do
      cat ${IMG_VARIABLE_FILES_ROOTDIR}/${file} >> ${variable_list}
      # Make sure there is an extra line
      echo $SPACE_BLANK_LINE >> ${variable_list}
    done

  for file in $(sort_files ${WDT_CONFIGMAP_ROOT} ".properties") ;
    do
      echo $SPACE_BLANK_LINE >> ${variable_list}
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

  #  We cannot strictly run create domain for JRF liked type because it's tied to a database schema
  #  We shouldn't require user to drop the db first since it may have data in it
  #
  opss_wallet=$(get_opss_key_wallet_dopv)
  if [ -f "${opss_wallet}" ] ; then
    trace "A wallet file was passed in using walletFileSecret, so we're using an existing rcu schema."
    createFolder "/tmp/opsswallet" "This folder is used to hold a generated OPSS wallet file." || exitOrLoop
    base64 -d  ${opss_wallet} > /tmp/opsswallet/ewallet.p12
    OPSS_FLAGS="-opss_wallet /tmp/opsswallet"
  else
    OPSS_FLAGS=""
  fi

  trace "Exiting buildWDTParams"
}

createDomainFromWDTModel() {

  trace "Entering createDomainFromWDTModel"
  stop_trap

  # optional but useful - no need to fail
  if [ -d "$LOG_HOME" ] ; then
    trace "Creating rcu log directory "
    local LOG_DIR_RCU="$LOG_HOME/rculogdir"
    createFolder "$LOG_DIR_RCU" "This folder is used to hold rcu logs"
    if [ -d "$LOG_DIR_RCU" ] ; then
      export RCU_LOG_LOCATION=$LOG_DIR_RCU
    fi
  fi

  local wdtArgs=""
  wdtArgs+=" -oracle_home ${ORACLE_HOME}"
  wdtArgs+=" -domain_home ${DOMAIN_HOME}"
  wdtArgs+=" ${model_list} ${archive_list} ${variable_list}"
  wdtArgs+=" -domain_type ${WDT_DOMAIN_TYPE}"
  wdtArgs+=" ${OPSS_FLAGS}"

  if [ "$(required_rcu)" == "1" ]; then
    export WDT_CUSTOM_CONFIG=/tmp/model_filters
    mkdir -p "${WDT_CUSTOM_CONFIG}" || exitOrLoop
    cp /weblogic-operator/scripts/dopv-filters.json "${WDT_CUSTOM_CONFIG}/model_filters.json" || exitOrLoop
  fi

  if [ -d "${WDT_MODEL_ENCRYPTION_PASSPHRASE_ROOT}" ]; then
    if [ ! -f "${WDT_MODEL_ENCRYPTION_PASSPHRASE}" ]; then
      trace SEVERE "Domain Source Type is 'DomainOnPV' and you have specified " \
      " 'initializeDomainOnPV.wdtModelEncryptionPassphraseSecret' but this secret does not have the required key " \
      " 'passphrase', update the secret and rerun the introspector job."
      exitOrLoop
    fi
    wdtArgs+=" -passphrase_file ${WDT_MODEL_ENCRYPTION_PASSPHRASE}"
  fi

  if [ -z "${OPSS_FLAGS}" ]; then

    # Determine run rcu or not

    if [ "${INIT_DOMAIN_ON_PV}" == "DomainAndRCU" ] && [ "$(required_rcu)" == "1" ]; then
      wdtArgs+=" -run_rcu"
    fi
    trace "About to call '${WDT_BINDIR}/createDomain.sh ${wdtArgs}'."
    ${WDT_BINDIR}/createDomain.sh ${wdtArgs} > ${WDT_OUTPUT} 2>&1
  else
    # when there is a wallet presence either in the configmap or secret

    trace "About to call '${WDT_BINDIR}/createDomain.sh ${wdtArgs}'."
    # shellcheck disable=SC2046
    echo $(cat ${OPSS_KEY_PASSPHRASE}) | \
      ${WDT_BINDIR}/createDomain.sh ${wdtArgs} > ${WDT_OUTPUT} 2>&1

  fi

  ret=$?
  if [ $ret -ne 0 ]; then
    # Important:
    # The "FatalIntrospectorError" keyword is detected by DomainProcessorImpl.isShouldContinue
    # If it is detected then it will stop the periodic retry
    # We need to prevent retries with a "FatalIntrospectorError".  We are not retrying create domain for now.
    #
    trace SEVERE "Domain On PV: FatalIntrospectorError: WDT Create domain failed, return code ${ret}. ${FATAL_JRF_INTROSPECTOR_ERROR_MSG}"
    cat ${WDT_OUTPUT}
    exitOrLoop
  else
    trace "WDT Create Domain Succeeded, ret=${ret}:"
    cat ${WDT_OUTPUT}
  fi

  wdtRotateAndCopyLogFile "${WDT_CREATE_DOMAIN_LOG}"
  wdtRotateAndCopyOutFile

  trace "WDT log files have been copied to '$LOG_HOME'.  Additional RCU log files if any can be found under '$LOG_HOME/rculogdir'"

  # restore trap
  start_trap

  trace "Exiting createDomainFromWDTModel"
}

# get_opss_key_wallet_dopv   returns opss key wallet ewallet.p12 location
#
# if there is one from the user config map, use it first
#

get_opss_key_wallet_dopv() {
  if [ -f ${OPSS_KEY_B64EWALLET} ]; then
    echo ${OPSS_KEY_B64EWALLET}
  fi
}
