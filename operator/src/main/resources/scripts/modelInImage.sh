#!/usr/bin/env bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script contains the all the function of model in image
# It is used by introspectDomain.sh job and startServer.sh

source ${SCRIPTPATH}/utils.sh

OPERATOR_ROOT=${TEST_OPERATOR_ROOT:-/weblogic-operator}
INTROSPECTCM_IMAGE_MD5="/weblogic-operator/introspectormii/inventory_image.md5"
INTROSPECTCM_CM_MD5="/weblogic-operator/introspectormii/inventory_cm.md5"
INTROSPECTCM_PASSPHRASE_MD5="/weblogic-operator/introspectormii/inventory_passphrase.md5"
INTROSPECTCM_MERGED_MODEL="/weblogic-operator/introspectormii/merged_model.json"
INTROSPECTCM_WLS_VERSION="/weblogic-operator/introspectormii/wls.version"
INTROSPECTCM_JDK_PATH="/weblogic-operator/introspectormii/jdk.path"
INTROSPECTCM_SECRETS_AND_ENV_MD5="/weblogic-operator/introspectormii/secrets_and_env.md5"
PRIMORDIAL_DOMAIN_ZIPPED="/weblogic-operator/introspectormii/primordial_domainzip.secure"
INTROSPECTJOB_IMAGE_MD5="/tmp/inventory_image.md5"
INTROSPECTJOB_CM_MD5="/tmp/inventory_cm.md5"
INTROSPECTJOB_PASSPHRASE_MD5="/tmp/inventory_passphrase.md5"
LOCAL_PRIM_DOMAIN_ZIP="/tmp/prim_domain.tar.gz"
LOCAL_PRIM_DOMAIN_TAR="/tmp/prim_domain.tar"
NEW_MERGED_MODEL="/tmp/new_merged_model.json"
WDT_CONFIGMAP_ROOT="/weblogic-operator/wdt-config-map"
RUNTIME_ENCRYPTION_SECRET_PASSWORD="/weblogic-operator/model-runtime-secret/password"

# we export the opss password file location because it's also used by introspectDomain.py
export OPSS_KEY_PASSPHRASE="/weblogic-operator/opss-walletkey-secret/walletPassword"
OPSS_KEY_B64EWALLET="/weblogic-operator/opss-walletfile-secret/walletFile"
IMG_MODELS_HOME="${WDT_MODEL_HOME:-/u01/wdt/models}"
IMG_MODELS_ROOTDIR="${IMG_MODELS_HOME}"
IMG_ARCHIVES_ROOTDIR="${IMG_MODELS_HOME}"
IMG_VARIABLE_FILES_ROOTDIR="${IMG_MODELS_HOME}"
WDT_ROOT="/u01/wdt/weblogic-deploy"
WDT_OUTPUT="/tmp/wdt_output.log"
WDT_BINDIR="${WDT_ROOT}/bin"
WDT_FILTER_JSON="/weblogic-operator/scripts/model-filters.json"
WDT_CREATE_FILTER="/weblogic-operator/scripts/model-wdt-create-filter.py"
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
MODELS_SAME=3
SECURITY_INFO_UPDATED=4
RCU_PASSWORD_CHANGED=5
NOT_FOR_ONLINE_UPDATE=6
SCRIPT_ERROR=255

WDT_ONLINE_MIN_VERSION="1.9.9"
WDT_OFFLINE_MIN_VERSION="1.7.3"

export WDT_MODEL_SECRETS_DIRS="/weblogic-operator/config-overrides-secrets"
[ ! -d ${WDT_MODEL_SECRETS_DIRS} ] && unset WDT_MODEL_SECRETS_DIRS

