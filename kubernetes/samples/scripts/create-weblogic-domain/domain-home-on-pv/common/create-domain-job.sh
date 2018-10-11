#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# Include common utility functions
source ${CREATE_DOMAIN_SCRIPT_DIR}/utility.sh

# Verify the script to create the domain exists
script=${CREATE_DOMAIN_SCRIPT_DIR}/create-domain-script.sh

checkCreateDomainScript $script
checkDomainSecret
prepareDomainHomeDir

# Execute the script to create the domain
source $script

# Create the node manager
wlst.sh -skipWLSModuleScanning ${CREATE_DOMAIN_SCRIPT_DIR}/setup-nodemanager.py

# DON'T REMOVE THIS
# This script has to contain this log message. 
# It is used to determine if the job is really completed.
echo "Successfully Completed"
