#!/bin/bash
#
#Copyright (c) 2021, Oracle and/or its affiliates.
#Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

echo "Info: Domain Home is: " $DOMAIN_HOME

if [ -z "${DOMAIN_HOME}" ]; then
   echo "Error: DOMAIN_HOME not set."
   exit 1
fi

if ! touch $DOMAIN_HOME/test-file ; then
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
USER=`awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep ADMIN_USER_NAME | cut -d "=" -f2`
if [ -z "${USER}" ]; then
   echo "Error: The domain username is blank.  The Admin username must be set in the properties file."
   exit 1
fi
# Get Password
PASS=`awk '{print $1}' ${DOMAIN_PROPERTIES_FILE} | grep ADMIN_USER_PASS | cut -d "=" -f2`
if [ -z "${PASS}" ]; then
   echo "Error: The domain password is blank.  The Admin password must be set in the properties file."
   exit 1
fi

echo "Info: Content of ${DOMAIN_PROPERTIES_FILE}:"
sed 's/ADMIN_USER_PASS=.*/ADMIN_USER_PASS=********/g' ${DOMAIN_PROPERTIES_FILE}

# Create domain
wlst.sh -skipWLSModuleScanning -loadProperties ${DOMAIN_PROPERTIES_FILE} /u01/oracle/create-wls-domain.py || exit 1
