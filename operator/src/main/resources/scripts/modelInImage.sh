#!/usr/bin/env bash
# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script contains the all the function of model in image
# It is used by introspectDomain.sh job and startServer.sh

source ${SCRIPTPATH}/utils.sh
source ${SCRIPTPATH}/wdt_common.sh

OPERATOR_ROOT=${TEST_OPERATOR_ROOT:-/weblogic-operator}
INTROSPECTCM_IMAGE_MD5="/weblogic-operator/introspectormii/inventory_image.md5"
INTROSPECTCM_CM_MD5="/weblogic-operator/introspectormii/inventory_cm.md5"
INTROSPECTCM_PASSPHRASE_MD5="/weblogic-operator/introspectormii/inventory_passphrase.md5"
INTROSPECTCM_MERGED_MODEL="/weblogic-operator/introspectormii/merged_model.json"
INTROSPECTCM_WLS_VERSION="/weblogic-operator/introspectormii/wls.version"
INTROSPECTCM_JDK_PATH="/weblogic-operator/introspectormii/jdk.path"
INTROSPECTCM_SECRETS_AND_ENV_MD5="/weblogic-operator/introspectormii/secrets_and_env.md5"
INTROSPECTCM_DOMAIN_WDT_VERSION="/weblogic-operator/introspectormii/domain_wdt_version"
PRIMORDIAL_DOMAIN_ZIPPED="/weblogic-operator/introspectormii/primordial_domainzip.secure"
WLSDOMAIN_CONFIG_ZIPPED="/weblogic-operator/introspectormii//domainzip.secure"
INTROSPECTJOB_IMAGE_MD5="/tmp/inventory_image.md5"
INTROSPECTJOB_CM_MD5="/tmp/inventory_cm.md5"
INTROSPECTJOB_PASSPHRASE_MD5="/tmp/inventory_passphrase.md5"
LOCAL_PRIM_DOMAIN_ZIP="/tmp/prim_domain.tar.gz"
LOCAL_WLSDOMAIN_CONFIG_ZIP="/tmp/wlsdomain_config.gz"
LOCAL_PRIM_DOMAIN_TAR="/tmp/prim_domain.tar"
NEW_MERGED_MODEL="/tmp/new_merged_model.json"
WDT_CONFIGMAP_ROOT="/weblogic-operator/wdt-config-map"
RUNTIME_ENCRYPTION_SECRET_PASSWORD="/weblogic-operator/model-runtime-secret/password"
DOMAIN_BIN_LIB_LIST="/weblogic-operator/introspector/binliblist"
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
WDT_UPDATE_DOMAIN_LOG=updateDomain.log
WDT_VALIDATE_MODEL_LOG=validateModel.log
WDT_COMPARE_MODEL_LOG=compareModel.log
WDT_BINDIR="${WDT_ROOT}/bin"
WDT_FILTER_JSON="/weblogic-operator/scripts/model-filters.json"
WDT_CREATE_FILTER="/weblogic-operator/scripts/model-wdt-create-filter.py"
WDT_MII_FILTER="/weblogic-operator/scripts/model_wdt_mii_filter.py"
UPDATE_RCUPWD_FLAG=""
WLSDEPLOY_PROPERTIES="${WLSDEPLOY_PROPERTIES} -Djava.security.egd=file:/dev/./urandom"
ARCHIVE_ZIP_CHANGED=0
WDT_ARTIFACTS_CHANGED=0
PROG_RESTART_REQUIRED=103
MII_UPDATE_NO_CHANGES_TO_APPLY=false
# return codes for model_diff
UNSAFE_ONLINE_UPDATE=0
SAFE_ONLINE_UPDATE=1
FATAL_MODEL_CHANGES=2
MERGED_MODEL_ENVVARS_SAME="false"
SECURITY_INFO_UPDATED=4
RCU_PASSWORD_CHANGED=5
NOT_FOR_ONLINE_UPDATE=6
SCRIPT_ERROR=255
SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

WDT_ONLINE_MIN_VERSION="1.9.9"
WDT_OFFLINE_MIN_VERSION="1.7.3"

FATAL_JRF_INTROSPECTOR_ERROR_MSG="Model In Image JRF domain creation and schema initialization encountered an unrecoverable error.
 If it is a database credential related error such as wrong password, schema prefix, or database connect
 string, then correct the error and patch the domain resource 'domain.spec.introspectVersion' with a new
 value. If the error is not related to a database credential, then you must also drop and recreate the
 JRF schemas before patching the domain resource. Introspection Error: "

export WDT_MODEL_SECRETS_DIRS="/weblogic-operator/config-overrides-secrets"
[ ! -d ${WDT_MODEL_SECRETS_DIRS} ] && unset WDT_MODEL_SECRETS_DIRS

#TBD: CREDENTIALS_SECRET_NAME is unexpectedly empty. Maybe that's a regression?
#  export WDT_MODEL_SECRETS_NAME_DIR_PAIRS="__weblogic-credentials__=/weblogic-operator/secrets,__WEBLOGIC-CREDENTIALS__=/weblogic-operator/secrets,${CREDENTIALS_SECRET_NAME}=/weblogic-operator/secret"
#For now:
export WDT_MODEL_SECRETS_NAME_DIR_PAIRS="__weblogic-credentials__=/weblogic-operator/secrets,__WEBLOGIC-CREDENTIALS__=/weblogic-operator/secrets"

if [ ! -d "${WDT_OUTPUT_DIR}" ]; then
  trace "Creating WDT standard output directory: '${WDT_OUTPUT_DIR}'"
  createFolder "${WDT_OUTPUT_DIR}"  "This folder is for holding Model In Image WDT command output files for logging purposes. If 'domain.spec.logHomeEnabled' is 'true', then it is located in 'domain.spec.logHome', otherwise it is located within '/tmp'." || exitOrLoop
fi

#
# If JAVA_OPTIONS are specified via config-map, replace the newline and env variables.
#
if [[ "${REPLACE_VARIABLES_IN_JAVA_OPTIONS}" == "true" ]]; then
  replaceEnv "$JAVA_OPTIONS" newJavaOptions true
  if [[ "${newJavaOptions}" =~ "SEVERE ERROR: " ]]; then
    echo  "${newJavaOptions}"
    exit 1
  fi
  JAVA_OPTIONS="${newJavaOptions}"
fi

#
# compareArtifactsMD5  checks the WDT artifacts MD5s in the introspect config map against the current introspect job
# WDT artifacts MD5s
#
# If there are any differences, set WDT_ARTIFACTS_CHANGED=1
# If there are any WDT archives changed set ARCHIVE_ZIP_CHANGED=1 (for online update)
#

compareArtifactsMD5() {

  local has_md5=0

  trace "Entering checkExistInventory"

  trace "Checking wdt artifacts in image"
  if [ -f ${INTROSPECTCM_IMAGE_MD5} ] ; then
    has_md5=1
    # introspectorDomain py put two blank lines in the configmap, use -B to ignore blank lines
    diff -wB ${INTROSPECTCM_IMAGE_MD5} ${INTROSPECTJOB_IMAGE_MD5} > /tmp/imgmd5diff
    if [ $? -ne 0 ] ; then
      trace "WDT artifacts in image changed: create domain again"
      WDT_ARTIFACTS_CHANGED=1
    fi
  fi

  trace "Checking wdt artifacts in config map"
  if [ -f ${INTROSPECTCM_CM_MD5} ] ; then
    has_md5=1
    diff -wB  ${INTROSPECTCM_CM_MD5} ${INTROSPECTJOB_CM_MD5}
    if [ $? -ne 0 ] ; then
      trace "WDT artifacts in wdt config map changed: create domain again"
      WDT_ARTIFACTS_CHANGED=1
    fi
  else
    # if no config map before but adding one now
    if [ -f ${INTROSPECTJOB_CM_MD5} ]; then
      trace "New inventory in cm: create domain"
      WDT_ARTIFACTS_CHANGED=1
    fi
  fi

  if [ $has_md5 -eq 0 ]; then
    # Initial deployment
    trace "no md5 found: create domain"
    WDT_ARTIFACTS_CHANGED=1
  fi

  trace "Exiting checkExistInventory"
}

