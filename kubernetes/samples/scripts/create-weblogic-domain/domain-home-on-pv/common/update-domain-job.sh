#!/bin/bash
# Copyright (c) 2018, 2019, Oracle and/or its affiliates.  All rights reserved.
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

# Execute the script to create the domain
source $script update
exitIfError $? "ERROR: $script failed when updating domain."

# DON'T REMOVE THIS
# This script has to contain this log message. 
# It is used to determine if the job is really completed.
echo "Successfully Completed"
