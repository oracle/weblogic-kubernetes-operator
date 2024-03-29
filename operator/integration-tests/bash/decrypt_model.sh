#!/bin/bash
# Copyright (c) 2020, 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
#  This script is to be run inside a model in image pod that has JDK, WebLogic and WDT in it.
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
  trace "Entering encrypt_decrypt_model"
  
  local ORACLE_SERVER_DIR=${ORACLE_HOME}/wlserver
  local WDT_OUTPUT=/tmp/output.log
  if [ -d /tmp/weblogic-deploy ] ; then
    local WDT_HOME=/tmp/weblogic-deploy
  else
    local WDT_HOME=/u01/wdt
  fi

  local JAVA_PROPS="${JAVA_PROPS} -Dpython.cachedir.skip=true"
  local JAVA_PROPS="-Dpython.path=${ORACLE_SERVER_DIR}/common/wlst/modules/jython-modules.jar/Lib ${JAVA_PROPS}"
  local JAVA_PROPS="-Dpython.console= ${JAVA_PROPS} -Dpython.verbose=debug"
  local CP="${ORACLE_SERVER_DIR}/server/lib/weblogic.jar:$WDT_HOME/lib/weblogic-deploy-core.jar"
  ${JAVA_HOME}/bin/java -cp ${CP} \
	  ${JAVA_PROPS} org.python.util.jython /tmp/model-encryption-util.py  $1 $(cat $2) $3 $4 > ${WDT_OUTPUT} 2>&1
  rc=$?
  if [ $rc -ne 0 ]; then
    trace SEVERE "encrypt_decrypt_model failure "
    trace SEVERE "$(cat ${WDT_OUTPUT})"
    exit 1
  fi

  trace "Exiting encrypt_decrypt_model"
}

trace() {
    echo $*
}

encrypt_decrypt_model $*