# get_opss_key_wallet   returns opss key wallet ewallet.p12 location
#
# if there is one from the user config map, use it first
# otherwise use the one in the introspect job config map
#

get_opss_key_wallet() {
  if [ -f ${OPSS_KEY_B64EWALLET} ]; then
    echo ${OPSS_KEY_B64EWALLET}
  else
    echo "/weblogic-operator/introspectormii/ewallet.p12"
  fi
}

#
# buildWDTParams_MD5   Setup the WDT artifacts MD5 for comparison between updates
#  Also setup the wdt parameters
#

buildWDTParams_MD5() {
  trace "Entering setupInventoryList"

  model_list=""
  archive_list=""
  variable_list="/u01/_k8s_generated_props.properties"

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
  local SPACE_BLANK_LINE=" "
  for file in $(sort_files ${IMG_VARIABLE_FILES_ROOTDIR} ".properties") ;
    do
      md5sum ${IMG_VARIABLE_FILES_ROOTDIR}/$file >> ${INTROSPECTJOB_IMAGE_MD5}
      cat ${IMG_VARIABLE_FILES_ROOTDIR}/${file} >> ${variable_list}
      # Make sure there is an extra line
      echo $SPACE_BLANK_LINE >> ${variable_list}
    done

  for file in $(sort_files ${WDT_CONFIGMAP_ROOT} ".properties") ;
    do
      md5sum  ${WDT_CONFIGMAP_ROOT}/$file >> ${INTROSPECTJOB_CM_MD5}
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

  if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] && [ ! -f "${OPSS_KEY_PASSPHRASE}" ] ; then
    trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
       "is 'FromModel' and the 'spec.configuration.model.domainType' is 'JRF';" \
       "this combination requires specifying a" \
       "'spec.configuration.opss.walletPasswordSecret' in your domain" \
       "resource and deploying this secret with a 'walletPassword' key," \
       "but the secret does not have this key."
    exitOrLoop
  fi

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

  if [ "true" == "${MII_USE_ONLINE_UPDATE}" ] ; then
    overrideWDTTimeoutValues
  fi

  trace "Exiting setupInventoryList"
}

changeTimeoutProperty() {
  if [ ! -z $2 ] ; then
    sed -i "s/\($1=\).*\$/\1$2/" ${WDT_ROOT}/lib/tool.properties || exitOrLoop
  fi
}

overrideWDTTimeoutValues() {
  start_trap
  trace "Entering overrideWDTTimeoutValues"
  # WDT defaults
  #
  #  connect.timeout=120000
  #  activate.timeout=180000
  #  deploy.timeout=180000
  #  redeploy.timeout=180000
  #  undeploy.timeout=180000
  #  start.application.timeout=180000
  #  stop.application.timeout=180000
  #  set.server.groups.timeout=30000

  changeTimeoutProperty "connect.timeout" ${WDT_CONNECT_TIMEOUT}
  changeTimeoutProperty "activate.timeout" ${WDT_ACTIVATE_TIMEOUT}
  changeTimeoutProperty "deploy.timeout" ${WDT_DEPLOY_TIMEOUT}
  changeTimeoutProperty "redeploy.timeout" ${WDT_REDEPLOY_TIMEOUT}
  changeTimeoutProperty "undeploy.timeout" ${WDT_UNDEPLOY_TIMEOUT}
  changeTimeoutProperty "start.application.timeout" ${WDT_START_APPLICATION_TIMEOUT}
  changeTimeoutProperty "stop.application.timeout" ${WDT_STOP_APPLICATION_TIMEOUT}
  changeTimeoutProperty "set.server.groups.timeout" ${WDT_SET_SERVER_GROUPS_TIMEOUT}

  trace "Exiting setupInventoryList"
  stop_trap
}

# createWLDomain
#