#TBD: CREDENTIALS_SECRET_NAME is unexpectedly empty. Maybe that's a regression?
#  export WDT_MODEL_SECRETS_NAME_DIR_PAIRS="__weblogic-credentials__=/weblogic-operator/secrets,__WEBLOGIC-CREDENTIALS__=/weblogic-operator/secrets,${CREDENTIALS_SECRET_NAME}=/weblogic-operator/secret"
#For now:
export WDT_MODEL_SECRETS_NAME_DIR_PAIRS="__weblogic-credentials__=/weblogic-operator/secrets,__WEBLOGIC-CREDENTIALS__=/weblogic-operator/secrets"

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
# If there are any WDT archives changed set ARCHIVE_ZIP_CHANGED=1 (for online update)
#

function compareArtifactsMD5() {

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

function get_opss_key_wallet() {
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

function buildWDTParams_MD5() {
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
       "'spec.configuration.model.walletPasswordSecret' in your domain" \
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
    mkdir -p /tmp/opsswallet
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

function changeTimeoutProperty() {
  if [ ! -z $2 ] ; then
    sed -i "s/\($1=\).*\$/\1$2/" ${WDT_ROOT}/lib/tool.properties || exitOrLoop
  fi
}

function overrideWDTTimeoutValues() {
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

function createWLDomain() {
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
  # Check if modelHome (default /u01/wdt/models) and /u01/wdt/weblogic-deploy exists

  checkDirNotExistsOrEmpty ${IMG_MODELS_HOME}
  checkDirNotExistsOrEmpty ${WDT_BINDIR}

  checkModelDirectoryExtensions
  if [ "true" != "${WDT_BYPASS_WDT_VERSION_CHECK}" ] ; then
    checkWDTVersion
  fi

  # copy the filter related files to the wdt lib

  cp ${WDT_FILTER_JSON} ${WDT_ROOT}/lib/model_filters.json
  cp ${WDT_CREATE_FILTER} ${WDT_ROOT}/lib

  # check to see if any model including changed (or first model in image deploy)
  # if yes. then run create domain again


  local current_version=$(getWebLogicVersion)
  local current_jdkpath=$(readlink -f $JAVA_HOME)
  # check for version:  can only be rolling

  local version_changed=0
  local jdk_changed=0
  local secrets_and_env_changed=0
  trace "current version "${current_version}

  getSecretsAndEnvMD5
  local current_secrets_and_env_md5=$(cat /tmp/secrets_and_env.md5)

  trace "Checking changes in secrets and jdk path"

  if [ -f ${INTROSPECTCM_SECRETS_AND_ENV_MD5} ] ; then
    previous_secrets_and_env_md5=$(cat ${INTROSPECTCM_SECRETS_AND_ENV_MD5})
    if [ "${current_secrets_and_env_md5}" != "${previous_secrets_and_env_md5}" ]; then
      trace "Secrets and env different: old_md5=${previous_secrets_and_env_md5} new_md5=${current_secrets_and_env_md5}"
      secrets_and_env_changed=1
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

  # Set this so that the introspectDomain.sh can decidde to call the python script of not
  DOMAIN_CREATED=0

  # something changed in the wdt artifacts or wls version changed
  # create domain again

  if  [ ${WDT_ARTIFACTS_CHANGED} -ne 0 ] || [ ${jdk_changed} -eq 1 ] \
    || [ ${secrets_and_env_changed} -ne 0 ] ; then

    trace "Need to create domain ${WDT_DOMAIN_TYPE}"
    createModelDomain
    DOMAIN_CREATED=1
  else
    trace "Nothing changed no op"
  fi
  trace "Exiting createWLDomain"
  stop_trap
}

# checkDirNotExistsOrEmpty
#  Test directory exists or empty

function checkDirNotExistsOrEmpty() {
  trace "Entering checkDirNotExistsOrEmpty"

  if [ $# -eq 1 ] ; then
    if [ ! -d $1 ] ; then
      trace SEVERE "Directory '$1' does not exist"
      exitOrLoop
    else
      if [ -z "$(ls -A $1)" ] ; then
        trace SEVERE "Directory '$1' is empty"
        exitOrLoop
      fi
    fi
  fi

  trace "Exiting checkDirNotExistsOrEmpty"
}

# limit the file extensions in the model directories

function checkModelDirectoryExtensions() {
  trace "Entering checkModelDirectoryExtensions"

  cd ${IMG_MODELS_HOME}
  counter=$(ls  -I  "*.yaml" -I "*.zip" -I "*.properties" | wc -l)
  if [ $counter -ne 0 ] ; then
    trace SEVERE "Model image home '${IMG_MODELS_HOME}' contains files with unsupported extensions." \
      "Expected extensions: '.yaml', '.properties', or '.zip'." \
      "Files with unsupported extensions: '$(ls -I "*.yaml" -I "*.zip" -I "*.properties")'"
    exitOrLoop
  fi
  if [ -d ${WDT_CONFIGMAP_ROOT} ] ; then
    cd ${WDT_CONFIGMAP_ROOT}
    counter=$(ls  -I  "*.yaml" -I "*.properties" | wc -l)
    if [ $counter -ne 0 ] ; then
      trace SEVERE "Model 'spec.configuration.model.configMap' contains files with unsupported extensions." \
        "Expected extensions: '.yaml' or '.properties'." \
        "Files with unsupported extensions: '$(ls -I "*.yaml" -I "*.properties")'"
      exitOrLoop
    fi
  fi

  trace "Exiting checkModelDirectoryExtensions"
}

# Check for WDT version

function checkWDTVersion() {
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

  trace "Exiting checkWDTVersion"
}

# getSecretsAndEnvMD5
#
# concatenate all the secrets and env, calculate the md5 and delete the file.
# The md5 is used to determine whether the domain needs to be recreated
# Note: the secrets are two levels indirections, so use find and filter out the ..data
# output:  /tmp/secrets_and_env.md5

function getSecretsAndEnvMD5() {
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

function createModelDomain() {

  trace "Entering createModelDomain"
  createPrimordialDomain

  # if there is a new primordial domain created then use newly created primordial domain otherwise
  # if the primordial domain already in the configmap, restore it
  #

  if [ -f "${LOCAL_PRIM_DOMAIN_ZIP}" ] ; then
    trace "Using newly created domain"
  elif [ -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
    trace "Using existing primordial domain"
    cd / && base64 -d ${PRIMORDIAL_DOMAIN_ZIPPED} > ${LOCAL_PRIM_DOMAIN_ZIP} && tar -xzf ${LOCAL_PRIM_DOMAIN_ZIP}
    # create empty lib since we don't archive it in primordial zip and WDT will fail without it
    mkdir ${DOMAIN_HOME}/lib
    # Since the SerializedSystem ini is encrypted, restore it first
    local MII_PASSPHRASE=$(cat ${RUNTIME_ENCRYPTION_SECRET_PASSWORD})
    encrypt_decrypt_domain_secret "decrypt" ${DOMAIN_HOME} ${MII_PASSPHRASE}
  fi

  wdtUpdateModelDomain

  # This will be a no op if MII_USE_ONLINE_UPDATE is not defined or false
  wdtHandleOnlineUpdate

  trace "Exiting createModelDomain"
}


# Expands into the root directory the MII domain configuration, stored in one or more config maps
function restoreDomainConfig() {
  restoreEncodedTar "domainzip.secure" || return 1

  chmod +x ${DOMAIN_HOME}/bin/*.sh ${DOMAIN_HOME}/*.sh  || return 1
}

# Expands into the root directory the MII primordial domain, stored in one or more config maps
function restorePrimordialDomain() {
  restoreEncodedTar "primordial_domainzip.secure" || return 1
}

# Restores the specified directory, targz'ed and stored in one or more config maps after base 64 encoding
# args:
# $1 the name of the encoded file in the config map
function restoreEncodedTar() {
  cd / || return 1
  cat $(ls ${OPERATOR_ROOT}/introspector*/${1} | sort -t- -k3) > /tmp/domain.secure || return 1
  base64 -d "/tmp/domain.secure" > /tmp/domain.tar.gz || return 1

  tar -xzf /tmp/domain.tar.gz || return 1
}

# This is before WDT compareModel implementation
#
function diff_model_v1() {
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
  return ${rc}
}

# This is WDT compareModel.sh implementation

function diff_model() {
  trace "Entering diff_model"

  export __WLSDEPLOY_STORE_MODEL__=1
  # $1 - new model, $2 original model

  # no need to redirect output, -output_dir for compareModel will generate the output file
  ${WDT_BINDIR}/compareModel.sh -oracle_home ${ORACLE_HOME} -output_dir /tmp $1 $2 > /dev/null 2>&1
  ret=$?
  if [ $ret -ne 0 ]; then
    trace SEVERE "WDT Compare Model failed:"
    cat /tmp/compare_model_stdout
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
        # Model is Identical, but env vars unrelated to the model may have changed (such as JAVA_OPTIONS)
        MII_USE_ONLINE_UPDATE=false
      fi
    fi
  fi

  #
  local ORACLE_SERVER_DIR=${ORACLE_HOME}/wlserver
  local JAVA_PROPS="-Dpython.cachedir.skip=true ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.path=${ORACLE_SERVER_DIR}/common/wlst/modules/jython-modules.jar/Lib ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.console= ${JAVA_PROPS} -Djava.security.egd=file:/dev/./urandom"
  local CP=${ORACLE_SERVER_DIR}/server/lib/weblogic.jar
  ${JAVA_HOME}/bin/java -cp ${CP} \
    ${JAVA_PROPS} \
    org.python.util.jython \
    ${SCRIPTPATH}/model-diff.py $2 > ${WDT_OUTPUT} 2>&1
  if [ $? -ne 0 ] ; then
    trace SEVERE "Failed to compare models. Error output:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi
  trace "Exiting diff_model"
  return ${rc}
}

#
# createPrimordialDomain will create the primordial domain
#

function createPrimordialDomain() {
  trace "Entering createPrimordialDomain"
  local create_primordial_tgz=0
  local recreate_domain=0

  if [  -f ${PRIMORDIAL_DOMAIN_ZIPPED} ] ; then
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
    # RCU cache table SCHEMA_COMPONENT_INFO stored password will never be correct,  and subsequenetly any
    # other updates such as admin credenitals or security roles that caused the re-create of the primordial
    # domain will fail since without this flag set, defaults is to use the RCU cached info. (aka. wlst
    # getDatabaseDefaults).
    #
    if [ ${security_info_updated} == "true" ] ; then
      recreate_domain=1
      if [ ${WDT_DOMAIN_TYPE} == "JRF" ] ; then
        UPDATE_RCUPWD_FLAG="-updateRCUSchemaPassword"
      fi
    fi

    # if the domain is JRF and the schema password has been changed. Set this so that updateDomain will also update
    # the RCU password using the RCUDnbinfo

    local rcu_password_updated="false"
    rcu_password_updated=$(contain_returncode ${diff_rc} ${RCU_PASSWORD_CHANGED})
    if [ ${WDT_DOMAIN_TYPE} == "JRF" ] && [ ${rcu_password_updated} == "true" ] ; then
        UPDATE_RCUPWD_FLAG="-updateRCUSchemaPassword"
    fi
  fi

  # If there is no primordial domain or needs to recreate one due to security changes

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

    tar -pczf ${LOCAL_PRIM_DOMAIN_ZIP} --exclude ${DOMAIN_HOME}/wlsdeploy --exclude ${DOMAIN_HOME}/sysman/log  \
    --exclude ${DOMAIN_HOME}/lib --exclude ${DOMAIN_HOME}/backup_config ${empath} ${DOMAIN_HOME}/*

    # Put back the original one so that update can continue
    mv  /tmp/sii.dat.saved ${DOMAIN_HOME}/security/SerializedSystemIni.dat

  fi

  trace "Exiting createPrimordialDomain"

}

#
# Generate model from wdt artifacts
#
function generateMergedModel() {
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it
  trace "Entering generateMergedModel"
  stop_trap

  export __WLSDEPLOY_STORE_MODEL__="${NEW_MERGED_MODEL}"

  ${WDT_BINDIR}/validateModel.sh -oracle_home ${ORACLE_HOME} ${model_list} \
    ${archive_list} ${variable_list}  -domain_type ${WDT_DOMAIN_TYPE}  > ${WDT_OUTPUT} 2>&1
  ret=$?
  if [ $ret -ne 0 ]; then
    trace SEVERE "Model in Image: the WDT validate model tool detected an error with the fully merged model:"
    cat ${WDT_OUTPUT}
    exitOrLoop
  fi

  # restore trap
  start_trap
  trace "Exiting generateMergedModel"
}


# wdtCreatePrimordialDomain
# Create the actual primordial domain using WDT
#

function wdtCreatePrimordialDomain() {
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
    # We need to prevent retries with a "MII Fatal Error" because JRF without the OPSS_FLAGS indicates
    # a likely attempt to initialize the RCU DB schema for this domain, and we don't want to retry when this fails
    # without admin intervention (retrying can compound the problem and obscure the original issue).
    #
    if [ "JRF" == "$WDT_DOMAIN_TYPE" ] && [ -z "${OPSS_FLAGS}" ] ; then
      trace SEVERE "Model in Image: FatalIntrospectorError: WDT Create Primordial Domain Failed, ret=${ret}"
    else
      trace SEVERE "Model in Image: WDT Create Primordial Domain Failed, ret=${ret}"
    fi
    cat ${WDT_OUTPUT}
    exitOrLoop
  else
    trace "WDT Create Domain Succeeded, ret=${ret}:"
    cat ${WDT_OUTPUT}
  fi

  # restore trap
  start_trap
  trace "Exiting wdtCreatePrimordialDomain"

}

#
# wdtUpdateModelDomain  use WDT to update the model domain over the primordial domain
#

function wdtUpdateModelDomain() {

  trace "Entering wdtUpdateModelDomain"
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it

  stop_trap
  # make sure wdt create write out the merged model to a file in the root of the domain
  export __WLSDEPLOY_STORE_MODEL__=1

  ${WDT_BINDIR}/updateDomain.sh -oracle_home ${ORACLE_HOME} -domain_home ${DOMAIN_HOME} $model_list \
  ${archive_list} ${variable_list}  -domain_type ${WDT_DOMAIN_TYPE}  ${UPDATE_RCUPWD_FLAG}  >  ${WDT_OUTPUT} 2>&1
  ret=$?

  if [ $ret -ne 0 ]; then
    trace SEVERE "WDT Update Domain command Failed:"
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

  gzip ${DOMAIN_HOME}/wlsdeploy/domain_model.json || exitOrLoop
  base64 ${DOMAIN_HOME}/wlsdeploy/domain_model.json.gz > ${DOMAIN_HOME}/wlsdeploy/domain_model.json.b64 || exitOrLoop
  encrypt_decrypt_model "encrypt" ${DOMAIN_HOME}/wlsdeploy/domain_model.json.b64 ${MII_PASSPHRASE} \
    ${DOMAIN_HOME}/wlsdeploy/domain_model.json

  # restore trap
  start_trap
  trace "Exiting wdtUpdateModelDomain"
}

function wdtHandleOnlineUpdate() {

  trace "Entering wdtHandleOnlineUpdate"
  # wdt shell script may return non-zero code if trap is on, then it will go to trap instead
  # temporarily disable it
  stop_trap
  if [ -z ${MII_USE_ONLINE_UPDATE} ] || [ "false" == "${MII_USE_ONLINE_UPDATE}" ] ; then
    # no op for offline use case'
    trace "Domain resource specified 'domain.spec.configuration.model.onlineUpdate=false' or not defined - no op"
    trace "Exiting wdtHandleOnlineUpdate"
    return
  fi

  # We need to extract all the archives, WDT online checks for file existence
  # even for delete
  #
  mkdir -p ${DOMAIN_HOME}/lib || exitOrLoop
  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip")
    do
        # expand the archive domain libraries to the domain lib
        cd ${DOMAIN_HOME}/lib || return exitOrLoop
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/domainLibraries/

        if [ $? -ne 0 ] ; then
          trace SEVERE  "Error extracting domain lib '${IMG_ARCHIVES_ROOTDIR}/${file}'"
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

  local ret=$?

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

  trace "wrote updateResult"

  start_trap
  trace "Exiting wdtHandleOnlineUpdate"

}

function write_updatedresult() {
    # The >>> updatedomainResult is used in the operator code
    trace ">>>  updatedomainResult=${1}"
}

function write_non_dynamic_changes_text_file() {
    # Containing text regarding the non dynmaic mbean details
    if [ -f /tmp/non_dynamic_changes.file ] ; then
      echo ">>> /tmp/non_dynamic_changes.file"
      cat /tmp/non_dynamic_changes.file
      echo ">>> EOF"
    fi
}

function contain_returncode() {
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
function encrypt_decrypt_model() {
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

function encrypt_decrypt_domain_secret() {
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

# prepare mii server

function prepareMIIServer() {

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

  # restore the config zip
  #
  trace "Model-in-Image: Restore domain config"
  restoreDomainConfig || return 1

  # restore the archive apps and libraries
  #
  trace "Model-in-Image: Restoring apps and libraries"

  mkdir -p ${DOMAIN_HOME}/lib
  if [ $? -ne 0 ] ; then
    trace  SEVERE "Domain Source Type is FromModel, cannot create ${DOMAIN_HOME}/lib "
    return 1
  fi
  local WLSDEPLOY_DOMAINLIB="wlsdeploy/domainLibraries"

  for file in $(sort_files ${IMG_ARCHIVES_ROOTDIR} "*.zip")
    do
        # expand the archive domain libraries to the domain lib
        cd ${DOMAIN_HOME}/lib || return 1
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} ${WLSDEPLOY_DOMAINLIB}

        if [ $? -ne 0 ] ; then
          trace SEVERE  "Domain Source Type is FromModel, error in extracting domain libs ${IMG_ARCHIVES_ROOTDIR}/${file}"
          return 1
        fi

        # Flatten the jars to the domain lib root

        if [ -d ${WLSDEPLOY_DOMAINLIB} ] && [ "$(ls -A ${WLSDEPLOY_DOMAINLIB})" ] ; then
          mv ${WLSDEPLOY_DOMAINLIB}/* .
        fi
        rm -fr wlsdeploy/

        # expand the archive apps and shared lib to the wlsdeploy/* directories
        # the config.xml is referencing them from that path

        cd ${DOMAIN_HOME} || return 1
        ${JAVA_HOME}/bin/jar xf ${IMG_ARCHIVES_ROOTDIR}/${file} wlsdeploy/
        if [ $? -ne 0 ] ; then
          trace SEVERE "Domain Source Type is FromModel, error in extracting application archive ${IMG_ARCHIVES_ROOTDIR}/${file}"
          return 1
        fi
        # No need to have domainLibraries in domain home
        rm -fr ${WLSDEPLOY_DOMAINLIB}
    done
  return 0
}

#
# Generic error handler
#
function error_handler() {
    if [ $1 -ne 0 ]; then
        # Use FINE instead of SEVERE, avoid showing in domain status
        trace FINE  "Script Error: There was an error at line: ${2} command: ${@:3:20}"
        stop_trap
        exitOrLoop
    fi
}

function start_trap() {
    set -eE
    trap 'error_handler $? $BASH_LINENO $BASH_COMMAND ' ERR EXIT SIGHUP SIGINT SIGTERM SIGQUIT
}

function stop_trap() {
    trap -  ERR EXIT SIGHUP SIGINT SIGTERM SIGQUIT
    set +eE
}

function cleanup_mii() {
  rm -f /tmp/*.md5 /tmp/*.gz /tmp/*.ini /tmp/*.json
}
