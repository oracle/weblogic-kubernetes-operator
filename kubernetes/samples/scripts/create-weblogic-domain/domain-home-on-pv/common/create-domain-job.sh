#!/bin/bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

function exitIfError {
  if [ "$1" != "0" ]; then
    echo "$2"
    exit $1
  fi
}

# Include common utility functions
source ${CREATE_DOMAIN_SCRIPT_DIR}/utility.sh

# Verify the script to create the domain exists
script=${CREATE_DOMAIN_SCRIPT_DIR}/create-domain-script.sh

checkCreateDomainScript $script
checkDomainSecret
prepareDomainHomeDir

# Execute the script to create the domain
source $script
exitIfError $? "ERROR: $script failed."

# DON'T REMOVE THIS
# This script has to contain this log message. 
# It is used to determine if the job is really completed.
echo "Successfully Completed"