createWLDomain() {
  start_trap
  trace "Entering createWLDomain"

  if [ ! -f ${RUNTIME_ENCRYPTION_SECRET_PASSWORD} ] ; then
    trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
       "is 'FromModel';" \
       "this requires specifying a" \
       "'spec.configuration.model.runtimeEncryptionSecret' in your domain" \
       "resource and deploying this secret with a 'password' key," \
       "but the secret does not have this key."
    exitOrLoop
  fi

  if [ ! -f "${WDT_ROOT}/lib/weblogic-deploy-core.jar" ]; then
    trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
         "is 'FromModel' " \
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
  if [ "true" != "${WDT_BYPASS_WDT_VERSION_CHECK}" ] ; then
    checkWDTVersion
  fi

  # copy the filter related files to the wdt lib
  cp ${WDT_FILTER_JSON} ${WDT_ROOT}/lib/model_filters.json || logSevereAndExit ${WDT_FILTER_JSON}
  cp ${WDT_CREATE_FILTER} ${WDT_ROOT}/lib || logSevereAndExit ${WDT_CREATE_FILTER}
  cp ${WDT_MII_FILTER} ${WDT_ROOT}/lib || logSevereAndExit ${WDT_MII_FILTER}

  # check to see if any model including changed (or first model in image deploy)
  # if yes. then run create domain again


  local current_version=$(getWebLogicVersion)
  local current_jdkpath=$(readlink -f $JAVA_HOME)
  # check for version:  can only be rolling

  local version_changed=0
  local jdk_changed=0
  SECRETS_AND_ENV_CHANGED=0
  trace "current version "${current_version}

  getSecretsAndEnvMD5
  local current_secrets_and_env_md5=$(cat /tmp/secrets_and_env.md5)

  trace "Checking changes in secrets and jdk path"

  if [ -f ${INTROSPECTCM_SECRETS_AND_ENV_MD5} ] ; then
    previous_secrets_and_env_md5=$(cat ${INTROSPECTCM_SECRETS_AND_ENV_MD5})
    if [ "${current_secrets_and_env_md5}" != "${previous_secrets_and_env_md5}" ]; then
      trace "Secrets and env different: old_md5=${previous_secrets_and_env_md5} new_md5=${current_secrets_and_env_md5}"
      SECRETS_AND_ENV_CHANGED=1
    fi
  fi

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

  # Set this so that the introspectDomain.sh can decide to call the python script of not
  DOMAIN_CREATED=0

  # something changed in the wdt artifacts or wls version changed
  # create domain again

  DISABLE_SM_FOR_12214_NONSM_UPG=0
  if [  -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
    checkSecureModeForUpgrade
  fi
  if  [ ${WDT_ARTIFACTS_CHANGED} -ne 0 ] || [ ${jdk_changed} -eq 1 ] \
    || [ ${SECRETS_AND_ENV_CHANGED} -ne 0 ] || [ ${DISABLE_SM_FOR_12214_NONSM_UPG} -eq 1 ] ; then
    trace "Need to create domain ${WDT_DOMAIN_TYPE}"
    createModelDomain
    if [ "${MERGED_MODEL_ENVVARS_SAME}" == "false" ] || [ ${DISABLE_SM_FOR_12214_NONSM_UPG} -eq 1 ] ; then
      # Make sure it will run the introspectDomain.py
      DOMAIN_CREATED=1
    fi
  else
    trace "Nothing changed no op"
  fi
  trace "Exiting createWLDomain"
  stop_trap
}


# Check for WDT version

checkWDTVersion() {
  trace "Entering checkWDTVersion"
  unzip -c ${WDT_ROOT}/lib/weblogic-deploy-core.jar META-INF/MANIFEST.MF > /tmp/wdtversion.txt || exitOrLoop
  WDT_VERSION="$(grep "Implementation-Version" /tmp/wdtversion.txt | cut -f2 -d' ' | tr -d '\r' )" || exitOrLoop

  # trim out any non numeric character except dot and numbers, this avoid handling SNAPSHOT release
  WDT_VERSION=$(echo "${WDT_VERSION}" | tr -dc ^[.0-9]) || exitOrLoop

  if [ "true" = "${MII_USE_ONLINE_UPDATE}" ]; then
    local actual_min="$WDT_ONLINE_MIN_VERSION"
  else
    local actual_min="$WDT_OFFLINE_MIN_VERSION"
  fi

  if [ -z "${WDT_VERSION}" ] || ! versionGE "${WDT_VERSION}" "${actual_min}" ; then
    trace SEVERE "The domain resource 'spec.domainHomeSourceType' is 'FromModel'" \
      "and its image's WebLogic Deploy Tool installation has version '${WDT_VERSION:-unknown version}'" \
      "but introspection requires at least version '${WDT_ONLINE_MIN_VERSION}'" \
      "when 'spec.configuration.model.onlineUpdate.enabled' is set to 'true'" \
      "or at least version '${WDT_OFFLINE_MIN_VERSION}' otherwise." \
      "To fix this, create another image with an updated version of the WebLogic Deploy" \
      "Tool and redeploy the domain again."
    exitOrLoop
  fi

  if versionGE ${WDT_VERSION} "4.0.0" ; then
     echo "${WDT_VERSION}" > /tmp/domain_wdt_version
  fi
  trace "Exiting checkWDTVersion"
}

# getSecretsAndEnvMD5
#
# concatenate all the secrets and env, calculate the md5 and delete the file.
# The md5 is used to determine whether the domain needs to be recreated
# Note: the secrets are two levels indirections, so use find and filter out the ..data
# output:  /tmp/secrets_and_env.md5

getSecretsAndEnvMD5() {
  trace "Entering getSecretsAndEnvMD5"

  local secrets_and_env_text="/tmp/secrets.txt"
  local override_secrets="/weblogic-operator/config-overrides-secrets/"
  local weblogic_secrets="/weblogic-operator/secrets/"
  local env_var

  rm -f ${secrets_and_env_text}

  for env_var in ${OPERATOR_ENVVAR_NAMES//,/ }; do
    echo "$env_var='${!env_var}'"
  done | sort >> ${secrets_and_env_text}

  if [ -d "${override_secrets}" ] ; then
    # find the link and exclude ..data so that the normalized file name will be found
    # otherwise it will return ../data/xxx ..etc. Note: the actual file is in a timestamp linked directory
    find ${override_secrets} -type l -not -name "..data" -print  | sort  | xargs cat >> ${secrets_and_env_text}
  fi

  if [ -d "${weblogic_secrets}" ] ; then
    find ${weblogic_secrets} -type l -not -name "..data" -print |  sort  | xargs cat >> ${secrets_and_env_text}
  fi

  if [ ! -f "${secrets_and_env_text}" ] ; then
    echo "0" > ${secrets_and_env_text}
  fi
  local secrets_and_env_md5=$(md5sum ${secrets_and_env_text} | cut -d' ' -f1)
  echo ${secrets_and_env_md5} > /tmp/secrets_and_env.md5
  trace "Found secrets and env: md5=${secrets_and_env_md5}"
  rm ${secrets_and_env_text}
  trace "Exiting getSecretsAndEnvMD5"
}


#
# createModelDomain call WDT to create the domain
#

createModelDomain() {

  trace "Entering createModelDomain"
  createPrimordialDomain

  # If model changes or upgrade image scenario, run update domain.
  if [ "${MERGED_MODEL_ENVVARS_SAME}" == "false" ] || [ $DISABLE_SM_FOR_12214_NONSM_UPG -eq 1 ] ; then
    # if there is a new primordial domain created then use newly created primordial domain otherwise
    # if the primordial domain already in the configmap, restore it
    #

    if [ -f "${LOCAL_PRIM_DOMAIN_ZIP}" ] ; then
      trace "Using newly created domain"
    elif [ -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
      trace "Using existing primordial domain"
      restoreIntrospectorPrimordialDomain || return 1
      # create empty lib since we don't archive it in primordial zip and WDT will fail without it
      createFolder "${DOMAIN_HOME}/lib" "This is the './lib' directory within directory 'domain.spec.domainHome'." || exitOrLoop
      # Since the SerializedSystem ini is encrypted, restore it first
      local MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})
      encrypt_decrypt_domain_secret "decrypt" ${DOMAIN_HOME} ${MII_PASSPHRASE}
    fi

    wdtUpdateModelDomain

    # This will be a no op if MII_USE_ONLINE_UPDATE is not defined or false
    wdtHandleOnlineUpdate

  fi

  trace "Exiting createModelDomain"
}


# Expands into the root directory the MII domain configuration, stored in one or more config maps
restoreDomainConfig() {
  restoreEncodedTar "domainzip.secure" || return 1

  chmod u+x ${DOMAIN_HOME}/bin/*.sh ${DOMAIN_HOME}/*.sh  || return 1
}

# Expands into the root directory the MII primordial domain, stored in one or more config maps
restorePrimordialDomain() {
  restoreEncodedTar "primordial_domainzip.secure" || return 1
}

restoreIntrospectorPrimordialDomain() {
  cd / || return 1
  cat $(ls /weblogic-operator/introspectormii*/primordial_domainzip.secure | sort -t- -k3) > /tmp/domain.secure || return 1
  base64 -d "/tmp/domain.secure" > $LOCAL_PRIM_DOMAIN_ZIP || return 1
  tar -pxzf $LOCAL_PRIM_DOMAIN_ZIP || return 1
}

# Restores the specified directory, targz'ed and stored in one or more config maps after base 64 encoding
# args:
# $1 the name of the encoded file in the config map
restoreEncodedTar() {
  cd / || return 1
  cat $(ls ${OPERATOR_ROOT}/introspector*/${1} | sort -t- -k3) > /tmp/domain.secure || return 1
  base64 -d "/tmp/domain.secure" > /tmp/domain.tar.gz || return 1

  tar -pxzf /tmp/domain.tar.gz || return 1
}

# This is before WDT compareModel implementation
#
diff_model_v1() {
  trace "Entering diff_model v1"

  #
  local ORACLE_SERVER_DIR=${ORACLE_HOME}/wlserver
  local JAVA_PROPS="-Dpython.cachedir.skip=true ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.path=${ORACLE_SERVER_DIR}/common/wlst/modules/jython-modules.jar/Lib ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.console= ${JAVA_PROPS} -Djava.security.egd=file:/dev/./urandom"
  local CP=${ORACLE_SERVER_DIR}/server/lib/weblogic.jar
  ${JAVA_HOME}/bin/java -cp ${CP} \
    ${JAVA_PROPS} \
    org.python.util.jython \
    ${SCRIPTPATH}/model-diff-v1.py $1 $2 > ${WDT_OUTPUT} 2>&1
  if [ $? -ne 0 ] ; then
    trace SEVERE "Failed to compare models. Check logs for error. Comparison output:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi
  trace "Exiting diff_model v1"
}

# This is WDT compareModel.sh implementation

