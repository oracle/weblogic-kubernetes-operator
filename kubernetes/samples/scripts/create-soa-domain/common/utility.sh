#!/bin/bash
# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Report an error and fail the job
# $1 - text of error
function fail {
  echo ERROR: $1
  exit 1
}

#
# Create a folder
# $1 - path of folder to create
function createFolder {
  mkdir -m 777 -p $1
  if [ ! -d $1 ]; then
    fail "Unable to create folder $1"
  fi
}

function checkCreateDomainScript {
  if [ -f $1 ]; then
    echo The domain will be created using the script $1
  else
    fail "Could not locate the domain creation script ${1}"
  fi
}
 
function checkDomainSecret { 

  # Validate the domain secrets exist before proceeding.
  if [ ! -f /weblogic-operator/secrets/username ]; then
    fail "The domain secret /weblogic-operator/secrets/username was not found"
  fi
  if [ ! -f /weblogic-operator/secrets/password ]; then
    fail "The domain secret /weblogic-operator/secrets/password was not found"
  fi
}

function prepareDomainHomeDir { 
  # Do not proceed if the domain already exists
  local domainFolder=${DOMAIN_HOME_DIR}
  if [ -d ${domainFolder} ]; then
    fail "The create domain job will not overwrite an existing domain. The domain folder ${domainFolder} already exists"
  fi

  # Create the base folders
  createFolder ${DOMAIN_ROOT_DIR}/domains
  createFolder ${DOMAIN_LOGS_DIR}
  createFolder ${DOMAIN_ROOT_DIR}/applications
  createFolder ${DOMAIN_ROOT_DIR}/stores
}

