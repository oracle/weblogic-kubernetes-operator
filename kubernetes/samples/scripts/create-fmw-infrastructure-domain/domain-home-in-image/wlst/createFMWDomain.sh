#!/bin/bash
#
#Copyright (c) 2021, Oracle and/or its affiliates.
#Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# Function to parse a properties file set environment variables
# $1 - Input properties filename
function parseProperties {
  while IFS='=' read -r key value
  do
    if [ -n "${value}" ] && [ "${key}" != "\#*" ]; then
      eval ${key}="\${value}"
    fi
  done < $1
}

echo "Info: Domain Home is: " $DOMAIN_HOME

if [ -z "${DOMAIN_HOME}" ]; then
  echo "Error: DOMAIN_HOME not set."
  exit 1
fi

if ! touch $DOMAIN_HOME/test-file; then
  echo "Error: No permission to create a file in domain home '$DOMAIN_HOME'. id=$(id). Parent dir contents:"
  ls -l $(dirname $DOMAIN_HOME)
  exit 1
fi
rm -f $DOMAIN_HOME/test-file

dfiles="$(ls -A $DOMAIN_HOME 2>&1)"
if [ ! -z $dfiles ]; then
  echo "Error: Domain home '$DOMAIN_HOME' must be empty before we run the domain home creation script."
  echo "$dfiles"
  exit 1
fi

# Verify that the properties file exist
DOMAIN_PROPERTIES_FILE=${PROPERTIES_FILE_DIR}/domain.properties
if [ ! -e "${DOMAIN_PROPERTIES_FILE}" ]; then
  echo "Error: Property file '${DOMAIN_PROPERTIES_FILE}' not found. A property file with the username and password needs to be supplied."
  exit 1
fi

# Get Username
USER=$(awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep ADMIN_USER_NAME | cut -d "=" -f2)
if [ -z "${USER}" ]; then
  echo "Error: The domain username is blank.  The Admin username must be set in the properties file."
  exit 1
fi
# Get Password
PASS=$(awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep ADMIN_USER_PASS | cut -d "=" -f2)
if [ -z "${PASS}" ]; then
  echo "Error: The domain password is blank.  The Admin password must be set in the properties file."
  exit 1
fi

#CONTAINERCONFIG_DIR=/u01/oracle/ContainerData

DB_CONN_STRING=$(awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep RCU_DB_CONN_STRING | cut -d "=" -f2)
export jdbc_url="jdbc:oracle:thin:@"$DB_CONN_STRING

# Get database Schema Password
DB_SCHEMA_PASS=$(awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep RCU_SCHEMA_PASSWORD | cut -d "=" -f2)
if [ -z "$DB_SCHEMA_PASS" ]; then
  echo "The databse schema password is blank.  The database schema password must be set in the properties file."
  exit
fi

echo "Info: Content of ${DOMAIN_PROPERTIES_FILE}:"
sed 's/ADMIN_USER_PASS=.*/ADMIN_USER_PASS=********/g;s/RCU_SCHEMA_PASSWORD=.*/RCU_SCHMEA_PASSWORD=********/g' ${DOMAIN_PROPERTIES_FILE}

parseProperties ${DOMAIN_PROPERTIES_FILE}

if [ -z "${JAVA_HOME}" ]; then
  JAVA_HOME=/usr/java/latest
fi

echo "Domain Configuration Phase"
echo "=========================="
#wlst.sh -skipWLSModuleScanning -loadProperties ${DOMAIN_PROPERTIES_FILE} /u01/oracle/createFMWDomain.py -oh ${ORACLE_HOME} -jh ${JAVA_HOME}
cmd="wlst.sh -skipWLSModuleScanning \
        /u01/oracle/createFMWDomain.py \
        -oh ${ORACLE_HOME} \
        -jh ${JAVA_HOME} \
        -parent "${DOMAIN_HOME}/../" \
        -name ${DOMAIN_NAME} \
        -user ${ADMIN_USER_NAME} \
        -password %ADMIN_USER_PASS% \
        -rcuDb ${RCU_DB_CONN_STRING} \
        -rcuPrefix ${RCU_SCHEMA_PREFIX} \
        -rcuSchemaPwd %RCU_SCHEMA_PASSWORD% \
        -adminListenPort ${ADMIN_PORT} \
        -adminName ${ADMIN_NAME} \
        -managedNameBase ${MANAGED_SERVER_NAME_BASE} \
        -managedServerPort ${MANAGED_SERVER_PORT} \
        -prodMode ${PRODUCTION_MODE} \
        -managedServerCount ${CONFIGURED_MANAGED_SERVER_COUNT} \
        -clusterName ${CLUSTER_NAME} \
        -exposeAdminT3Channel ${EXPOSE_T3_CHANNEL} \
        -t3ChannelPort ${T3_CHANNEL_PORT}
        "
if [ -n "${T3_PUBLIC_ADDRESS}" ]; then
  cmd="$cmd -t3ChannelPublicAddress ${T3_PUBLIC_ADDRESS}"
fi

echo @@ "Info: Calling WLST script with: "
echo ${cmd} | sed 's:%ADMIN_USER_PASS%:********:g;s:%RCU_SCHEMA_PASSWORD%:********:g' 
echo
# put real passwords back to cmd
cmd=$(echo ${cmd} | sed "s:%ADMIN_USER_PASS%:\"${ADMIN_USER_PASS}\":g;s:%RCU_SCHEMA_PASSWORD%:\"${RCU_SCHEMA_PASSWORD}\":g")
eval $cmd || exit 1