diff_model() {
  trace "Entering diff_model"
  # wdt shell script or logFileRotate may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it

  stop_trap
  export __WLSDEPLOY_STORE_MODEL__=1
  # $1 - new model, $2 original model

  ${WDT_BINDIR}/compareModel.sh -oracle_home ${ORACLE_HOME} -output_dir /tmp $1 $2 > ${WDT_OUTPUT} 2>&1
  ret=$?
  if [ $ret -ne 0 ]; then
    trace SEVERE "WDT Compare Model failed:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi

  if [ "true" == "$MII_USE_ONLINE_UPDATE" ] ; then
    if [  ! -f "/tmp/diffed_model.yaml" ] ; then
      if [ -f "/tmp/compare_model_stdout" ] ; then
        trace SEVERE "WDT Compare Model detected 'There are no changes to apply between the old and new models'" \
          "and 'spec.configuration.model.onlineUpdate.enabled' is set to 'true'. This indicates" \
          "there may be incompatible changes for online update," \
          "such as trying to remove an existing attribute." \
          "Please correct the attributes listed below or use offline update:"
        cat /tmp/compare_model_stdout
        exitOrLoop
      else
        if [ ${SECRETS_AND_ENV_CHANGED} -eq 0 ] ; then
          # Merged model and env vars are identical, tell introspectDomain.sh not to run python and short circuit
          trace "Merged models and environment variables are identical, this introspection should be no-op."
          MERGED_MODEL_ENVVARS_SAME="true"
        fi
      fi
    fi
  fi

  #  Checking whether domain, rcu credentials have been changed - needed for offline
  #  and also incompatible changes for online update.

  if [ "${MERGED_MODEL_ENVVARS_SAME}" == "false" ] ; then
    # Generate diffed model update compatibility result, use partial model to avoid loading large model
    local ORACLE_SERVER_DIR=${ORACLE_HOME}/wlserver
    local JAVA_PROPS="-Dpython.cachedir.skip=true ${JAVA_PROPS}"
    local JAVA_PROPS="-Dpython.path=${ORACLE_SERVER_DIR}/common/wlst/modules/jython-modules.jar/Lib ${JAVA_PROPS}"
    local JAVA_PROPS="-Dpython.console= ${JAVA_PROPS} -Djava.security.egd=file:/dev/./urandom"
    local CP=${ORACLE_SERVER_DIR}/server/lib/weblogic.jar
    # Get partial models for sanity check for forbidden attribute change
    local SERVER_OR_SERVERTEMPLATES_NAMES
    SERVER_OR_SERVERTEMPLATES_NAMES=$(jq '{ topology: { Server: (.topology.Server | with_entries(.value = {})),
     ServerTemplate: (if .topology.ServerTemplate then (.topology.ServerTemplate | with_entries(.value = {})) else {} end)
       }} | if .topology.ServerTemplate == {} then del(.topology.ServerTemplate) else . end' $2)
    rc=$?
    if [ $rc -ne 0 ] ; then
      trace SEVERE "Failed to extract server names from original model using jq "$rc
      exitOrLoop
    fi
    local PARTIAL_DIFFED_MODEL
    if [ -f /tmp/diffed_model.json ] ; then
      PARTIAL_DIFFED_MODEL=$(jq '{domainInfo: .domainInfo, topology: .topology} | with_entries(select(.value != null))' /tmp/diffed_model.json)
      rc=$?
      if [ $rc -ne 0 ] ; then
        trace SEVERE "Failed to extract domainInfo and topology from delta model using jq "$rc
        exitOrLoop
      fi
    else
      PARTIAL_DIFFED_MODEL="{}"
    fi
    # Use the real wlst.sh and not the operator wrap script, calling jypthon directory has problem understanding WLST boolean false, true etc..
    # eval will fail
    ${ORACLE_HOME}/oracle_common/common/bin/wlst.sh ${SCRIPTPATH}/model-diff.py "${SERVER_OR_SERVERTEMPLATES_NAMES}" "${PARTIAL_DIFFED_MODEL}" > ${WDT_OUTPUT} 2>&1
    if [ $? -ne 0 ] ; then
      trace SEVERE "Failed to interpret models results . Error output:"
      cat ${WDT_OUTPUT}
      exitOrLoop
    fi
  fi

  wdtRotateAndCopyLogFile "${WDT_COMPARE_MODEL_LOG}"

  start_trap

  trace "Exiting diff_model"
}

#
# createPrimordialDomain will create the primordial domain
#

