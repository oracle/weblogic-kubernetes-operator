#!/usr/bin/env bash
# Copyright (c) 2020, 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

TEST_OPERATOR_ROOT=/tmp/test/weblogic-operator
setUp() {
  DISALLOW=
  PWD=/no/where/special
  DOMAIN_HOME=${TEST_OPERATOR_ROOT}/domain/home

  INTROSPECTOR_MAP=${TEST_OPERATOR_ROOT}/introspector
  rm -fR ${TEST_OPERATOR_ROOT}
  mkdir -p ${TEST_OPERATOR_ROOT}/introspector ${DOMAIN_HOME}/wlsdeploy/applications
  echo "<ignored>" > $INTROSPECTOR_MAP/domainzip.secure
  echo "<ignored>" > $INTROSPECTOR_MAP/primordial_domainzip.secure

  INTROSPECTCM_WLS_VERSION=${INTROSPECTOR_MAP}/wls.version
  CURRENT_WLS_VERSION=14.1.2.0.0
  MII_RUNNING_SERVERS_STATES=
  WDT_DOMAIN_TYPE=WLS
  TAR_APPEND_ARGS=
  TAR_CREATE_ARGS=
  TAR_DEMO_EXTRACT_ARGS=
  TAR_LISTING=
  GZIP_ARGS=
  UNZIP_APP_ARGS=
}

testRestoreDomainConfig_failsIfUnableToCDToRoot() {
  DISALLOW="CD"

  restoreDomainConfig

  assertEquals "should have failed to cd to /" '1' "$?"
}

testRestoreDomainConfig_failsIfUnableToDecodeDomainZip() {
  DISALLOW="BASE64"

  restoreDomainConfig

  assertEquals "should have failed to run decode domainzip" '1' "$?"
}

testRestoreDomainConfig_failsIfUnableToUnTarDomain() {
  DISALLOW="TAR"

  restoreDomainConfig

  assertEquals "should have failed to run tar" '1' "$?"
}

testOnRestoreDomainConfig_useRootDirectory() {
  restoreDomainConfig

  assertEquals "should be at '/'" "/" "$PWD"
}

testOnRestoreDomainConfig_whenNoIndexesDefinedCatSingleFile() {
  echo -n "abc" > $INTROSPECTOR_MAP/domainzip.secure

  restoreDomainConfig

  expected="abc"
  actual="$(cat /tmp/domain.secure)"
  assertEquals "$expected" "$actual"
}

testOnRestoreDomainConfig_whenIndexesDefinedCatMultipleFiles() {
  mkdir ${INTROSPECTOR_MAP}-1
  mkdir ${INTROSPECTOR_MAP}-2
  echo "0 2" > $INTROSPECTOR_MAP/domainzip.secure.range
  echo -n "abc" > $INTROSPECTOR_MAP/domainzip.secure
  echo -n "def" > ${INTROSPECTOR_MAP}-1/domainzip.secure
  echo -n "ghi" > ${INTROSPECTOR_MAP}-2/domainzip.secure

  restoreDomainConfig

  expected="abcdefghi"
  actual="$(cat /tmp/domain.secure)"
  assertEquals "$expected" "$actual"
}

testOnRestoreDomainConfig_base64DecodeZip() {
  rm /tmp/domain.tar.gz

  restoreDomainConfig

  actual="$(cat /tmp/domain.tar.gz)"
  assertEquals "/tmp/domain.secure" $actual
}

testOnRestoreDomainConfig_unTarDomain() {
  restoreDomainConfig

  assertEquals "TAR command arguments" "-pxzf /tmp/domain.tar.gz" "$TAR_ARGS"
}

testOnRestoreDomainConfig_makeScriptsExecutable() {
  restoreDomainConfig

  assertEquals "CD command arguments" "u+x ${DOMAIN_HOME}/bin/*.sh ${DOMAIN_HOME}/*.sh" "$CHMOD_ARGS"
}

