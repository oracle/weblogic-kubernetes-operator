#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Include common utility functions
source ${CREATE_DOMAIN_SCRIPT_DIR}/utility.sh

# Verify the script to create the domain exists
script=${CREATE_DOMAIN_SCRIPT_DIR}/create-domain-script.sh
if [ -f $script ]; then
  echo The domain will be created using the script $script
else
  fail "Could not locate the domain creation script ${script}"
fi

# Validate the domain secrets exist before proceeding.
if [ ! -f /weblogic-operator/secrets/username ]; then
  fail "The domain secret /weblogic-operator/secrets/username was not found"
fi
if [ ! -f /weblogic-operator/secrets/password ]; then
  fail "The domain secret /weblogic-operator/secrets/password was not found"
fi

# Do not proceed if the domain already exists
domainFolder=${DOMAIN_HOME_DIR}
if [ -d ${domainFolder} ]; then
  fail "The create domain job will not overwrite an existing domain. The domain folder ${domainFolder} already exists"
fi

# Create the base folders
createFolder ${DOMAIN_ROOT_DIR}/domain
createFolder ${DOMAIN_LOGS_DIR}
createFolder ${DOMAIN_ROOT_DIR}/applications
createFolder ${DOMAIN_ROOT_DIR}/stores

# Execute the script to create the domain
source $script

