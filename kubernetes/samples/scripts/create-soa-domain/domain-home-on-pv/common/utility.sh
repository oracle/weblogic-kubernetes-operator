#!/bin/bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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

#
# traceDirs before|after
#   Trace contents and owner of DOMAIN_HOME/LOG_HOME/DATA_HOME directories
#
function traceDirs() {
  echo "id = '`id`'"
  local indir
  for indir in DOMAIN_HOME_DIR DOMAIN_ROOT_DIR DOMAIN_LOGS_DIR; do
    [ -z "${!indir}" ] && continue
    echo "Directory trace for $indir=${!indir} ($1)"
    local cnt=0
    local odir=""
    local cdir="${!indir}/*"
    while [ ${cnt} -lt 30 ] && [ ! "$cdir" = "$odir" ]; do
      echo "  ls -ld $cdir:"
      ls -ld $cdir 2>&1 | sed 's/^/    /'
      odir="$cdir"
      cdir="`dirname "$cdir"`"
      cnt=$((cnt + 1))
    done
  done
}

function prepareDomainHomeDir {
  traceDirs before

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

  traceDirs after
}
