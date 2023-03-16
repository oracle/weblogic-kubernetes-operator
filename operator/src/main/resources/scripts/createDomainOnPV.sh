#!/usr/bin/env bash
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script contains the all the function of creating domain on pv
# It is used by introspectDomain.sh job

source ${SCRIPTPATH}/utils.sh
source ${SCRIPTPATH}/wdt_common.sh

OPERATOR_ROOT=${TEST_OPERATOR_ROOT:-/weblogic-operator}

# we export the opss password file location because it's also used by introspectDomain.py
export OPSS_KEY_PASSPHRASE="/weblogic-operator/opss-walletkey-secret/walletPassword"
OPSS_KEY_B64EWALLET="/weblogic-operator/opss-walletfile-secret/walletFile"
IMG_MODELS_HOME="${WDT_MODEL_HOME:-/u01/wdt/models}"
IMG_MODELS_ROOTDIR="${IMG_MODELS_HOME}"
IMG_ARCHIVES_ROOTDIR="${IMG_MODELS_HOME}"
IMG_VARIABLE_FILES_ROOTDIR="${IMG_MODELS_HOME}"
WDT_ROOT="${WDT_INSTALL_HOME:-/u01/wdt/weblogic-deploy}"
WDT_OUTPUT_DIR="${LOG_HOME:-/tmp}"
WDT_OUTPUT="${WDT_OUTPUT_DIR}/wdt_output.log"
WDT_CREATE_DOMAIN_LOG=createDomain.log
WDT_BINDIR="${WDT_ROOT}/bin"
WLSDEPLOY_PROPERTIES="${WLSDEPLOY_PROPERTIES} -Djava.security.egd=file:/dev/./urandom"
WDT_CONFIGMAP_ROOT="/weblogic-operator/wdt-config-map"

FATAL_JRF_INTROSPECTOR_ERROR_MSG="Domain On PV JRF domain creation and schema initialization encountered an unrecoverable error.
 If it is a database credential related error such as wrong password, schema prefix, or database connect
 string, then correct the error and patch the domain resource 'domain.spec.introspectVersion' with a new
 value. If the error is not related to a database credential, then you must also drop and recreate the
 JRF schemas before patching the domain resource. Introspection Error: "

export WDT_MODEL_SECRETS_DIRS="/weblogic-operator/config-overrides-secrets"
[ ! -d ${WDT_MODEL_SECRETS_DIRS} ] && unset WDT_MODEL_SECRETS_DIRS

export WDT_MODEL_SECRETS_NAME_DIR_PAIRS="__weblogic-credentials__=/weblogic-operator/secrets,__WEBLOGIC-CREDENTIALS__=/weblogic-operator/secrets"

if [ ! -d "${WDT_OUTPUT_DIR}" ]; then
  trace "Creating WDT standard output directory: '${WDT_OUTPUT_DIR}'"
  createFolder "${WDT_OUTPUT_DIR}"  "This folder is for holding Model In Image WDT command output files for logging purposes. If 'domain.spec.logHomeEnabled' is 'true', then it is located in 'domain.spec.logHome', otherwise it is located within '/tmp'." || exitOrLoop
fi

