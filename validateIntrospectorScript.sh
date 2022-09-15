#!/bin/bash
#
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

declare -a filesToCheck=("src/main/resources/scripts/introspectDomain.sh" 
                "src/main/java/oracle/kubernetes/operator/ProcessingConstants.java"
                "src/main/resources/scripts/introspectDomain.py"
                )
for fileToCheck in "${filesToCheck[@]}"
do
  if [[ ! `grep "Domain introspection complete" "${fileToCheck}"` ]]; then
    printf "Failed to find the expected string 'Domain introspection complete' in '"${fileToCheck}"' file.\n"
    exit 1
  fi
done

exit 0
