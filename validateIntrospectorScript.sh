#!/bin/bash
#
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
found_error=0

INTROSPECT_DOMAIN_SCRIPT_FILE=$(find "$(pwd -P)" -type f -name "introspectDomain.sh" | tail -n 1)
DOMAIN_INTROSPECTION_COMPLETE_STRING=$(grep -Er "DOMAIN_INTROSPECTION_COMPLETE" * | grep ProcessingConstants.java | grep -v html | sed 's/.*= //g' | sed 's/;$//g')
RESULT=$(grep -E "${DOMAIN_INTROSPECTION_COMPLETE_STRING}" "$INTROSPECT_DOMAIN_SCRIPT_FILE")
if [ -z "${RESULT}" ]; then
  printf "The script file '%s' must contain phrase %s that matches the DOMAIN_INTROSPECTION_COMPLETE constant value in the operator.\n" \
    "$INTROSPECT_DOMAIN_SCRIPT_FILE" "$DOMAIN_INTROSPECTION_COMPLETE_STRING"
  exit 1
fi

found_error=$?

exit $found_error