createPrimordialDomain() {
  trace "Entering createPrimordialDomain"
  local create_primordial_tgz=0
  local recreate_domain=0


  if [ -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
    # If there is an existing domain in the cm - this is update in the lifecycle
    # Call WDT validateModel.sh to generate the new merged mdoel
    trace "Checking if security info has been changed"

    generateMergedModel

    # decrypt the merged model from introspect cm
    local DECRYPTED_MERGED_MODEL="/tmp/decrypted_merged_model.json"
    local MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})

    # Maintain backward compatibility - check first byte to see if it is a json file
    # if yes then it is the not a gzipped and encrypted model, just use it
    # else base64d to gzip file and unzip it
    encrypt_decrypt_model "decrypt" ${INTROSPECTCM_MERGED_MODEL}  ${MII_PASSPHRASE} \
      ${DECRYPTED_MERGED_MODEL}

    if [ "{" != $(head -c 1 ${DECRYPTED_MERGED_MODEL}) ] ; then
      base64 -d ${DECRYPTED_MERGED_MODEL} > ${DECRYPTED_MERGED_MODEL}.gz  || exitOrLoop
      rm ${DECRYPTED_MERGED_MODEL}  || exitOrLoop
      gunzip ${DECRYPTED_MERGED_MODEL}.gz  || exitOrLoop
    fi

    if  versionGE ${WDT_VERSION} ${WDT_ONLINE_MIN_VERSION} ; then
      diff_model ${NEW_MERGED_MODEL} ${DECRYPTED_MERGED_MODEL}
    else
      diff_model_v1 ${NEW_MERGED_MODEL} ${DECRYPTED_MERGED_MODEL}
    fi

    if versionGE ${WDT_VERSION} "4.0.0" ; then
      # If this is WDT 4.0 or newer and there is an existing primordial domain and no domain version
      # then it is created from WDT 3.x, force recreate of the domain
      if [ -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] && [ ! -f ${INTROSPECTCM_DOMAIN_WDT_VERSION} ] ; then
        recreate_domain=1
      fi
    fi

    if [ "${MERGED_MODEL_ENVVARS_SAME}" == "false" ] ; then

      diff_rc=$(cat /tmp/model_diff_rc)
      rm ${DECRYPTED_MERGED_MODEL}
      trace "createPrimordialDomain: model diff return code list (can be empty): "${diff_rc}

      local security_info_updated="false"
      local cannot_perform_online_update="false"
      security_info_updated=$(contain_returncode ${diff_rc} ${SECURITY_INFO_UPDATED})
      cannot_perform_online_update=$(contain_returncode ${diff_rc} ${NOT_FOR_ONLINE_UPDATE})

      if [ ${cannot_perform_online_update} == "true" ] ; then
        trace SEVERE \
          "The Domain resource specified 'spec.configuration.model.onlineUpdate.enabled=true'," \
          "but there are unsupported model changes for online update. Examples of unsupported" \
          "changes include: changing ListenPort, ListenAddress, SSL, changing top level Topology attributes," \
          "or deleting a ServerTemplate."
        exitOrLoop
      fi

      # recreate the domain if there is an unsafe security update such as admin password update or security roles

      # Always use the schema password in RCUDbInfo.  Since once the password is updated by the DBA.  The
      # RCU cache table SCHEMA_COMPONENT_INFO stored password will never be correct,  and subsequently any
      # other updates such as admin credentials or security roles that caused the re-create of the primordial
      # domain will fail since without this flag set, defaults is to use the RCU cached info. (aka. wlst
      # getDatabaseDefaults).
      #
      if [ ${security_info_updated} == "true" ] ; then
        recreate_domain=1
        if [ ${WDT_DOMAIN_TYPE} == "JRF" ] ; then
          UPDATE_RCUPWD_FLAG="-updateRCUSchemaPassword"
        fi
      fi

      # if the domain is JRF and the schema password has been changed. Set this so that the changes are persisted
      # in the primordial domain.

      local rcu_password_updated="false"
      rcu_password_updated=$(contain_returncode ${diff_rc} ${RCU_PASSWORD_CHANGED})
      if [ ${WDT_DOMAIN_TYPE} == "JRF" ] && [ ${rcu_password_updated} == "true" ] ; then
          recreate_domain=1
          UPDATE_RCUPWD_FLAG="-updateRCUSchemaPassword"
      fi
    fi

  fi

  # If there is no primordial domain or needs to recreate one due to security changes
  trace "recreate domain "${recreate_domain}
  if [ ! -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] || [ ${recreate_domain} -eq 1 ]; then

    if [ "true" == "$MII_USE_ONLINE_UPDATE" ] \
       && [ "true" == "${security_info_updated}" ] \
       && [  ${recreate_domain} -eq 1 ] ; then
      trace SEVERE "There are unsupported security realm related changes to a Model In Image model and" \
        "'spec.configuration.model.onlineUpdate.enabled=true'; WDT currently does not" \
        "support online changes for most security realm related mbeans. Use offline update" \
        "to update the domain by setting 'domain.spec.configuration.model.onlineUpdate.enabled'" \
        "to 'false' and trying again."
      exitOrLoop
    fi

    trace "No primordial domain or need to create again because of changes require domain recreation"

    wdtCreatePrimordialDomain
    create_primordial_tgz=1
    MII_USE_ONLINE_UPDATE=false
  fi

  # tar up primordial domain with em.ear if it is there.  The zip will be added to the introspect config map by the
  # introspectDomain.py

  if [ ${create_primordial_tgz} -eq 1 ]; then
    empath=""
    if [ "${WDT_DOMAIN_TYPE}" != "WLS" ] ; then
      empath=$(grep "/em.ear" ${DOMAIN_HOME}/config/config.xml | grep -oPm1 "(?<=<source-path>)[^<]+")
    fi

    # Before targz it, we encrypt the SerializedSystemIni.dat, first save the original

    cp ${DOMAIN_HOME}/security/SerializedSystemIni.dat /tmp/sii.dat.saved

    local MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})
    encrypt_decrypt_domain_secret "encrypt" ${DOMAIN_HOME} ${MII_PASSPHRASE}

    if [[ "${KUBERNETES_PLATFORM^^}" == "OPENSHIFT" ]]; then
      # Operator running on Openshift platform - change file permissions in the DOMAIN_HOME dir to give
      # group same permissions as user .
      chmod -R g=u ${DOMAIN_HOME} || return 1
    fi

    tar -pczf ${LOCAL_PRIM_DOMAIN_ZIP} --exclude ${DOMAIN_HOME}/wlsdeploy --exclude ${DOMAIN_HOME}/sysman/log  \
    --exclude ${DOMAIN_HOME}/lib --exclude ${DOMAIN_HOME}/backup_config ${empath} ${DOMAIN_HOME}/*

    # Put back the original one so that update can continue
    mv  /tmp/sii.dat.saved ${DOMAIN_HOME}/security/SerializedSystemIni.dat

  fi

  trace "Exiting createPrimordialDomain"

}

# check for secure production mode for upgrade scenario

checkSecureModeForUpgrade() {
    trace "Checking existing domain configuration "
    local cur_wl_ver="`getWebLogicVersion`"
    local exp_wl_ver="14.1.2.0.0"
    trace "Current pod version " $cur_wl_ver
    # Only do this if the wls version in the pod is >= 14.1.2.0
    if versionGE "${cur_wl_ver}" "${exp_wl_ver}" ; then
      trace "Checking if upgrade to 14.1.2.0 or higher needs model patch"
      mkdir /tmp/miiupgdomain
      cd /tmp/miiupgdomain && base64 -d ${PRIMORDIAL_DOMAIN_ZIPPED} > ${LOCAL_PRIM_DOMAIN_ZIP}.tmp && tar -pxzf ${LOCAL_PRIM_DOMAIN_ZIP}.tmp
      createFolder "/tmp/miiupgdomain${DOMAIN_HOME}/lib" "This is the './lib' directory within directory 'domain.spec.domainHome'." || exitOrLoop
      local MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})
      encrypt_decrypt_domain_secret "decrypt" /tmp/miiupgdomain${DOMAIN_HOME} ${MII_PASSPHRASE}
      cd /tmp/miiupgdomain && base64 -d ${WLSDOMAIN_CONFIG_ZIPPED} > ${LOCAL_WLSDOMAIN_CONFIG_ZIP}.tmp && tar -pxzf ${LOCAL_WLSDOMAIN_CONFIG_ZIP}.tmp
      # reading existing domain to determine what the secure mode should be whether it is set or by default.
      # a file is written to a /tmp/mii_domain_upgrade.txt containing the status of SecureModeEnabled.
      ${SCRIPTPATH}/wlst.sh ${SCRIPTPATH}/mii-domain-upgrade.py /tmp/miiupgdomain$DOMAIN_HOME || exitOrLoop
      # cd to an existing dir since we are deleting the /tmp/miiupgdomain
      cd /
      if [ -f /tmp/mii_domain_upgrade.txt ] && [ $(grep -i False /tmp/mii_domain_upgrade.txt | wc -l ) -gt 0 ] ; then
        if [ -f /tmp/mii_domain_before14120.txt ] && [ $(grep -i True /tmp/mii_domain_before14120.txt | wc -l ) -gt 0 ] ; then
          # Set this so that the upgrade image only scenario 12.2.1.4 to 14.1.2 will recreate the domain
          trace "Domain version is earlier than 14.1.2, upgrade only image to 14.1.2 detected"
          DISABLE_SM_FOR_12214_NONSM_UPG=1
        fi
      fi
      rm -fr /tmp/miiupgdomain
    fi
}


#
# Generate model from wdt artifacts
#
generateMergedModel() {
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it
  trace "Entering generateMergedModel"
  stop_trap

  export __WLSDEPLOY_STORE_MODEL__="${NEW_MERGED_MODEL}"

  local wdtArgs=""
  wdtArgs+=" -oracle_home ${ORACLE_HOME}"
  wdtArgs+=" ${model_list} ${archive_list} ${variable_list}"
  wdtArgs+=" -domain_type ${WDT_DOMAIN_TYPE}"

  trace "About to call '${WDT_BINDIR}/validateModel.sh ${wdtArgs}'."
  ${WDT_BINDIR}/validateModel.sh ${wdtArgs} > ${WDT_OUTPUT} 2>&1
  ret=$?
  if [ $ret -ne 0 ]; then
    trace SEVERE "Model in Image: the WDT validate model tool detected an error with the fully merged model:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi

  wdtRotateAndCopyLogFile "${WDT_VALIDATE_MODEL_LOG}"

  # restore trap
  start_trap
  trace "Exiting generateMergedModel"
}


# wdtCreatePrimordialDomain
# Create the actual primordial domain using WDT
#

wdtCreatePrimordialDomain() {
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it
  trace "Entering wdtCreatePrimordialDomain"
  stop_trap

  export __WLSDEPLOY_STORE_MODEL__=1

  if [ "JRF" == "$WDT_DOMAIN_TYPE" ] ; then
    if [ -z "${OPSS_FLAGS}" ] ; then
      trace INFO "An OPSS wallet was not supplied for the Model in Image JRF domain in its " \
        "'spec.configuration.opss.walletFileSecret' attribute; therefore, it's assumed that this is the first time " \
        "the OPSS RCU database is being accessed by the domain, so a schema and a wallet file will be created. " \
        "Consult the Model in Image documentation for instructions about preserving the OPSS wallet file."
    else
      trace "Creating JRF Primordial Domain"
    fi
  fi

  local wdtArgs=""
  wdtArgs+=" -oracle_home ${ORACLE_HOME}"
  wdtArgs+=" -domain_home ${DOMAIN_HOME}"
  wdtArgs+=" ${model_list} ${archive_list} ${variable_list}"
  wdtArgs+=" -domain_type ${WDT_DOMAIN_TYPE}"
  wdtArgs+=" ${OPSS_FLAGS}"
  wdtArgs+=" ${UPDATE_RCUPWD_FLAG}"

  trace "About to call '${WDT_BINDIR}/createDomain.sh ${wdtArgs}'."

  expandWdtArchiveCustomDir

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
    # We need to prevent retries with a "FatalIntrospectorError" because JRF without the OPSS_FLAGS indicates
    # a likely attempt to initialize the RCU DB schema for this domain, and we don't want to retry when this fails
    # without admin intervention (retrying can compound the problem and obscure the original issue).
    #
    if [ "JRF" == "$WDT_DOMAIN_TYPE" ] && [ -z "${OPSS_FLAGS}" ] ; then
      trace SEVERE "Model in Image: FatalIntrospectorError: WDT Create Domain Failed, return ${ret}. " \
        ${FATAL_JRF_INTROSPECTOR_ERROR_MSG}
    else
      trace SEVERE "Model in Image: WDT Create Primordial Domain Failed, ret=${ret}"
    fi
    cat ${WDT_OUTPUT}
    exitOrLoop
  else
    trace "WDT Create Domain Succeeded, ret=${ret}:"
    cat ${WDT_OUTPUT}
    if [ "JRF" == "$WDT_DOMAIN_TYPE" ]; then
      CREATED_JRF_PRIMODIAL="true"
    fi
  fi

  wdtRotateAndCopyLogFile "${WDT_CREATE_DOMAIN_LOG}"
  wdtRotateAndCopyOutFile

  # restore trap
  start_trap
  trace "Exiting wdtCreatePrimordialDomain"

}

#
# wdtUpdateModelDomain  use WDT to update the model domain over the primordial domain
#

wdtUpdateModelDomain() {

  trace "Entering wdtUpdateModelDomain"
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it

  stop_trap
  # make sure wdt create write out the merged model to a file in the root of the domain
  export __WLSDEPLOY_STORE_MODEL__=1

  local pod_version=$(getWebLogicVersion)
  local config_version=$(grep '<domain-version>' $DOMAIN_HOME/config/config.xml | sed -n 's/.*<domain-version>\(.*\)<\/domain-version>.*/\1/p')
  local major_pod_version=$(echo $pod_version | cut -d'.' -f1)
  local major_config_version=$(echo $config_version | cut -d'.' -f1)

  # Legacy JRF checks
  if [ "${WDT_DOMAIN_TYPE}" == "JRF" ] ; then
    if versionGT $major_pod_version $major_config_version  ; then
      trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
        " is 'FromModel' and the 'spec.configuration.model.domainType' is 'JRF';" \
        " the domain is configured with WebLogic Server version $config_version" \
        ", and the WebLogic server version in the introspector pod is $pod_version." \
        " You cannot update an existing JRF domain using a WebLogic server which has a major version" \
        " that is higher than the existing domain.  Note: The JRF domain for Model in Image has been deprecated," \
        " you should use Domain on Persistent Volume instead."
      exitOrLoop
    fi
  fi

  if [ "${WDT_DOMAIN_TYPE}" == "WLS" ] ; then
    if versionGT $major_pod_version $major_config_version  ; then
      if [ ! -z ${MII_RUNNING_SERVERS_STATES} ] ; then
        trace SEVERE "The domain resource 'spec.domainHomeSourceType'" \
          " is 'FromModel' and the 'spec.configuration.model.domainType' is 'WLS';" \
          " the domain is configured with WebLogic Server version $config_version" \
          ", and the WebLogic server version in the introspector pod is $pod_version." \
          " When updating an existing WLS domain with a new version of WebLogic Server.  All servers in" \
          " the entire domain must be shut down first. The following servers are not in SHUTDOWN state:" \
          " ${MII_RUNNING_SERVERS_STATES}"
        exitOrLoop
      fi
    fi
  fi

  local wdtArgs=""
  wdtArgs+=" -oracle_home ${ORACLE_HOME}"
  wdtArgs+=" -domain_home ${DOMAIN_HOME}"
  wdtArgs+=" ${model_list} ${archive_list} ${variable_list}"
  wdtArgs+=" -domain_type ${WDT_DOMAIN_TYPE}"
  wdtArgs+=" ${UPDATE_RCUPWD_FLAG}"

  trace "About to call '${WDT_BINDIR}/updateDomain.sh ${wdtArgs}'."

  expandWdtArchiveCustomDir

  ${WDT_BINDIR}/updateDomain.sh ${wdtArgs} > ${WDT_OUTPUT} 2>&1
  ret=$?

  if [ $ret -ne 0 ]; then
    if [ "true" == "${CREATED_JRF_PRIMODIAL}" ] ; then
      trace SEVERE "Model in Image: FatalIntrospectorError: WDT Update Domain Failed, return ${ret}. " \
        ${FATAL_JRF_INTROSPECTOR_ERROR_MSG}
    else
      trace SEVERE "WDT Update Domain command Failed:"
    fi
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi

  # update the wallet
  if [ ! -z ${UPDATE_RCUPWD_FLAG} ]; then
    trace "Updating wallet because schema password changed"
    gunzip ${LOCAL_PRIM_DOMAIN_ZIP}
    if [ $? -ne 0 ] ; then
      trace SEVERE "WDT Update Domain failed: failed to upzip primordial domain"
      exitOrLoop
    fi
    tar uf ${LOCAL_PRIM_DOMAIN_TAR} ${DOMAIN_HOME}/config/fmwconfig/bootstrap/cwallet.sso
    if [ $? -ne 0 ] ; then
      trace SEVERE "WDT Update Domain failed: failed to tar update wallet file"
      exitOrLoop
    fi
    gzip ${LOCAL_PRIM_DOMAIN_TAR}
    if [ $? -ne 0 ] ; then
      trace SEVERE "WDT Update Domain failed: failed to zip up primordial domain"
      exitOrLoop
    fi
  fi

  # This is the complete model and used for life-cycle comparision, encrypt this before storing in
  # config map by the operator
  #
  local MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})

  captureBinLibAdded

  gzip ${DOMAIN_HOME}/wlsdeploy/domain_model.json || exitOrLoop
  base64 ${DOMAIN_HOME}/wlsdeploy/domain_model.json.gz > ${DOMAIN_HOME}/wlsdeploy/domain_model.json.b64 || exitOrLoop
  encrypt_decrypt_model "encrypt" ${DOMAIN_HOME}/wlsdeploy/domain_model.json.b64 ${MII_PASSPHRASE} \
    ${DOMAIN_HOME}/wlsdeploy/domain_model.json

  wdtRotateAndCopyLogFile "${WDT_UPDATE_DOMAIN_LOG}"

  # restore trap
  start_trap
  trace "Exiting wdtUpdateModelDomain"
}