testOnRestorePrimordialDomain_useRootDirectory() {
  restorePrimordialDomain

  assertEquals "should be at '/'" "/" "$PWD"
}

testOnRestorePrimordialDomain_base64DecodeZip() {
  rm /tmp/domain.tar.gz

  restorePrimordialDomain

  actual="$(cat /tmp/domain.tar.gz)"
  assertEquals "/tmp/domain.secure" $actual
}

testOnRestoreDomainConfig_whenNoIndexesDefinedCatSingleFile() {
  echo -n "abc" > $INTROSPECTOR_MAP/primordial_domainzip.secure

  restorePrimordialDomain

  expected="abc"
  actual="$(cat /tmp/domain.secure)"
  assertEquals "$expected" "$actual"
}

testOnRestorePrimordialDomain_unTarDomain() {
  restorePrimordialDomain

  assertEquals "TAR command arguments" "-pxzf /tmp/domain.tar.gz" "$TAR_ARGS"
}

testRestoreIntrospectorArchive_concatenatesConfigMapParts() {
  mkdir -p ${TEST_OPERATOR_ROOT}/introspectormii ${TEST_OPERATOR_ROOT}/introspectormii-1
  echo -n "abc" > ${TEST_OPERATOR_ROOT}/introspectormii/primordial_domainzip.secure
  echo -n "def" > ${TEST_OPERATOR_ROOT}/introspectormii-1/primordial_domainzip.secure

  restoreIntrospectorArchive "primordial_domainzip.secure" "/" "/tmp/prim_domain.tar.gz"

  assertEquals "abcdef" "$(cat /tmp/domain.secure)"
}

testRestoreDomainDemoPKIs_allowsArchiveWithoutDomainSpecificDemoCertificates() {
  TAR_LISTING="tmp/test/domain/home/security/SerializedSystemIni.dat"

  restoreDomainDemoPKIs "primordial_domainzip.secure"

  assertEquals "archive without domain-specific demo certificates must be accepted" 0 "$?"
  assertEquals "" "${TAR_DEMO_EXTRACT_ARGS}"
}

testRestoreDomainDemoPKIs_extractsDomainSpecificDemoCertificatesWhenPresent() {
  TAR_LISTING="tmp/test/domain/home/security/DemoIdentity.p12
tmp/test/domain/home/security/democacert.der"

  restoreDomainDemoPKIs "primordial_domainzip.secure"

  assertEquals "-pzxvf /tmp/domain.tar.gz -C /tmp -T /tmp/domain-demo-files" \
    "${TAR_DEMO_EXTRACT_ARGS}"
}

testCreateModelDomainArchive_includesProcessedApplicationDescriptors() {
  touch ${DOMAIN_HOME}/wlsdeploy/applications/application.xml
  touch ${DOMAIN_HOME}/wlsdeploy/applications/application.ear

  createModelDomainArchive ""

  assertEquals "-prf /tmp/prim_domain.tar ${DOMAIN_HOME}/wlsdeploy/applications/application.xml" \
    "${TAR_APPEND_ARGS}"
  echo "${TAR_CREATE_ARGS}" | grep -q -- "--exclude=${DOMAIN_HOME}/config/deployments"
  assertEquals "domain deployments must be excluded from the ConfigMap archive" 0 "$?"
  assertEquals "-f /tmp/prim_domain.tar" "${GZIP_ARGS}"
}

testRestoreAppAndLibs_doesNotOverwriteProcessedApplicationDescriptors() {
  mkdir -p ${DOMAIN_HOME}/bin ${DOMAIN_HOME}/lib
  IMG_ARCHIVES_ROOTDIR=/models
  DOMAIN_BIN_LIB_LIST=${TEST_OPERATOR_ROOT}/missing-binlib-list

  restoreAppAndLibs

  echo "${UNZIP_APP_ARGS}" | grep -q -- "wlsdeploy/applications/\*.xml"
  assertEquals "processed application descriptors must not be overwritten" 0 "$?"
}