createDomainOnPVWLDomain() {
  start_trap
  trace "Entering createDomainOnPVWLDomain"

  if [ ! -f "${WDT_ROOT}/lib/weblogic-deploy-core.jar" ]; then
    trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
         "is 'PersistentVolume' " \
         "and a WebLogic Deploy Tool (WDT) install is not located at " \
         "'spec.configuration.model.wdtInstallHome' " \
         "which is currently set to '${WDT_ROOT}'. A WDT install " \
         "is normally created when you use the WebLogic Image Tool " \
         "to create an image for Model in Image."
     exitOrLoop
  fi

  # Check if modelHome (default /u01/wdt/models) and wdtInstallHome (default /u01/wdt/weblogic-deploy) exists
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
  variable_list="/u01/_k8s_generated_props.properties"

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

  # Remove operator already checking it
#  if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] && [ ! -f "${OPSS_KEY_PASSPHRASE}" ] ; then
#    trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
#       "is 'PersistentVolume' and the 'spec.configuration.initializeDomainOnPV.domain.domainType' is 'JRF';" \
#       "this combination requires specifying a" \
#       "'spec.configuration.initializeDomainOnPV.domain.opss.walletPasswordSecret' in your domain" \
#       "resource and deploying this secret with a 'walletPassword' key," \
#       "but the secret does not have this key."
#    exitOrLoop
#  fi

  #  We cannot strictly run create domain for JRF type because it's tied to a database schema
  #  We shouldn't require user to drop the db first since it may have data in it
  #
  opss_wallet=$(get_opss_key_wallet)
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

  export __WLSDEPLOY_STORE_MODEL__=1

  local wdtArgs=""
  wdtArgs+=" -oracle_home ${ORACLE_HOME}"
  wdtArgs+=" -domain_home ${DOMAIN_HOME}"
  wdtArgs+=" ${model_list} ${archive_list} ${variable_list}"
  wdtArgs+=" -domain_type ${WDT_DOMAIN_TYPE}"
  wdtArgs+=" ${OPSS_FLAGS}"

  cd $WDT_ROOT
  trace "About to call '${WDT_BINDIR}/createDomain.sh ${wdtArgs}'."

  if [ -z "${OPSS_FLAGS}" ]; then

    # We get here for WLS domains, and for the JRF 'first time' case

    # JRF wallet generation note:
    #  If this is JRF, the unset OPSS_FLAGS indicates no wallet file was specified
    #  via spec.configuration.opss.walletFileSecret and so we assume that this is
    #  the first time this domain started for this RCU database. We also assume
    #  that 'createDomain.sh' will perform the one time initialization of the
    #  empty RCU schema for the domain in the database (where the empty schema
    #  itself must be setup external to the Operator by calling 'create_rcu_schema.sh'
    #  or similar prior to deploying the domain for the first time).
    #
    #  The 'introspectDomain.py' script, which runs later, will create a wallet
    #  file using the spec.configuration.opss.walletPasswordSecret as its passphrase
    #  so that an administrator can then retrieve the file from the introspector's
    #  output configmap and save it for reuse.

    if [ ${INIT_DOMAIN_ON_PV} == "domainAndRCU" ] && [ ${WDT_DOMAIN_TYPE} == 'JRF' ]; then
      wdtArgs+=" -run_rcu"
    fi

    ${WDT_BINDIR}/createDomain.sh ${wdtArgs} > ${WDT_OUTPUT} 2>&1
  else
    # We get here only for JRF domain 'second time' (or more) case.

    # JRF wallet reuse note:
    #  The set OPSS_FLAGS indicates a wallet file was specified
    #  via spec.configuration.opss.walletFileSecret on the domain resource.
    #  So we assume that this domain already
    #  has its RCU tables and the wallet file will give us access to them.

    echo $(cat ${OPSS_KEY_PASSPHRASE}) | \
      ${WDT_BINDIR}/createDomain.sh ${wdtArgs} > ${WDT_OUTPUT} 2>&1

  fi

  ret=$?
  if [ $ret -ne 0 ]; then
    # Important:
    # The "FatalIntrospectorError" keyword is detected by DomainProcessorImpl.isShouldContinue
    # If it is detected then it will stop the periodic retry
    # We need to prevent retries with a "MII Fatal Error" because JRF without the OPSS_FLAGS indicates
    # a likely attempt to initialize the RCU DB schema for this domain, and we don't want to retry when this fails
    # without admin intervention (retrying can compound the problem and obscure the original issue).
    #
    if [ "JRF" == "$WDT_DOMAIN_TYPE" ] && [ -z "${OPSS_FLAGS}" ] ; then
      trace SEVERE "Domain On PV: FatalIntrospectorError: WDT Create Domain Failed, return ${ret}. " \
        ${FATAL_JRF_INTROSPECTOR_ERROR_MSG}
    else
      trace SEVERE "Domain On PV: WDT Create Domain Failed, ret=${ret}"
    fi
    cat ${WDT_OUTPUT}
    exitOrLoop
  else
    trace "WDT Create Domain Succeeded, ret=${ret}:"
    cat ${WDT_OUTPUT}
  fi

  wdtRotateAndCopyLogFile "${WDT_CREATE_DOMAIN_LOG}"

  # restore trap
  start_trap

  trace "Exiting createDomainFromWDTModel"
}

