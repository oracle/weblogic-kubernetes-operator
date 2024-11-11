#!/bin/bash

# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

debug_setting=""
if [[ "${debug}" == "true" ]] ; then
  debug_setting="--debug"
fi

olcnectl provision --yes $debug_setting --config-file config-file.yaml