testCheckMiiDomainUpgradeCompatibility_rejectsMajorWlsUpgradeWithRunningServers() {
  echo "12.2.1.4.0" > "${INTROSPECTCM_WLS_VERSION}"
  MII_RUNNING_SERVERS_STATES="managed-server1:RUNNING"

  checkMiiDomainUpgradeCompatibility

  assertEquals "major WLS upgrade with running servers must be rejected" 1 "$?"
}

testCheckMiiDomainUpgradeCompatibility_allowsMajorWlsUpgradeWhenServersAreShutdown() {
  echo "12.2.1.4.0" > "${INTROSPECTCM_WLS_VERSION}"

  checkMiiDomainUpgradeCompatibility

  assertEquals "major WLS upgrade with all servers shut down must be allowed" 0 "$?"
}

testCheckMiiDomainUpgradeCompatibility_rejectsMajorJrfUpgrade() {
  echo "12.2.1.4.0" > "${INTROSPECTCM_WLS_VERSION}"
  WDT_DOMAIN_TYPE=JRF

  checkMiiDomainUpgradeCompatibility

  assertEquals "major JRF upgrade must be rejected" 1 "$?"
}

testConfigureRcuSchemaPasswordUpdate_setsFlagWhenRcuPasswordChanges() {
  WDT_DOMAIN_TYPE=JRF
  UPDATE_RCUPWD_FLAG=

  configureRcuSchemaPasswordUpdate "1,5" "false"

  assertEquals "-updateRCUSchemaPassword" "${UPDATE_RCUPWD_FLAG}"
}

testConfigureRcuSchemaPasswordUpdate_doesNotSetFlagForWlsDomain() {
  UPDATE_RCUPWD_FLAG=

  configureRcuSchemaPasswordUpdate "5" "true"

  assertEquals "" "${UPDATE_RCUPWD_FLAG}"
}

######################### Mocks for the tests ###############

# simulates the shell 'cd' command. Will fail on CD to forbidden location, or set PWD
# otherwise
cd() {
  if [ "$DISALLOW" = "CD" ]; then
    return 1
  else
    PWD=$1
  fi
}

base64() {
  if [ "$DISALLOW" = "BASE64" ]; then
    return 1
  elif [ "$1" != "-d" ]; then
    return 1
  else
    echo "$2"
  fi
}

source() {
  if [ "$DISALLOW" = "SOURCE" ]; then
    return 1
  else
    SOURCE_ARGS="$*"
  fi
}

tar() {
  if [ "$DISALLOW" = "TAR" ]; then
    return 1
  else
    TAR_ARGS="$*"
    if [ "$1" = "-tzf" ]; then
      echo "${TAR_LISTING}"
    elif [ "$1" = "-pcf" ]; then
      TAR_CREATE_ARGS="$*"
    elif [ "$1" = "-prf" ]; then
      TAR_APPEND_ARGS="$*"
    elif [ "$1" = "-pzxvf" ]; then
      TAR_DEMO_EXTRACT_ARGS="$*"
    fi
  fi
}

chmod() {
  CHMOD_ARGS="$*"
}

gzip() {
  GZIP_ARGS="$*"
}

unzip() {
  if [ "$1" = "-o" ]; then
    UNZIP_APP_ARGS="$*"
  fi
  return 0
}

sort_files() {
  echo "archive.zip"
}

createFolder() {
  mkdir -p "$1"
}

getWebLogicVersion() {
  echo "${CURRENT_WLS_VERSION}"
}

versionGT() {
  [ "$1" -gt "$2" ]
}

trace() {
  TRACE_ARGS="$*"
}

# shellcheck source=src/main/resources/scripts/modelInImage.sh
. ${SCRIPTPATH}/modelInImage.sh

# shellcheck source=target/classes/shunit/shunit2
. ${SHUNIT2_PATH}