captureBinLibAdded() {
  local BINLIBDIR_NAME="/tmp/binlibdir.txt"
  find $DOMAIN_HOME/bin -maxdepth 1 -type f | sed  "s|$DOMAIN_HOME/bin|wlsdeploy/domainBin|g" > $BINLIBDIR_NAME
  find $DOMAIN_HOME/lib -maxdepth 1 -type f | sed  "s|$DOMAIN_HOME/lib|wlsdeploy/domainLibraries|g" >> $BINLIBDIR_NAME
}

wdtHandleOnlineUpdate() {

  trace "Entering wdtHandleOnlineUpdate"
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it
  stop_trap
  if [ -z ${MII_USE_ONLINE_UPDATE} ] || [ "false" == "${MII_USE_ONLINE_UPDATE}" ] || [ ! -f /tmp/diffed_model.yaml ] ; then
      # no op for offline use case or no change in model with new image
      trace "Domain resource specified 'domain.spec.configuration.model.onlineUpdate=false' or not defined or no " \
        " merged model is the same, no need for online update."
      trace "Exiting wdtHandleOnlineUpdate"
      return
  fi
  local ret=0

  # We need to extract all the archives, WDT online checks for file existence
  # even for delete
  #
  createFolder "${DOMAIN_HOME}/lib" "This is the './lib' directory within directory 'domain.spec.domainHome'." || exitOrLoop
  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip")
    do
        # expand the archive domain libraries to the domain lib, 11 is caution when zip entry doesn't exists
        cd ${DOMAIN_HOME}/lib || exitOrLoop
        unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/domainLibraries/*
        ret=$?
        if [ $ret -ne 0 ] && [ $ret -ne 11 ] ; then
          trace SEVERE  "Domain Source Type is FromModel, error in extracting domainLibraries " \
          "${IMG_ARCHIVES_ROOTDIR}/${file}"
          exitOrLoop
        fi

        # expand the domain bin, in update case user may only update a file in the domainBin archive, 11 is caution when
        # zip entry doesn't exists
        cd ${DOMAIN_HOME}/bin || exitOrLoop
        unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/domainBin/*
        ret=$?
        if [ $ret -ne 0 ] && [ $ret -ne 11 ] ; then
          trace SEVERE  "Domain Source Type is FromModel, error in extracting domainBin " \
          "${IMG_ARCHIVES_ROOTDIR}/${file}"
          exitOrLoop
        fi

        # expand the archive apps and shared lib to the wlsdeploy/* directories
        # the config.xml is referencing them from that path

        cd ${DOMAIN_HOME} || exitOrLoop
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/

        if [ $? -ne 0 ] ; then
          trace SEVERE "Error extracting application archive '${IMG_ARCHIVES_ROOTDIR}/${file}'"
          exitOrLoop
        fi


    done


  # Save off the encrypted model
  cp ${DOMAIN_HOME}/wlsdeploy/domain_model.json /tmp/encrypted_merge_model.json
  local admin_user=$(cat /weblogic-operator/secrets/username)
  local admin_pwd=$(cat /weblogic-operator/secrets/password)

  if [ -z ${AS_SERVICE_NAME} ] || [ -z ${ADMIN_PORT} ] ; then
    trace SEVERE "Cannot find admin service name or port"
    exitOrLoop
  fi

  local admin_url
  if [ -z "${ADMIN_PORT_SECURE}" ] ; then
    admin_url="t3://${AS_SERVICE_NAME}:${ADMIN_PORT}"
  else
    admin_url="t3s://${AS_SERVICE_NAME}:${ADMIN_PORT}"
  fi
  echo ${admin_pwd} | ${WDT_BINDIR}/updateDomain.sh -oracle_home ${MW_HOME} \
   -admin_url ${admin_url} -admin_user ${admin_user} -model_file \
   /tmp/diffed_model.yaml -domain_home ${DOMAIN_HOME} ${archive_list} \
   -discard_current_edit -output_dir /tmp  >  ${WDT_OUTPUT} 2>&1

  ret=$?

  trace "Completed online update="${ret}
  if [ ${ret} -eq ${PROG_RESTART_REQUIRED} ] ; then
    write_updatedresult ${ret}
    write_non_dynamic_changes_text_file
  elif [ ${ret} -ne 0 ] ; then
    trace SEVERE "Online update failed" \
       "(the Domain resource specified 'spec.configuration.model.onlineUpdate.enabled=true')." \
       "Depending on the type of failure, the model may have an unsupported change," \
       "you may need to try an offline update, or you may need to shutdown the entire domain and then restart it."
    cat ${WDT_OUTPUT}
    write_updatedresult ${ret}
    exitOrLoop
  else
    write_updatedresult ${ret}
  fi

  # Restore encrypted merge model otherwise the on in the domain will be the diffed model

  cp  /tmp/encrypted_merge_model.json ${DOMAIN_HOME}/wlsdeploy/domain_model.json

  wdtRotateAndCopyLogFile ${WDT_UPDATE_DOMAIN_LOG} 

  trace "wrote updateResult"

  start_trap
  trace "Exiting wdtHandleOnlineUpdate"

}

write_updatedresult() {
    # The >>> updatedomainResult is used in the operator code
    trace ">>>  updatedomainResult=${1}"
}

write_non_dynamic_changes_text_file() {
    # Containing text regarding the non dynmaic mbean details
    if [ -f /tmp/non_dynamic_changes.file ] ; then
      echo ">>> /tmp/non_dynamic_changes.file"
      cat /tmp/non_dynamic_changes.file
      echo ">>> EOF"
    fi
}

contain_returncode() {
  if echo ",$1," | grep -q ",$2,"
  then
    echo "true"
  else
    echo "false"
  fi
}

#
# Encrypt WDT model (Full encryption)
#
# parameter:
#   1 -  action (encrypt| decrypt)
#   2 -  input file
#   3 -  password
#   4 -  output file
#
encrypt_decrypt_model() {
  trace "Entering encrypt_wdtmodel $1"

  local ORACLE_SERVER_DIR=${ORACLE_HOME}/wlserver
  local JAVA_PROPS="-Dpython.cachedir.skip=true ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.path=${ORACLE_SERVER_DIR}/common/wlst/modules/jython-modules.jar/Lib ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.console= ${JAVA_PROPS} -Djava.security.egd=file:/dev/./urandom"
  local CP=${ORACLE_SERVER_DIR}/server/lib/weblogic.jar:${WDT_BINDIR}/../lib/weblogic-deploy-core.jar
  ${JAVA_HOME}/bin/java -cp ${CP} \
    ${JAVA_PROPS} \
    org.python.util.jython \
    ${SCRIPTPATH}/model-encryption-util.py $1 "$(cat $2)" $3 $4 > ${WDT_OUTPUT} 2>&1
  rc=$?
  if [ $rc -ne 0 ]; then
    trace SEVERE "Failed to '$1' domain model. Check to see if the secret" \
    "referenced in the 'spec.configuration.model.runtimeEncryptionSecret' domain resource field" \
    "has been changed since the" \
    "creation of the domain. You can either reset the password to the original one and try again" \
    "or shutdown the entire domain and restart it." \
    "Failure output:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi

  trace "Exiting encrypt_wdtmodel $1"
}

# encrypt_decrypt_domain_secret
# parameter:
#   1 - action (encrypt|decrypt)
#   2 -  domain home
#   3 -  password
#   4 -  output file

encrypt_decrypt_domain_secret() {
  trace "Entering encrypt_decrypt_domain_secret $1"
  # Do not use trap for this startServer.sh fail for some not zero function call

  local tmp_output="/tmp/tmp_encrypt_decrypt_output.file"
  if [ "$1" == "encrypt" ] ; then
    base64 $2/security/SerializedSystemIni.dat > /tmp/secure.ini
  else
    cp $2/security/SerializedSystemIni.dat  /tmp/secure.ini
  fi

  #
  local ORACLE_SERVER_DIR=${ORACLE_HOME}/wlserver
  local JAVA_PROPS="-Dpython.cachedir.skip=true ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.path=${ORACLE_SERVER_DIR}/common/wlst/modules/jython-modules.jar/Lib ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.console= ${JAVA_PROPS} -Djava.security.egd=file:/dev/./urandom"
  local CP=${ORACLE_SERVER_DIR}/server/lib/weblogic.jar:${WDT_BINDIR}/../lib/weblogic-deploy-core.jar
  ${JAVA_HOME}/bin/java -cp ${CP} \
    ${JAVA_PROPS} \
    org.python.util.jython \
    ${SCRIPTPATH}/model-encryption-util.py $1 "$(cat /tmp/secure.ini)" $3 ${tmp_output} > ${WDT_OUTPUT} 2>&1
  rc=$?
  if [ $rc -ne 0 ]; then
    trace SEVERE "Failed to '$1' domain model. Check to see if the secret" \
    "referenced in the 'spec.configuration.model.runtimeEncryptionSecret' domain resource field" \
    "has been changed since the" \
    "creation of the domain. You can either reset the password to the original one and try again" \
    "or shutdown the entire domain and restart it." \
    "Failure output:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi

  if [ "$1" == "decrypt" ] ; then
    base64 -d ${tmp_output} > $2/security/SerializedSystemIni.dat
  else
    cp ${tmp_output} $2/security/SerializedSystemIni.dat
  fi
  rm ${tmp_output}
  trace "Exiting encrypt_decrypt_domain_secret"
}

# restore App Libs and others from the archive
restoreAppAndLibs() {

  createFolder "${DOMAIN_HOME}/lib" "This is the './lib' directory within DOMAIN_HOME directory 'domain.spec.domainHome'." || return 1
  local WLSDEPLOY_DOMAINLIB="wlsdeploy/domainLibraries"
  local TMP_EXTRACT_LIST=""
  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip")
    do

        # expand the archive domain libraries to the domain lib, 11 is caution when zip entry doesn't exists
        cd ${DOMAIN_HOME}/lib || exitOrLoop
        if [ -f $DOMAIN_BIN_LIB_LIST ] ; then
          TMP_EXTRACT_LIST=$(awk '{print $0}' <<< $(grep "wlsdeploy/domainLibraries" $DOMAIN_BIN_LIB_LIST))
          if [ -n "$TMP_EXTRACT_LIST" ] ; then
            unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} $TMP_EXTRACT_LIST
          fi
        else
          unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/domainLibraries/*
        fi
        ret=$?
        if [ $ret -ne 0 ] && [ $ret -ne 11 ] ; then
          trace SEVERE  "Domain Source Type is FromModel, error in extracting domainLibraries " \
          "${IMG_ARCHIVES_ROOTDIR}/${file}"
          exitOrLoop
        fi

        # expand the domain bin, in update case user may only update a file in the domainBin archive, 11 is caution when
        # zip entry doesn't exists
        cd ${DOMAIN_HOME}/bin || exitOrLoop
        if [ -f $DOMAIN_BIN_LIB_LIST ] ; then
          TMP_EXTRACT_LIST=$(awk '{print $0}' <<< $(grep "wlsdeploy/domainBin" $DOMAIN_BIN_LIB_LIST))
          if [ -n "$TMP_EXTRACT_LIST" ] ; then
            unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} $TMP_EXTRACT_LIST
          fi
        else
          unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/domainBin/*
        fi

        #unzip -jo ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/domainBin/*
        ret=$?
        if [ $ret -ne 0 ] && [ $ret -ne 11 ] ; then
          trace SEVERE  "Domain Source Type is FromModel, error in extracting domainBin " \
          "${IMG_ARCHIVES_ROOTDIR}/${file}"
          exitOrLoop
        fi

        # expand the archive apps, shared lib and other wlsdeploy/* directories
        # exclude directories that are already expanded separately and already included in domain config zip
        #   wlsdeploy/domainBin, wlsdeploy/domainLibraries, config/ - avoid confusion but no harm
        #   wlsdeploy/applications/*.xml since it is included int zipped up domain config
        #   zip, the original xml in the archive may have wdt tokenized notations.
        cd ${DOMAIN_HOME} || return 1
        unzip -o ${IMG_ARCHIVES_ROOTDIR}/${file} -x "wlsdeploy/domainBin/*" "wlsdeploy/domainLibraries/*"  "config/*"
        ret=$?
        if [ $ret -ne 0 ] && [ $ret -ne 11 ] ; then
          trace SEVERE "Domain Source Type is FromModel, error in extracting application archive ${IMG_ARCHIVES_ROOTDIR}/${file}"
          return 1
        fi

    done

}

#
restoreZippedDbWallets() {
  local count=$(find ${DOMAIN_HOME}/wlsdeploy/dbWallets/*/*.zip -type f 2>/dev/null | wc -l)
  if [ "$count" -gt  0 ] ; then
    find ${DOMAIN_HOME}/wlsdeploy/dbWallets/*/*.zip -type f  | xargs -I % sh -c 'unzip -jo % -d $(dirname %) ; rm %'
  fi
}


prepareMIIServer() {

  trace "Model-in-Image: Creating domain home."

  # primordial domain contain the basic structures, security and other fmwconfig templated info
  # domainzip only contains the domain configuration (config.xml jdbc/ jms/)
  # Both are needed for the complete domain reconstruction

  if [ ! -f /weblogic-operator/introspector/primordial_domainzip.secure ] ; then
    trace SEVERE "Domain Source Type is FromModel, the primordial model archive is missing, cannot start server"
    return 1
  fi

  if [ ! -f /weblogic-operator/introspector/domainzip.secure ] ; then
    trace SEVERE  "Domain type is FromModel, the domain configuration archive is missing, cannot start server"
    return 1
  fi

  trace "Model-in-Image: Restoring primordial domain"
  restorePrimordialDomain || return 1

  trace "Model-in-Image: Restore domain secret"
  # decrypt the SerializedSystemIni first
  if [ -f ${RUNTIME_ENCRYPTION_SECRET_PASSWORD} ] ; then
    MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})
  else
    trace SEVERE "Domain Source Type is 'FromModel' which requires specifying a runtimeEncryptionSecret " \
    "in your domain resource and deploying this secret with a 'password' key, but the secret does not have this key."
    return 1
  fi
  encrypt_decrypt_domain_secret "decrypt" ${DOMAIN_HOME} ${MII_PASSPHRASE}

  # We restore the app and libs from the archive first,  the domain zip may contain any standalone application
  # modules under wlsdeploy/applications/*.xml.  In the next step, if any standalone application module exists collected
  # during introspection, it will overwrite the tokenized version in the archive.
    
  trace "Model-in-Image: Restoring apps and libraries"
  restoreAppAndLibs || return 1

  trace "Model-in-Image: Restore domain config"
  restoreDomainConfig || return 1

  trace "Model-in-image: Restore dbWallets zip"

  restoreZippedDbWallets || return 1
  return 0
}

cleanup_mii() {
  rm -f /tmp/*.md5 /tmp/*.gz /tmp/*.ini /tmp/*.json
}

logSevereAndExit() {
  trace SEVERE "cp '$1' failed"
  exitOrLoop
}

# Function to expand the WDT custom/wallet folders from the archive before calling update domain.
# The domain may be created by WDT 3 and older archive format, restore any entries prior to update
#  new 4.0 entry paths are config/**
expandWdtArchiveCustomDir() {
  cd ${DOMAIN_HOME} || exitOrLoop
  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip")
    do
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/custom
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/dbWallets
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} config/wlsdeploy/custom
    done

  restoreZippedDbWallets
  trace "Listing domain home directories"
  ls -lR ${DOMAIN_HOME}
}
